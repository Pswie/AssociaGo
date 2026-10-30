package com.associago.attendance;

import com.associago.membership.UserAssociation;
import com.associago.membership.repository.UserAssociationRepository;
import com.associago.security.ClerkIdentityService;
import com.associago.suite.SuiteException;
import com.associago.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AttendanceAccessTests {
    AttendanceAccess access;
    UserAssociation membership;
    ClerkIdentityService identities;
    JwtAuthenticationToken actor;
    @BeforeEach void setup() {
        var environment = new MockEnvironment(); environment.setActiveProfiles("cloud");
        identities = mock(ClerkIdentityService.class);
        var memberships = mock(UserAssociationRepository.class);
        var user = new User(); user.setId(7L);
        membership = new UserAssociation(); membership.setStatus("ACTIVE");
        when(identities.resolve(any())).thenReturn(Optional.of(user));
        when(memberships.findByUserIdAndAssociationId(7L, 1L)).thenReturn(Optional.of(membership));
        access = new AttendanceAccess(environment, identities, memberships);
        actor = actor("ROLE_REFERENTE_VOLONTARI");
    }
    @Test void attendanceOperatorRequiresVerifiedOwnActiveAssociation() {
        assertDoesNotThrow(() -> access.require(actor, 1L, 1L));
        assertEquals(403, assertThrows(SuiteException.class, () -> access.require(actor, 2L, null)).getStatus());
        assertEquals(403, assertThrows(SuiteException.class, () -> access.require(actor, 1L, 2L)).getStatus());
        membership.setStatus("SUSPENDED");
        assertEquals(403, assertThrows(SuiteException.class, () -> access.require(actor, 1L, null)).getStatus());
    }
    @Test void unrelatedRolesUnlinkedIdentitiesAndAnonymousCallsAreDenied() {
        assertEquals(403, assertThrows(SuiteException.class, () -> access.require(actor("ROLE_SOCIO"), 1L, null)).getStatus());
        assertEquals(401, assertThrows(SuiteException.class, () -> access.require(null, 1L, null)).getStatus());
        when(identities.resolve(any())).thenReturn(Optional.empty());
        assertEquals(403, assertThrows(SuiteException.class, () -> access.require(actor, 1L, null)).getStatus());
    }
    @Test void desktopRetainsLocalBehaviorButSelectedAssociationMustStillMatch() {
        var local = new AttendanceAccess(new MockEnvironment(), null, null);
        assertDoesNotThrow(() -> local.require(null, 1L, null));
        assertEquals(403, assertThrows(SuiteException.class, () -> local.require(null, 1L, 2L)).getStatus());
    }
    private static JwtAuthenticationToken actor(String role) {
        return new JwtAuthenticationToken(Jwt.withTokenValue("fixture").header("alg", "none").subject("verified-user").build(),
                List.of(new SimpleGrantedAuthority(role)));
    }
}
