package com.associago.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Traduce i claim di un JWT Clerk in authority di Spring Security.
 *
 * <p>Clerk non ha un claim di ruolo standardizzato: dipende da come è scritto
 * il session token template. Per questo si guarda un elenco configurabile di
 * claim, accettando sia una stringa singola sia una lista, e si scende anche
 * dentro {@code public_metadata}, dove i ruoli finiscono più spesso.
 *
 * <p>Un ruolo presente nel token ma non incluso in
 * {@link ClerkProperties#getKnownRoles()} viene <strong>ignorato</strong>:
 * i permessi li decide il backend, non chi confeziona il token.
 *
 * @since 0.2.0
 */
public class ClerkRoleConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String METADATA_CLAIM = "public_metadata";
    private static final String ORGANIZATION_CLAIM = "o";
    private static final String ORGANIZATION_ROLE_CLAIM = "rol";

    private final ClerkProperties properties;

    public ClerkRoleConverter(ClerkProperties properties) {
        this.properties = properties;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Set<String> roles = new LinkedHashSet<>();

        // Nei session token Clerk v2 il ruolo dell'organizzazione attiva non
        // arriva più come org_role, ma nella forma compatta `o.rol`.
        // `admin` e `member` sono i due ruoli predefiniti di Clerk: nel
        // gestionale corrispondono rispettivamente all'accesso completo e
        // all'area personale. Quando esiste, è la fonte autorevole: un vecchio
        // valore nei metadata non deve sopravvivere alla revoca di un ruolo.
        Object organization = jwt.getClaims().get(ORGANIZATION_CLAIM);
        boolean organizationRolePresent = organization instanceof Map<?, ?> map
                && map.containsKey(ORGANIZATION_ROLE_CLAIM);
        if (organization instanceof Map<?, ?> map) {
            roles.addAll(readRoles(map.get(ORGANIZATION_ROLE_CLAIM)));
        }

        // Compatibilità con installazioni senza Clerk Organizations. I claim
        // legacy si consultano soltanto se il token non contiene o.rol.
        if (!organizationRolePresent) {
            for (String claim : properties.getRoleClaims()) {
                roles.addAll(readRoles(jwt.getClaims().get(claim)));

                Object metadata = jwt.getClaims().get(METADATA_CLAIM);
                if (metadata instanceof Map<?, ?> map) {
                    roles.addAll(readRoles(map.get(claim)));
                }
            }
        }

        List<GrantedAuthority> authorities = new ArrayList<>();
        for (String role : roles) {
            String normalized = toApplicationRole(normalize(role));
            if (properties.getKnownRoles().contains(normalized)) {
                authorities.add(new SimpleGrantedAuthority(toAuthority(normalized)));
            }
        }

        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }

    /** Accetta indifferentemente una stringa singola o una lista di stringhe. */
    private Collection<String> readRoles(Object claimValue) {
        if (claimValue instanceof String single && !single.isBlank()) {
            return List.of(single);
        }
        if (claimValue instanceof Collection<?> many) {
            List<String> result = new ArrayList<>();
            for (Object item : many) {
                if (item instanceof String value && !value.isBlank()) {
                    result.add(value);
                }
            }
            return result;
        }
        return List.of();
    }

    /** {@code Coordinatore_Servizi} e {@code coordinatore servizi} sono lo stesso ruolo. */
    private String normalize(String role) {
        return role.trim().toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-');
    }

    private String toApplicationRole(String normalizedRole) {
        return switch (normalizedRole) {
            case "admin", "org:admin" -> "presidente";
            case "member", "org:member" -> "socio";
            default -> normalizedRole;
        };
    }

    /** {@code coordinatore-servizi} diventa {@code ROLE_COORDINATORE_SERVIZI}. */
    private String toAuthority(String normalizedRole) {
        return "ROLE_" + normalizedRole.toUpperCase(Locale.ROOT).replace('-', '_');
    }
}
