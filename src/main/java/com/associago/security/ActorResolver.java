package com.associago.security;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/** Resolves audit actors from the authenticated cloud identity, retaining explicit desktop actors. */
@Component
public class ActorResolver {
    private final ClerkIdentityService identityService;
    private final Environment environment;

    public ActorResolver(ClerkIdentityService identityService, Environment environment) {
        this.identityService = identityService;
        this.environment = environment;
    }

    public Long resolve(Authentication authentication, Long suppliedActor) {
        if (authentication instanceof JwtAuthenticationToken) {
            if (!authentication.isAuthenticated()) {
                throw new AccessDeniedException("An authenticated account is required.");
            }
            Long actor = identityService.resolve(authentication)
                    .map(com.associago.user.User::getId)
                    .filter(id -> id > 0)
                    .orElseThrow(() -> new AccessDeniedException("Link this account to an internal user before continuing."));
            if (suppliedActor != null && !actor.equals(suppliedActor)) {
                throw new AccessDeniedException("The action cannot be attributed to another user.");
            }
            return actor;
        }
        if (environment.acceptsProfiles(Profiles.of("cloud"))) {
            throw new AccessDeniedException("An authenticated cloud account is required.");
        }
        if (suppliedActor != null && suppliedActor <= 0) {
            throw new IllegalArgumentException("A valid user is required to attribute this action.");
        }
        return suppliedActor;
    }

    public long requireActor(Authentication authentication, Long suppliedActor) {
        Long actor = resolve(authentication, suppliedActor);
        if (actor == null) {
            throw new IllegalArgumentException("A user is required to approve this document.");
        }
        return actor;
    }
}
