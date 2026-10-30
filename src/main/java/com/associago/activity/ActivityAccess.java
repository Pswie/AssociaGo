package com.associago.activity;

import com.associago.finance.FinancialAccess;
import com.associago.activity.dto.ActivityParticipantCandidateDTO;
import com.associago.membership.repository.UserAssociationRepository;
import com.associago.security.ClerkIdentityService;
import com.associago.suite.SuiteException;
import com.associago.user.User;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.util.Objects;
import java.util.Set;

/** Server-side role and association checks apply to every activity service entry. */
@Component
public class ActivityAccess {
    private static final Set<String> ROLES = Set.of("ROLE_PRESIDENTE", "ROLE_RESPONSABILE_AMMINISTRATIVO", "ROLE_COORDINATORE_SERVIZI",
            "ROLE_PSICOLOGO", "ROLE_OPERATORE_ALIMENTARE", "ROLE_ORIENTATORE_SERVIZI", "ROLE_FACILITATORE_DIGITALE");
    private final Environment environment;
    private final ClerkIdentityService identities;
    private final UserAssociationRepository memberships;
    private final FinancialAccess financial;

    public ActivityAccess(Environment environment, ClerkIdentityService identities,
                          UserAssociationRepository memberships, FinancialAccess financial) {
        this.environment = environment; this.identities = identities;
        this.memberships = memberships; this.financial = financial;
    }
    public boolean cloud() { return environment.matchesProfiles("cloud"); }
    public boolean mayReadFinancial() {
        Authentication actor = SecurityContextHolder.getContext().getAuthentication();
        return !cloud() || actor != null && actor.getAuthorities().stream().anyMatch(a ->
                Set.of("ROLE_PRESIDENTE", "ROLE_RESPONSABILE_AMMINISTRATIVO").contains(a.getAuthority()));
    }
    public Long association(Long requested) {
        Long selected = selected();
        if (requested != null && selected != null && !requested.equals(selected)) forbidden();
        Long actual = requested != null ? requested : selected;
        if (actual == null && !cloud()) return null;
        require(actual);
        return actual;
    }
    public void require(Long association) {
        if (!cloud()) return;
        if (association == null || association <= 0) throw new SuiteException(400, "ASSOCIATION_REQUIRED", "Selezionare l'associazione.");
        Long selected = selected();
        if (selected != null && !Objects.equals(selected, association)) forbidden();
        Authentication actor = SecurityContextHolder.getContext().getAuthentication();
        if (!(actor instanceof JwtAuthenticationToken) || !actor.isAuthenticated()) {
            throw new SuiteException(401, "USER_UNAUTHORIZED", "Accesso autenticato richiesto.");
        }
        if (actor.getAuthorities().stream().noneMatch(a -> ROLES.contains(a.getAuthority()))) {
            throw new SuiteException(403, "ACTIVITY_FORBIDDEN", "Il ruolo non consente di gestire le attività.");
        }
        var user = identities.resolve(actor).orElseThrow(() -> new SuiteException(403, "IDENTITY_NOT_LINKED", "Accesso non collegato all'anagrafica."));
        if (memberships.findByUserIdAndAssociationId(user.getId(), association)
                .filter(m -> "ACTIVE".equals(m.getStatus())).isEmpty()) forbidden();
    }
    public void requireFinancial(Long association) {
        if (!cloud()) return;
        financial.requireRecord(SecurityContextHolder.getContext().getAuthentication(), association, selected());
    }
    public void requireWrite(Long association) {
        require(association);
        if (cloud() && SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .noneMatch(a -> !"ROLE_RESPONSABILE_AMMINISTRATIVO".equals(a.getAuthority()) && ROLES.contains(a.getAuthority()))) {
            throw new SuiteException(403, "ACTIVITY_FORBIDDEN", "Il ruolo non consente di modificare le attività.");
        }
    }
    public java.util.List<ActivityParticipantCandidateDTO> participantCandidates(Long association) {
        require(association);
        java.util.Map<Long, ActivityParticipantCandidateDTO> unique = new java.util.LinkedHashMap<>();
        memberships.findByAssociationId(association).stream().filter(m -> m.getUser() != null)
                .sorted(java.util.Comparator.comparing(m -> m.getId())).forEach(m -> unique.putIfAbsent(m.getUser().getId(),
                        new ActivityParticipantCandidateDTO(m.getUser().getId(), m.getId(),
                                m.getUser().getFirstName(), m.getUser().getLastName(), m.getMembershipCardNumber(), m.getStatus())));
        return java.util.List.copyOf(unique.values());
    }
    public User participantUser(Long userId, Long association) {
        if (userId == null || userId <= 0) throw new SuiteException(400, "PARTICIPANT_REQUIRED", "Indicare il partecipante.");
        return memberships.findByUserId(userId).stream().filter(m -> m.getAssociation() != null
                        && Objects.equals(m.getAssociation().getId(), association) && m.getUser() != null)
                .map(m -> m.getUser()).findFirst().orElseThrow(() -> new SuiteException(403, "ASSOCIATION_FORBIDDEN", "Il partecipante non appartiene all'associazione dell'attività."));
    }
    public void instructorMembership(Long id, Long association) {
        if (id != null && memberships.findById(id).filter(m -> m.getAssociation() != null
                && Objects.equals(m.getAssociation().getId(), association)).isEmpty()) forbidden();
    }
    public void creating(Long id) {
        if (cloud() && id != null) throw new SuiteException(400, "ACTIVITY_ID_FORBIDDEN", "L'identificativo di una nuova risorsa viene assegnato dal servizio.");
    }
    public void immutable(Long requested, Long actual) {
        if (cloud() && requested != null && !Objects.equals(requested, actual)) {
            throw new SuiteException(409, "ACTIVITY_PARENT_IMMUTABLE", "L'associazione e l'attività originali non possono essere riassegnate.");
        }
    }
    private Long selected() {
        if (!cloud() || !(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) return null;
        var request = attrs.getRequest();
        var headers = java.util.Collections.list(request.getHeaders("X-Association-Id"));
        if (headers.isEmpty()) return null;
        if (headers.size() != 1 || !headers.getFirst().matches("[1-9][0-9]*")) {
            throw new SuiteException(400, "ASSOCIATION_INVALID", "Associazione selezionata non valida.");
        }
        try { return Long.valueOf(headers.getFirst()); }
        catch (NumberFormatException error) { throw new SuiteException(400, "ASSOCIATION_INVALID", "Associazione selezionata non valida."); }
    }
    private static void forbidden() { throw new SuiteException(403, "ASSOCIATION_FORBIDDEN", "La risorsa non appartiene all'associazione autorizzata."); }
}
