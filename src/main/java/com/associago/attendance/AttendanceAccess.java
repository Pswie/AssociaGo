package com.associago.attendance;

import com.associago.membership.repository.UserAssociationRepository;
import com.associago.security.ClerkIdentityService;
import com.associago.suite.SuiteException;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;

/** Attendance roles still require a verified membership in the activity's association. */
@Component
public class AttendanceAccess {
    private static final Set<String> ROLES = Set.of("ROLE_PRESIDENTE", "ROLE_COORDINATORE_SERVIZI",
            "ROLE_REFERENTE_VOLONTARI", "ROLE_PSICOLOGO", "ROLE_OPERATORE_ALIMENTARE",
            "ROLE_ORIENTATORE_SERVIZI", "ROLE_FACILITATORE_DIGITALE");
    private final Environment environment;
    private final ClerkIdentityService identities;
    private final UserAssociationRepository memberships;

    public AttendanceAccess(Environment environment, ClerkIdentityService identities, UserAssociationRepository memberships) {
        this.environment = environment;
        this.identities = identities;
        this.memberships = memberships;
    }

    public boolean cloud() { return environment.matchesProfiles("cloud"); }

    public void require(Authentication actor, Long actualAssociation, Long selectedAssociation) {
        if (actualAssociation == null || actualAssociation <= 0) {
            throw new SuiteException(400, "ASSOCIATION_REQUIRED", "L'attività deve appartenere a un'associazione.");
        }
        if (selectedAssociation != null && !Objects.equals(actualAssociation, selectedAssociation)) {
            throw new SuiteException(403, "ASSOCIATION_FORBIDDEN", "L'attività non appartiene all'associazione selezionata.");
        }
        if (!cloud()) return;
        if (!(actor instanceof JwtAuthenticationToken) || !actor.isAuthenticated()) {
            throw new SuiteException(401, "USER_UNAUTHORIZED", "Accesso autenticato richiesto.");
        }
        if (actor.getAuthorities().stream().noneMatch(authority -> ROLES.contains(authority.getAuthority()))) {
            throw new SuiteException(403, "ATTENDANCE_FORBIDDEN", "Il ruolo non consente di gestire le presenze.");
        }
        var user = identities.resolve(actor).orElseThrow(() ->
                new SuiteException(403, "IDENTITY_NOT_LINKED", "L'accesso non è collegato all'anagrafica dell'associazione."));
        if (memberships.findByUserIdAndAssociationId(user.getId(), actualAssociation)
                .filter(membership -> "ACTIVE".equals(membership.getStatus())).isEmpty()) {
            throw new SuiteException(403, "ASSOCIATION_FORBIDDEN", "L'utente non appartiene all'associazione dell'attività.");
        }
    }
}
