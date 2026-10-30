package com.associago.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Sicurezza del profilo {@code cloud}: il backend è esposto in rete.
 *
 * <p>Differenze sostanziali rispetto a {@link SecurityConfig}, che vale solo
 * per desktop e sviluppo:
 *
 * <ul>
 *   <li>l'identità arriva da Clerk come JWT Bearer, verificato via JWKS;
 *   <li>nessuna sessione, quindi nessun CSRF da difendere;
 *   <li>{@code /api/auth/**} è chiuso: la master password per associazione e il
 *       reset via codice fiscale — dato pubblico sul RUNTS — resterebbero due
 *       vie d'ingresso parallele a Clerk;
 *   <li>{@code /api/setup/**} e {@code /api/bootstrap/**} sono chiusi: servono
 *       all'onboarding di un'installazione desktop, non a un servizio online;
 *   <li>CORS su origini esplicite, mai {@code *}.
 * </ul>
 *
 * <p>La configurazione rifiuta di avviarsi se manca l'issuer o se le origini
 * sono lasciate aperte: su un URL pubblico un default permissivo non è un
 * inconveniente, è una fuga di dati.
 *
 * @since 0.2.0
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Profile("cloud")
public class CloudSecurityConfig {

    private static final String[] ADMINISTRATIVE_ROLES = {
        "PRESIDENTE",
        "RESPONSABILE_AMMINISTRATIVO"
    };

    private static final String[] MEMBER_MANAGEMENT_ROLES = {
        "PRESIDENTE",
        "RESPONSABILE_AMMINISTRATIVO",
        "REFERENTE_VOLONTARI"
    };

    private static final String[] ACTIVITY_ROLES = {
        "PRESIDENTE",
        "COORDINATORE_SERVIZI",
        "PSICOLOGO",
        "OPERATORE_ALIMENTARE",
        "ORIENTATORE_SERVIZI",
        "FACILITATORE_DIGITALE"
    };

    private static final String[] ATTENDANCE_ROLES = {
        "PRESIDENTE",
        "COORDINATORE_SERVIZI",
        "REFERENTE_VOLONTARI",
        "PSICOLOGO",
        "OPERATORE_ALIMENTARE",
        "ORIENTATORE_SERVIZI",
        "FACILITATORE_DIGITALE"
    };

    private static final String[] VOLUNTEER_MANAGEMENT_ROLES = {
        "PRESIDENTE",
        "COORDINATORE_SERVIZI",
        "REFERENTE_VOLONTARI"
    };

    private static final String[] PROJECT_ROLES = {
        "PRESIDENTE",
        "COORDINATORE_SERVIZI",
        "RESPONSABILE_AMMINISTRATIVO"
    };

    private static final String[] CERTIFICATE_ROLES = {
        "PRESIDENTE",
        "RESPONSABILE_AMMINISTRATIVO",
        "REFERENTE_VOLONTARI"
    };

    private static final String[] EVENT_ROLES = {
        "PRESIDENTE",
        "COORDINATORE_SERVIZI"
    };

    private final ClerkProperties clerkProperties;
    private final List<String> allowedOrigins;

    public CloudSecurityConfig(
            ClerkProperties clerkProperties,
            @Value("${associago.cors.allowed-origins:}") List<String> allowedOrigins) {
        this.clerkProperties = clerkProperties;
        this.allowedOrigins = allowedOrigins.stream().filter(o -> !o.isBlank()).toList();

        if (clerkProperties.getIssuerUri() == null || clerkProperties.getIssuerUri().isBlank()) {
            throw new IllegalStateException(
                    "associago.clerk.issuer-uri non configurato: senza issuer i token non sono "
                            + "verificabili e il profilo cloud non può partire.");
        }
        if (allowedOrigins.isEmpty() || allowedOrigins.contains("*")) {
            throw new IllegalStateException(
                    "associago.cors.allowed-origins deve elencare le origini esatte del "
                            + "gestionale: '*' su un backend pubblico espone l'API a qualsiasi sito.");
        }
    }

    @Bean
    SecurityFilterChain cloudSecurityFilterChain(HttpSecurity http) throws Exception {
        http.cors(cors -> cors.configurationSource(cloudCorsConfigurationSource()));
        http.csrf(AbstractHttpConfigurer::disable);
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/error", "/actuator/health", "/actuator/info").permitAll()
                // These controller methods enforce constant-time peer authentication and a fixed organization.
                .requestMatchers("/api/integrations/v1/peer/**").permitAll()
                .requestMatchers("/api/integrations/v1/**").hasAnyRole(PROJECT_ROLES)
                .requestMatchers("/api/auth/**", "/api/v1/auth/**").denyAll()
                .requestMatchers("/api/setup/**", "/api/v1/setup/**",
                        "/api/bootstrap/**", "/api/v1/bootstrap/**",
                        "/api/associations/setup", "/api/v1/associations/setup").denyAll()

                // Area personale: aperta a qualunque ruolo autenticato, perché
                // non espone dati di terzi. Il soggetto lo decide il token, mai
                // un parametro della richiesta (vedi MeController).
                .requestMatchers("/api/v1/me", "/api/v1/me/**").authenticated()

                // Elenco associazioni: serve al frontend subito dopo l'accesso
                // per sapere di quale ente si tratta, prima di ogni altra cosa.
                .requestMatchers(HttpMethod.GET, "/api/v1/associations").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/associations").authenticated()
                .requestMatchers("/api/v1/app/**").authenticated()

                /*
                 * La matrice seguente è la barriera vera. Il menu React nasconde
                 * le sezioni non pertinenti, ma un utente può sempre costruire
                 * a mano una richiesta HTTP: per questo ogni famiglia di API
                 * ripete qui i ruoli funzionali che la possono usare.
                 */
                .requestMatchers("/api/dashboard/**", "/api/v1/dashboard/**")
                    .hasAnyRole(ADMINISTRATIVE_ROLES)
                .requestMatchers("/api/stats/financial", "/api/v1/stats/financial",
                        "/api/stats/general", "/api/v1/stats/general",
                        "/api/stats/assemblies/**", "/api/v1/stats/assemblies/**")
                    .hasAnyRole(ADMINISTRATIVE_ROLES)
                .requestMatchers("/api/stats/activities/**", "/api/v1/stats/activities/**")
                    .hasAnyRole(ACTIVITY_ROLES)

                .requestMatchers("/api/v1/finance/**", "/api/v1/fiscal/**",
                        "/api/v1/expense-documents/**", "/api/v1/budgets/**",
                        "/api/v1/balances/**", "/api/v1/cash-registers/**",
                        "/api/v1/payment-methods/**", "/api/v1/audit/**",
                        "/api/v1/reports/finance/**")
                    .hasAnyRole(ADMINISTRATIVE_ROLES)

                .requestMatchers("/api/v1/members/**", "/api/v1/memberships/**",
                        "/api/v1/users/**", "/api/v1/medical-certificates/**",
                        "/api/v1/reports/members/**")
                    .hasAnyRole(MEMBER_MANAGEMENT_ROLES)

                .requestMatchers("/api/v1/activities/**", "/api/v1/reports/activities/**")
                    .hasAnyRole(ACTIVITY_ROLES)
                .requestMatchers("/api/attendance/**", "/api/v1/attendance/**")
                    .hasAnyRole(ATTENDANCE_ROLES)
                .requestMatchers("/api/v1/events/**", "/api/v1/reports/events/**")
                    .hasAnyRole(EVENT_ROLES)
                .requestMatchers("/api/v1/volunteers/**")
                    .hasAnyRole(VOLUNTEER_MANAGEMENT_ROLES)
                .requestMatchers("/api/v1/inventory/**")
                    .hasAnyRole("PRESIDENTE", "OPERATORE_ALIMENTARE")
                .requestMatchers("/api/v1/projects/**")
                    .hasAnyRole(PROJECT_ROLES)
                .requestMatchers("/api/v1/communications/**")
                    .hasAnyRole("PRESIDENTE", "COORDINATORE_SERVIZI")
                .requestMatchers("/api/v1/certificates/**")
                    .hasAnyRole(CERTIFICATE_ROLES)
                .requestMatchers("/api/v1/assemblies/**", "/api/v1/reports/assemblies/**")
                    .hasAnyRole(ADMINISTRATIVE_ROLES)
                .requestMatchers("/api/associations/**", "/api/v1/associations/**",
                        "/api/v1/signatures/**")
                    .hasAnyRole(ADMINISTRATIVE_ROLES)

                // Le funzioni non ancora assegnate a un reparto (backup, sync,
                // import CSV, coupon, notifiche per id, risorse e federazione)
                // restano del solo presidente. Un nuovo endpoint non diventa
                // accessibile per errore a tutti i collaboratori.
                .requestMatchers("/api/**").hasRole("PRESIDENTE")
                .anyRequest().denyAll()
        );

        http.oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(new ClerkRoleConverter(clerkProperties)))
        );

        http.httpBasic(AbstractHttpConfigurer::disable);
        http.formLogin(AbstractHttpConfigurer::disable);
        http.logout(AbstractHttpConfigurer::disable);

        return http.build();
    }

    @Bean
    JwtDecoder cloudJwtDecoder() {
        NimbusJwtDecoder decoder = (NimbusJwtDecoder) JwtDecoders
                .fromIssuerLocation(clerkProperties.getIssuerUri());
        decoder.setJwtValidator(cloudTokenValidator());
        return decoder;
    }

    OAuth2TokenValidator<Jwt> cloudTokenValidator() {
        OAuth2TokenValidator<Jwt> issuerValidator =
                JwtValidators.createDefaultWithIssuer(clerkProperties.getIssuerUri());
        String audience = clerkProperties.getAudience();
        if (audience != null && !audience.isBlank()) {
            return new DelegatingOAuth2TokenValidator<>(issuerValidator, new AudienceValidator(audience));
        }
        return issuerValidator;
    }

    @Bean
    CorsConfigurationSource cloudCorsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Association-Id"));
        // Con i Bearer token non servono cookie: tenere false evita di dover
        // scegliere fra credenziali e origini larghe.
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
