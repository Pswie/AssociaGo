package com.associago.access;

import com.associago.suite.SuiteException;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Application approval and legal membership are separate records. */
public final class SuiteAssociationScope {
    private SuiteAssociationScope() { }
    public static boolean enabled(Environment env) { return env.getProperty("SUITE_ACCESS_ENABLED", Boolean.class, false); }
    public static void require(Environment env, Authentication actor, Long association, String permission) {
        if (!(actor instanceof JwtAuthenticationToken) || !actor.isAuthenticated())
            throw new SuiteException(401, "USER_UNAUTHORIZED", "Accesso autenticato richiesto.");
        if (!SuiteAccessService.permits(actor, permission))
            throw new SuiteException(403, "MODULE_FORBIDDEN", "Accesso non autorizzato a questa sezione.");
        Long configured = env.getProperty("SUITE_ASSOCIATION_ID", Long.class, 0L);
        if (configured <= 0) throw new SuiteException(503, "ASSOCIATION_CONFIGURATION", "Associazione non configurata.");
        if (!configured.equals(association)) throw new SuiteException(403, "ASSOCIATION_FORBIDDEN", "Associazione non autorizzata.");
    }
}
