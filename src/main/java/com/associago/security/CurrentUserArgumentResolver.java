package com.associago.security;

import org.springframework.core.MethodParameter;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Argument Resolver per @CurrentUser annotation
 *
 * Risolve l'ID utente corrente in ordine di priorità:
 * 1. Header HTTP "X-User-Id" (per client desktop/mobile)
 * 2. OAuth2 Principal (per autenticazione web)
 *
 * Questo permette ai client Electron/React di passare l'utente
 * senza richiedere una sessione OAuth2 completa.
 *
 * @author Lorenzo DM
 * @since 0.2.0
 * @updated 0.3.1 - Aggiunto supporto header X-User-Id
 */
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {
    private final ClerkIdentityService identities;
    public CurrentUserArgumentResolver() { this.identities = null; }
    public CurrentUserArgumentResolver(ClerkIdentityService identities) { this.identities = identities; }

    /**
     * Header HTTP per identificare l'utente corrente
     */
    public static final String USER_ID_HEADER = "X-User-Id";

    @Override
    public boolean supportsParameter(@NonNull MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && Long.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            @NonNull MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            @NonNull NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) {
        // A verified web identity always wins over an arbitrary desktop header.
        var actor = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (actor instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken) {
            if (identities == null) throw new IllegalStateException("Resolver identita web non configurato.");
            return identities.resolve(actor).map(com.associago.user.User::getId)
                    .orElseThrow(() -> new com.associago.suite.SuiteException(403, "IDENTITY_NOT_LINKED", "Accesso non collegato all'anagrafica."));
        }
        // 1. Prima prova a leggere l'header X-User-Id (per client desktop/mobile)
        String headerUserId = webRequest.getHeader(USER_ID_HEADER);
        if (headerUserId != null && !headerUserId.isBlank()) {
            try {
                return Long.parseLong(headerUserId.trim());
            } catch (NumberFormatException e) {
                // Ignora header malformato e prova fallback
            }
        }

        // 2. Fallback: prova OAuth2 Principal (per autenticazione web)
        try {
            return CurrentUserUtils.requireUserId();
        } catch (IllegalStateException e) {
            // Nessun utente autenticato né via header né via OAuth2
            throw new IllegalStateException(
                    "Utente non autenticato (AssociaGo). " +
                            "Invia header '" + USER_ID_HEADER + "' o autentica via OAuth2."
            );
        }
    }
}
