package com.associago.membership;

import com.associago.membership.repository.UserAssociationRepository;
import com.associago.security.ClerkIdentityService;
import com.associago.suite.SuiteException;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import java.util.Objects;
import java.util.Set;

/** An API caller cannot create the membership which would authorize that same call. */
@Component
public class MembershipAccess {
    private final Environment environment;
    private final ClerkIdentityService identities;
    private final UserAssociationRepository memberships;
    public MembershipAccess(Environment environment, ClerkIdentityService identities, UserAssociationRepository memberships) {
        this.environment = environment; this.identities = identities; this.memberships = memberships;
    }
    public boolean cloud() { return environment.matchesProfiles("cloud"); }
    public void require(Authentication actor, Long association, Long selected) {
        if (!cloud()) return;
        if (association == null || association <= 0) throw new SuiteException(400, "ASSOCIATION_REQUIRED", "Indicare l'associazione.");
        if (selected != null && !Objects.equals(selected, association)) denied();
        if (!(actor instanceof JwtAuthenticationToken) || !actor.isAuthenticated()) {
            throw new SuiteException(401, "USER_UNAUTHORIZED", "Accesso autenticato richiesto.");
        }
        if (actor.getAuthorities().stream().noneMatch(a -> Set.of("ROLE_PRESIDENTE", "ROLE_RESPONSABILE_AMMINISTRATIVO", "ROLE_REFERENTE_VOLONTARI").contains(a.getAuthority()))) {
            throw new SuiteException(403, "MEMBERSHIP_FORBIDDEN", "Il ruolo non consente di gestire le iscrizioni.");
        }
        var user = identities.resolve(actor).orElseThrow(() -> new SuiteException(403, "IDENTITY_NOT_LINKED", "Accesso non collegato all'anagrafica."));
        if (memberships.findByUserIdAndAssociationId(user.getId(), association)
                .filter(m -> "ACTIVE".equals(m.getStatus())).isEmpty()) denied();
    }
    public void createId(Long id) {
        if (cloud() && id != null) throw new SuiteException(400, "MEMBERSHIP_ID_FORBIDDEN", "L'identificativo della nuova iscrizione viene assegnato dal servizio.");
    }
    public void requireNew(Long userId, Long association) {
        if (cloud() && memberships.findByUserIdAndAssociationId(userId, association).isPresent()) {
            throw new SuiteException(409, "MEMBERSHIP_EXISTS", "La persona ha già un'iscrizione in questa associazione. Modificare quella esistente.");
        }
    }
    public void immutable(Long requested, Long actual) {
        if (cloud() && requested != null && !Objects.equals(requested, actual)) {
            throw new SuiteException(409, "MEMBERSHIP_PARENT_IMMUTABLE", "La persona e l'associazione originali non possono essere riassegnate.");
        }
    }
    private static void denied() { throw new SuiteException(403, "ASSOCIATION_FORBIDDEN", "L'iscrizione non appartiene all'associazione autorizzata."); }
}
