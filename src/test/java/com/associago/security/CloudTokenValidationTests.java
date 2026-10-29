package com.associago.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CloudTokenValidationTests {

    private static final String ISSUER = "https://example.clerk.accounts.dev";
    private static final String AUDIENCE = "associago";

    private OAuth2TokenValidator<Jwt> validator(String audience) {
        ClerkProperties properties = new ClerkProperties();
        properties.setIssuerUri(ISSUER);
        properties.setAudience(audience);
        return new CloudSecurityConfig(properties, List.of("https://example.org"))
                .cloudTokenValidator();
    }

    private Jwt token(String issuer, List<String> audiences, Instant expiration) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject("user_test")
                .issuer(issuer)
                .audience(audiences)
                .issuedAt(expiration.minusSeconds(3600))
                .expiresAt(expiration)
                .build();
    }

    @Test
    void acceptsExpectedIssuerAndAudience() {
        assertThat(validator(AUDIENCE).validate(token(ISSUER, List.of(AUDIENCE),
                Instant.now().plusSeconds(300))).hasErrors()).isFalse();
    }

    @Test
    void rejectsOtherIssuerWhenAudienceIsConfigured() {
        assertThat(validator(AUDIENCE).validate(token("https://other.example.org", List.of(AUDIENCE),
                Instant.now().plusSeconds(300))).hasErrors()).isTrue();
    }

    @Test
    void rejectsOtherAudience() {
        assertThat(validator(AUDIENCE).validate(token(ISSUER, List.of("other-app"),
                Instant.now().plusSeconds(300))).hasErrors()).isTrue();
    }

    @Test
    void rejectsMissingAudience() {
        assertThat(validator(AUDIENCE).validate(token(ISSUER, List.of(),
                Instant.now().plusSeconds(300))).hasErrors()).isTrue();
    }

    @Test
    void rejectsExpiredTokensWhenAudienceIsConfigured() {
        assertThat(validator(AUDIENCE).validate(token(ISSUER, List.of(AUDIENCE),
                Instant.now().minusSeconds(300))).hasErrors()).isTrue();
    }

    @Test
    void issuerIsStillRequiredWithoutConfiguredAudience() {
        assertThat(validator("").validate(token("https://other.example.org", List.of(),
                Instant.now().plusSeconds(300))).hasErrors()).isTrue();
        assertThat(validator("").validate(token(ISSUER, List.of(),
                Instant.now().plusSeconds(300))).hasErrors()).isFalse();
    }
}
