package com.associago.suite;

import com.associago.membership.repository.UserAssociationRepository;
import com.associago.security.ClerkIdentityService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

@Component
public class SuiteAccess {
    private final SuiteSettings settings;
    private final Environment environment;
    private final ClerkIdentityService identities;
    private final UserAssociationRepository memberships;

    public SuiteAccess(SuiteSettings settings, Environment environment, ClerkIdentityService identities,
                       UserAssociationRepository memberships) {
        this.settings = settings;
        this.environment = environment;
        this.identities = identities;
        this.memberships = memberships;
    }

    public void requirePeer(HttpServletRequest request) {
        settings.requireEnabled();
        String key = request.getHeader("X-Suite-Key");
        if (java.util.Collections.list(request.getHeaders("X-Suite-Key")).size() != 1
                || java.util.Collections.list(request.getHeaders("X-Suite-Peer")).size() != 1
                || java.util.Collections.list(request.getHeaders("X-Suite-Organization")).size() != 1
                || !"librepm".equals(request.getHeader("X-Suite-Peer")) || key == null
                || !MessageDigest.isEqual(settings.key().getBytes(StandardCharsets.UTF_8), key.getBytes(StandardCharsets.UTF_8))) {
            throw new SuiteException(401, "PEER_UNAUTHORIZED", "Credenziali del servizio non valide.");
        }
        if (!settings.organizationId().equals(request.getHeader("X-Suite-Organization"))) {
            throw new SuiteException(403, "ORGANIZATION_MISMATCH", "Organizzazione del collegamento non consentita.");
        }
    }

    public void requireUser(Authentication authentication) {
        settings.requireEnabled();
        requireAssociation(authentication, settings.associationId());
    }

    public void requireAssociation(Authentication authentication, Long associationId) {
        if (associationId == null || associationId <= 0) {
            throw new SuiteException(400, "ASSOCIATION_REQUIRED", "Associazione richiesta.");
        }
        if (!environment.acceptsProfiles(Profiles.of("cloud"))) {
            return;
        }
        if (!(authentication instanceof JwtAuthenticationToken) || !authentication.isAuthenticated()) {
            throw new SuiteException(401, "USER_UNAUTHORIZED", "Accesso autenticato richiesto.");
        }
        Set<String> roles = Set.of("ROLE_PRESIDENTE", "ROLE_RESPONSABILE_AMMINISTRATIVO", "ROLE_COORDINATORE_SERVIZI");
        if (authentication.getAuthorities().stream().noneMatch(authority -> roles.contains(authority.getAuthority()))) {
            throw new SuiteException(403, "PROJECT_FORBIDDEN", "Il ruolo non consente di gestire i progetti condivisi.");
        }
        var user = identities.resolve(authentication)
                .orElseThrow(() -> new SuiteException(403, "IDENTITY_NOT_LINKED", "Collegare l'accesso all'anagrafica dell'associazione."));
        if (memberships.findByUserIdAndAssociationId(user.getId(), associationId)
                .filter(membership -> "ACTIVE".equals(membership.getStatus())).isEmpty()) {
            throw new SuiteException(403, "ASSOCIATION_FORBIDDEN", "L'utente non appartiene all'associazione del collegamento.");
        }
    }

    public boolean mayReadFinancial(Authentication authentication) {
        return !environment.acceptsProfiles(Profiles.of("cloud")) || authentication != null
                && authentication.getAuthorities().stream().anyMatch(authority -> Set.of(
                        "ROLE_PRESIDENTE", "ROLE_RESPONSABILE_AMMINISTRATIVO").contains(authority.getAuthority()));
    }

    public String actorSubject(Authentication authentication) {
        return authentication instanceof JwtAuthenticationToken jwt ? jwt.getToken().getSubject() : null;
    }
}
