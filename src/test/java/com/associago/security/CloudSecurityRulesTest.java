package com.associago.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le regole di accesso del profilo {@code cloud}.
 *
 * <p>Il difetto che questa classe presidia è preciso: nella configurazione
 * originale, con {@code auth.enabled=false}, ogni endpoint era
 * {@code permitAll()}. Su un URL pubblico significa anagrafiche e contabilità
 * leggibili da chiunque conosca l'indirizzo. Qui si verifica che sul web non
 * possa più succedere.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("cloud")
@TestPropertySource(properties = {
    "associago.clerk.issuer-uri=https://esempio.clerk.accounts.dev",
    "associago.cors.allowed-origins=https://esempio.org",
    "spring.datasource.url=jdbc:h2:mem:auth-rules;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    // application.yaml disattiva la generazione dello schema a piu' livelli
    // perche' in esercizio e' Flyway a possederlo: qui vanno scavalcati tutti,
    // altrimenti H2 resta vuoto.
    "spring.jpa.properties.hibernate.hbm2ddl.auto=create-drop",
    "spring.jpa.properties.jakarta.persistence.schema-generation.database.action=drop-and-create",
    "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=true",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
@DisplayName("Profilo cloud: regole di accesso")
class CloudSecurityRulesTest {

    /**
     * Sostituisce il decoder reale, che all'avvio contatterebbe il JWKS di
     * Clerk. I test usano {@code .with(jwt())}, che inietta l'autenticazione
     * senza passare dalla verifica della firma: qui interessano le regole di
     * accesso, non la crittografia.
     */
    @MockitoBean
    JwtDecoder jwtDecoder;

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("senza token le API rispondono 401, non 200")
    void apiRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/associations"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("con un token valido le API rispondono")
    void apiAcceptsValidToken() throws Exception {
        mockMvc.perform(get("/api/v1/associations")
                        .with(jwt().jwt(builder -> builder.claim("role", "presidente"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("il login con master password è chiuso anche con un token valido")
    void masterPasswordLoginIsClosed() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .with(jwt())
                        .contentType("application/json")
                        .content("{\"associationId\":1,\"password\":\"qualsiasi\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("gli alias del login e del recupero sono chiusi anche al presidente")
    void masterPasswordAliasesAreClosedForPresident() throws Exception {
        for (String path : new String[]{
                "/api/auth/login", "/api/v1/auth/login",
                "/api/auth/recovery/reset-password", "/api/v1/auth/recovery/reset-password"}) {
            mockMvc.perform(post(path)
                            .with(jwt().authorities(role("PRESIDENTE")))
                            .contentType("application/json")
                            .content("{}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("gli alias del setup non creano nuove associazioni dal profilo cloud")
    void associationSetupAliasesAreClosedForPresident() throws Exception {
        for (String path : new String[]{"/api/associations/setup", "/api/v1/associations/setup"}) {
            mockMvc.perform(post(path)
                            .with(jwt().authorities(role("PRESIDENTE")))
                            .contentType("application/json")
                            .content("{}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("il reset password via codice fiscale è chiuso")
    void fiscalCodeRecoveryIsClosed() throws Exception {
        mockMvc.perform(get("/api/auth/recovery/associations"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/auth/recovery/associations").with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("gli endpoint di setup non sono raggiungibili dal web")
    void setupEndpointsAreClosed() throws Exception {
        mockMvc.perform(get("/api/setup/status").with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("l'health check resta pubblico, serve al load balancer")
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    // Nota sui test seguenti: il post-processor jwt() di spring-security-test
    // NON passa da ClerkRoleConverter, quindi le authority vanno dichiarate a
    // mano. Qui si collauda la matrice dei permessi; la traduzione dei claim
    // Clerk in authority è collaudata a parte, in ClerkRoleConverterTest.

    private static SimpleGrantedAuthority role(String name) {
        return new SimpleGrantedAuthority("ROLE_" + name);
    }

    @Test
    @DisplayName("un socio non entra nel gestionale")
    void plainMemberCannotReachManagementApi() throws Exception {
        mockMvc.perform(get("/api/v1/members").with(jwt().authorities(role("SOCIO"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/transactions").with(jwt().authorities(role("SOCIO"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("un socio raggiunge la propria area personale")
    void plainMemberReachesOwnArea() throws Exception {
        mockMvc.perform(get("/api/v1/me/presenze").with(jwt().authorities(role("SOCIO"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("un token senza ruoli non apre il gestionale")
    void tokenWithoutRolesGrantsNothing() throws Exception {
        mockMvc.perform(get("/api/v1/members").with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("il referente volontari vede volontari e soci, non la contabilita")
    void volunteerManagerIsLimitedToItsModules() throws Exception {
        mockMvc.perform(get("/api/v1/volunteers")
                        .with(jwt().authorities(role("REFERENTE_VOLONTARI"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/members")
                        .with(jwt().authorities(role("REFERENTE_VOLONTARI"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/finance/transactions")
                        .with(jwt().authorities(role("REFERENTE_VOLONTARI"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("il responsabile amministrativo vede contabilita e soci, non i volontari")
    void administrativeManagerIsLimitedToItsModules() throws Exception {
        mockMvc.perform(get("/api/v1/finance/transactions")
                        .with(jwt().authorities(role("RESPONSABILE_AMMINISTRATIVO"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/members")
                        .with(jwt().authorities(role("RESPONSABILE_AMMINISTRATIVO"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/volunteers")
                        .with(jwt().authorities(role("RESPONSABILE_AMMINISTRATIVO"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("il coordinatore servizi vede attivita ed eventi, non la contabilita")
    void serviceCoordinatorIsLimitedToItsModules() throws Exception {
        mockMvc.perform(get("/api/v1/activities")
                        .with(jwt().authorities(role("COORDINATORE_SERVIZI"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/events")
                        .with(jwt().authorities(role("COORDINATORE_SERVIZI"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/finance/transactions")
                        .with(jwt().authorities(role("COORDINATORE_SERVIZI"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("l'operatore alimentare vede inventario e presenze, non i soci")
    void foodOperatorIsLimitedToItsModules() throws Exception {
        mockMvc.perform(get("/api/v1/inventory")
                        .with(jwt().authorities(role("OPERATORE_ALIMENTARE"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/attendance/activity/1")
                        .with(jwt().authorities(role("OPERATORE_ALIMENTARE"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/members")
                        .with(jwt().authorities(role("OPERATORE_ALIMENTARE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("il presidente conserva accesso a ogni modulo")
    void presidentReachesEveryManagementModule() throws Exception {
        mockMvc.perform(get("/api/v1/volunteers")
                        .with(jwt().authorities(role("PRESIDENTE"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/finance/transactions")
                        .with(jwt().authorities(role("PRESIDENTE"))))
                .andExpect(status().isOk());

        // I coupon non sono ancora delegati a un reparto: cadono nella regola
        // finale del solo presidente.
        mockMvc.perform(get("/api/v1/coupons/association/1")
                        .with(jwt().authorities(role("PRESIDENTE"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/coupons/association/1")
                        .with(jwt().authorities(role("COORDINATORE_SERVIZI"))))
                .andExpect(status().isForbidden());
    }
}
