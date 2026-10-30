package com.associago.activity;

import com.associago.association.Association;
import com.associago.finance.FinancialAccess;
import com.associago.membership.UserAssociation;
import com.associago.membership.repository.UserAssociationRepository;
import com.associago.security.ClerkIdentityService;
import com.associago.suite.*;
import com.associago.user.User;
import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ActivityAccessTests {
    ActivityAccess access;
    UserAssociation membership;
    UserAssociationRepository memberships;
    @BeforeEach void setup() {
        var env = new MockEnvironment(); env.setActiveProfiles("cloud");
        var identities = mock(ClerkIdentityService.class); memberships = mock(UserAssociationRepository.class);
        var user = new User(); user.setId(7L); user.setFirstName("Fixture"); user.setEmail("private@example.invalid");
        var association = new Association(); association.setId(1L);
        membership = new UserAssociation(); membership.setId(301L); membership.setUser(user); membership.setAssociation(association);
        when(identities.resolve(any())).thenReturn(Optional.of(user));
        when(memberships.findByUserIdAndAssociationId(7L, 1L)).thenReturn(Optional.of(membership));
        when(memberships.findByUserId(7L)).thenReturn(List.of(membership));
        when(memberships.findByAssociationId(1L)).thenReturn(List.of(membership));
        access = new ActivityAccess(env, identities, memberships,
                new FinancialAccess(new SuiteAccess(new SuiteSettings(env), env, identities, memberships), env));
        actor("ROLE_COORDINATORE_SERVIZI");
    }
    @AfterEach void clean() { SecurityContextHolder.clearContext(); RequestContextHolder.resetRequestAttributes(); }
    @Test void scopeIsActualMembershipAndSelectedHeaderCannotOverrideIt() {
        assertDoesNotThrow(() -> access.require(1L));
        assertEquals(403, assertThrows(SuiteException.class, () -> access.require(2L)).getStatus());
        var request = new MockHttpServletRequest(); request.addHeader("X-Association-Id", "2");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        assertEquals(403, assertThrows(SuiteException.class, () -> access.require(1L)).getStatus());
    }
    @Test void readAndFinancialWriteRolesAreSeparateAndMembershipMustBeActive() {
        assertEquals(403, assertThrows(SuiteException.class, () -> access.requireFinancial(1L)).getStatus());
        actor("ROLE_RESPONSABILE_AMMINISTRATIVO");
        assertDoesNotThrow(() -> access.require(1L)); assertDoesNotThrow(() -> access.requireFinancial(1L));
        assertEquals(403, assertThrows(SuiteException.class, () -> access.requireWrite(1L)).getStatus());
        membership.setStatus("SUSPENDED");
        assertEquals(403, assertThrows(SuiteException.class, () -> access.require(1L)).getStatus());
    }
    @Test void listRequiresExplicitScopeAndNewResourceCannotForgeItsIdOrParent() {
        assertEquals(400, assertThrows(SuiteException.class, () -> access.association(null)).getStatus());
        assertEquals(400, assertThrows(SuiteException.class, () -> access.creating(99L)).getStatus());
        assertEquals(409, assertThrows(SuiteException.class, () -> access.immutable(2L, 1L)).getStatus());
        assertDoesNotThrow(() -> access.immutable(null, 1L));
    }
    @Test void candidateReferenceKeepsUserIdSeparateFromMembershipAndNeverIncludesEmail() {
        var rows = access.participantCandidates(1L);
        assertEquals(7L, rows.getFirst().userId()); assertEquals(301L, rows.getFirst().membershipId());
        assertFalse(rows.toString().contains("private@example.invalid"));
        assertEquals(7L, access.participantUser(7L, 1L).getId());
        assertEquals(403, assertThrows(SuiteException.class, () -> access.participantUser(301L, 1L)).getStatus());
    }
    static void actor(String role) {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                Jwt.withTokenValue("fixture").header("alg", "none").subject("linked-user").build(),
                List.of(new SimpleGrantedAuthority(role))));
    }
}
