package com.associago.me;

import com.associago.member.Member;
import com.associago.member.repository.MemberRepository;
import com.associago.membership.UserAssociation;
import com.associago.membership.repository.UserAssociationRepository;
import com.associago.security.ClerkIdentityService;
import com.associago.user.User;
import com.associago.volunteer.Volunteer;
import com.associago.volunteer.VolunteerExpense;
import com.associago.volunteer.VolunteerShift;
import com.associago.volunteer.repository.VolunteerExpenseRepository;
import com.associago.volunteer.repository.VolunteerRepository;
import com.associago.volunteer.repository.VolunteerShiftRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Area personale: quello che un socio o un volontario può vedere di sé.
 *
 * <p>Il principio che regge tutta la classe: <strong>nessun endpoint accetta un
 * identificativo di persona dall'esterno</strong>. Il soggetto è sempre e solo
 * chi sta chiamando, ricavato dal token. È l'unico modo per essere certi che un
 * socio non possa leggere la posizione di un altro cambiando un numero
 * nell'URL — e su un'associazione che gestisce sportello psicologico e pacchi
 * alimentari non è un dettaglio.
 *
 * <p>Questi percorsi stanno sotto {@code /api/v1/me/**} apposta: la
 * configurazione di sicurezza li apre a tutti i ruoli autenticati, mentre il
 * resto delle API resta riservato ai ruoli operativi.
 *
 * @since 0.2.0
 */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final ClerkIdentityService identityService;
    private final UserAssociationRepository userAssociationRepository;
    private final MemberRepository memberRepository;
    private final VolunteerRepository volunteerRepository;
    private final VolunteerShiftRepository shiftRepository;
    private final VolunteerExpenseRepository expenseRepository;

    public MeController(
            ClerkIdentityService identityService,
            UserAssociationRepository userAssociationRepository,
            MemberRepository memberRepository,
            VolunteerRepository volunteerRepository,
            VolunteerShiftRepository shiftRepository,
            VolunteerExpenseRepository expenseRepository) {
        this.identityService = identityService;
        this.userAssociationRepository = userAssociationRepository;
        this.memberRepository = memberRepository;
        this.volunteerRepository = volunteerRepository;
        this.shiftRepository = shiftRepository;
        this.expenseRepository = expenseRepository;
    }

    /** Profilo e posizione associativa di chi sta chiamando. */
    @GetMapping
    public ResponseEntity<Map<String, Object>> profile(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("message", "Authentication is required."));
        }
        Optional<User> maybeUser = identityService.resolve(authentication);
        if (maybeUser.isEmpty()) {
            return notLinked();
        }
        User user = maybeUser.get();

        List<UserAssociation> positions = userAssociationRepository.findByUserId(user.getId());
        List<Map<String, Object>> membership = positions.stream()
                .map(position -> Map.<String, Object>of(
                        "ruolo", position.getRole(),
                        "stato", position.getStatus(),
                        "tessera", nullSafe(position.getMembershipCardNumber()),
                        "iscrittoDal", nullSafe(position.getJoinDate()),
                        "scadenza", nullSafe(position.getExpirationDate()),
                        "consigliere", position.isBoardMember()))
                .toList();

        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "nome", nullSafe(user.getFirstName()),
                "cognome", nullSafe(user.getLastName()),
                "email", nullSafe(user.getEmail()),
                "telefono", nullSafe(user.getPhone()),
                "posizioni", membership));
    }

    /** Ore di volontariato registrate a nome di chi sta chiamando. */
    @GetMapping("/presenze")
    public ResponseEntity<List<Map<String, Object>>> shifts(Authentication authentication) {
        Optional<Volunteer> volunteer = resolveVolunteer(authentication);
        if (volunteer.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        List<Map<String, Object>> result = shiftRepository.findByVolunteerId(volunteer.get().getId())
                .stream()
                .map(this::toShiftView)
                .toList();
        return ResponseEntity.ok(result);
    }

    /** Rimborsi spese richiesti da chi sta chiamando, con il loro stato. */
    @GetMapping("/rimborsi")
    public ResponseEntity<List<Map<String, Object>>> expenses(Authentication authentication) {
        Optional<Volunteer> volunteer = resolveVolunteer(authentication);
        if (volunteer.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        List<Map<String, Object>> result = expenseRepository.findByVolunteerId(volunteer.get().getId())
                .stream()
                .map(this::toExpenseView)
                .toList();
        return ResponseEntity.ok(result);
    }

    /**
     * Da identità Clerk a scheda volontario.
     *
     * <p>Il percorso è tortuoso perché nello schema esistono due anagrafiche
     * distinte: {@code users}, a cui è agganciato l'accesso, e {@code members},
     * a cui è agganciato il volontariato. Si passa dal codice fiscale, che è
     * univoco, e solo in mancanza dall'email.
     */
    private Optional<Volunteer> resolveVolunteer(Authentication authentication) {
        return identityService.resolve(authentication)
                .flatMap(this::findMember)
                .flatMap(member -> volunteerRepository.findByMemberId(member.getId()));
    }

    private Optional<Member> findMember(User user) {
        if (user.getTaxCode() != null && !user.getTaxCode().isBlank()) {
            Optional<Member> byFiscalCode = memberRepository.findByFiscalCode(user.getTaxCode());
            if (byFiscalCode.isPresent()) {
                return byFiscalCode;
            }
        }
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            return memberRepository.findByEmail(user.getEmail());
        }
        return Optional.empty();
    }

    private Map<String, Object> toShiftView(VolunteerShift shift) {
        return Map.of(
                "inizio", nullSafe(shift.getStartTime()),
                "fine", nullSafe(shift.getEndTime()),
                "ore", nullSafe(shift.getHoursWorked()),
                "ruolo", nullSafe(shift.getRole()),
                "stato", nullSafe(shift.getStatus()));
    }

    private Map<String, Object> toExpenseView(VolunteerExpense expense) {
        return Map.of(
                "data", nullSafe(expense.getExpenseDate()),
                "importo", nullSafe(expense.getAmount()),
                "descrizione", nullSafe(expense.getDescription()),
                "stato", nullSafe(expense.getStatus()));
    }

    private ResponseEntity<Map<String, Object>> notLinked() {
        // 404 e non 403: l'accesso è legittimo, semplicemente non risulta
        // ancora collegato a nessuna persona in anagrafica.
        return ResponseEntity.status(404).body(Map.of(
                "message", "Nessuna anagrafica collegata a questo accesso. "
                        + "Chiedi al referente di registrare la tua email."));
    }

    /** Map.of() non accetta valori nulli: qui diventano stringa vuota. */
    private Object nullSafe(Object value) {
        return value == null ? "" : value;
    }
}
