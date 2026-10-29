package com.associago.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Traduzione dei claim Clerk in authority.
 *
 * <p>Collaudata a parte perché il post-processor {@code jwt()} usato nei test
 * MVC non passa da questo converter: senza questa classe, la mappatura dei
 * ruoli non sarebbe verificata da nessuno.
 */
@DisplayName("Da claim Clerk a ruoli Spring")
class ClerkRoleConverterTest {

    private final ClerkRoleConverter converter = new ClerkRoleConverter(new ClerkProperties());

    private static Jwt jwtWith(Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("user_2abc")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600));
        claims.forEach(builder::claim);
        return builder.build();
    }

    private List<String> authoritiesOf(Map<String, Object> claims) {
        return converter.convert(jwtWith(claims)).getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }

    @Test
    @DisplayName("un ruolo noto diventa un'authority ROLE_*")
    void knownRoleBecomesAuthority() {
        assertThat(authoritiesOf(Map.of("role", "referente-volontari")))
                .containsExactly("ROLE_REFERENTE_VOLONTARI");
    }

    @Test
    @DisplayName("il ruolo si accetta anche dentro public_metadata")
    void roleInsidePublicMetadata() {
        assertThat(authoritiesOf(Map.of("public_metadata", Map.of("role", "presidente"))))
                .containsExactly("ROLE_PRESIDENTE");
    }

    @Test
    @DisplayName("il ruolo admin compatto di Clerk v2 concede l'accesso completo")
    void clerkV2OrganizationAdminBecomesPresident() {
        assertThat(authoritiesOf(Map.of("o", Map.of(
                "id", "org_123",
                "rol", "admin",
                "slg", "angeli-del-futuro"))))
                .containsExactly("ROLE_PRESIDENTE");
    }

    @Test
    @DisplayName("il ruolo member compatto di Clerk v2 resta nell'area personale")
    void clerkV2OrganizationMemberBecomesMember() {
        assertThat(authoritiesOf(Map.of("o", Map.of("rol", "member"))))
                .containsExactly("ROLE_SOCIO");
    }

    @Test
    @DisplayName("un ruolo organizzativo personalizzato di Clerk v2 mantiene lo slug")
    void clerkV2CustomOrganizationRoleIsAccepted() {
        // Clerk ammette underscore, non trattini, nelle chiavi org:*.
        assertThat(authoritiesOf(Map.of("o", Map.of("rol", "referente_volontari"))))
                .containsExactly("ROLE_REFERENTE_VOLONTARI");
    }

    @Test
    @DisplayName("il ruolo organizzativo prevale su metadata rimasti da un ruolo precedente")
    void organizationRoleOverridesLegacyMetadata() {
        assertThat(authoritiesOf(Map.of(
                "o", Map.of("rol", "member"),
                "public_metadata", Map.of("role", "presidente"))))
                .containsExactly("ROLE_SOCIO");
    }

    @Test
    @DisplayName("un ruolo organizzativo vuoto non ripristina privilegi nei metadata")
    void emptyOrganizationRoleDoesNotRestoreLegacyPrivileges() {
        assertThat(authoritiesOf(Map.of(
                "o", Map.of("rol", ""),
                "public_metadata", Map.of("role", "presidente"))))
                .isEmpty();
    }

    @Test
    @DisplayName("un ruolo organizzativo malformato non ripristina privilegi nei metadata")
    void malformedOrganizationRoleDoesNotRestoreLegacyPrivileges() {
        assertThat(authoritiesOf(Map.of(
                "o", Map.of("rol", 42),
                "role", "presidente")))
                .isEmpty();
    }

    @Test
    @DisplayName("un ruolo organizzativo sconosciuto non ripristina privilegi nei metadata")
    void unknownOrganizationRoleDoesNotRestoreLegacyPrivileges() {
        assertThat(authoritiesOf(Map.of(
                "o", Map.of("rol", "revoked"),
                "public_metadata", Map.of("role", "presidente"))))
                .isEmpty();
    }

    @Test
    @DisplayName("una lista di ruoli produce piu' authority")
    void roleListIsAccepted() {
        assertThat(authoritiesOf(Map.of("roles", List.of("psicologo", "socio"))))
                .containsExactlyInAnyOrder("ROLE_PSICOLOGO", "ROLE_SOCIO");
    }

    @Test
    @DisplayName("maiuscole, spazi e underscore descrivono lo stesso ruolo")
    void roleIsNormalized() {
        assertThat(authoritiesOf(Map.of("role", "Coordinatore_Servizi")))
                .containsExactly("ROLE_COORDINATORE_SERVIZI");
        assertThat(authoritiesOf(Map.of("role", " orientatore servizi ")))
                .containsExactly("ROLE_ORIENTATORE_SERVIZI");
    }

    @Test
    @DisplayName("un ruolo inventato nel token viene scartato")
    void unknownRoleIsDropped() {
        // È il punto che conta: chi confeziona il token non decide i permessi.
        assertThat(authoritiesOf(Map.of("role", "amministratore-supremo"))).isEmpty();
        assertThat(authoritiesOf(Map.of("role", "ROLE_ADMIN"))).isEmpty();
        assertThat(authoritiesOf(Map.of("o", Map.of("rol", "super-admin")))).isEmpty();
    }

    @Test
    @DisplayName("un token senza claim di ruolo non concede niente")
    void noRoleClaimGrantsNothing() {
        assertThat(authoritiesOf(Map.of("email", "tizio@esempio.org"))).isEmpty();
    }
}
