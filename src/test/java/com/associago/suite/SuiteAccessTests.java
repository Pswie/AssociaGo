package com.associago.suite;

import com.associago.membership.UserAssociation;
import com.associago.membership.repository.UserAssociationRepository;
import com.associago.security.ClerkIdentityService;
import com.associago.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SuiteAccessTests {
    private final ClerkIdentityService identities = mock(ClerkIdentityService.class);
    private final UserAssociationRepository memberships = mock(UserAssociationRepository.class);
    private final MockEnvironment environment = new MockEnvironment();
    private SuiteAccess access;
    private JwtAuthenticationToken coordinator;
    private User user;

    @BeforeEach
    void setUp() {
        environment.setActiveProfiles("cloud");
        access = new SuiteAccess(new SuiteSettings(environment), environment, identities, memberships);
        coordinator = token("COORDINATORE_SERVIZI");
        user = new User(); user.setId(7L);
        when(identities.resolve(coordinator)).thenReturn(Optional.of(user));
    }

    @Test
    void requiresJwtIdentityAndActualMembershipInRequestedAssociation() {
        denied(() -> access.requireAssociation(null, 1L), 401);
        denied(() -> access.requireAssociation(new UsernamePasswordAuthenticationToken("user", "", List.of()), 1L), 401);
        denied(() -> access.requireAssociation(coordinator, 1L), 403);
        UserAssociation membership = new UserAssociation();
        when(memberships.findByUserIdAndAssociationId(7L, 1L)).thenReturn(Optional.of(membership));
        assertThatCode(() -> access.requireAssociation(coordinator, 1L)).doesNotThrowAnyException();
        denied(() -> access.requireAssociation(coordinator, 2L), 403);
    }

    @ParameterizedTest
    @ValueSource(strings = {"EXPIRED", "SUSPENDED", "RESIGNED"})
    void inactiveMembershipCannotAuthorizeProjectAccess(String status) {
        UserAssociation membership = new UserAssociation(); membership.setStatus(status);
        when(memberships.findByUserIdAndAssociationId(7L, 1L)).thenReturn(Optional.of(membership));
        denied(() -> access.requireAssociation(coordinator, 1L), 403);
    }

    @Test
    void roleAndLinkedIdentityAreRequiredAndFinancialAccessIsRestricted() {
        denied(() -> access.requireAssociation(token("SOCIO"), 1L), 403);
        when(identities.resolve(coordinator)).thenReturn(Optional.empty());
        denied(() -> access.requireAssociation(coordinator, 1L), 403);
        assertThat(access.mayReadFinancial(coordinator)).isFalse();
        assertThat(access.mayReadFinancial(token("PRESIDENTE"))).isTrue();
        assertThat(access.mayReadFinancial(token("RESPONSABILE_AMMINISTRATIVO"))).isTrue();
        assertThat(access.actorSubject(coordinator)).isEqualTo("test-clerk-user");
    }

    @Test
    void desktopKeepsLocalAccessWithoutEnablingPeerIntegration() {
        environment.setActiveProfiles("desktop");
        assertThatCode(() -> access.requireAssociation(null, 1L)).doesNotThrowAnyException();
        assertThat(access.mayReadFinancial(null)).isTrue();
        denied(() -> access.requireUser(null), 503);
    }

    private JwtAuthenticationToken token(String role) {
        Jwt jwt = Jwt.withTokenValue("test-only").header("alg", "RS256").subject("test-clerk-user").build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private void denied(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, int status) {
        assertThatThrownBy(action).isInstanceOf(SuiteException.class)
                .satisfies(error -> assertThat(((SuiteException) error).getStatus()).isEqualTo(status));
    }
}
