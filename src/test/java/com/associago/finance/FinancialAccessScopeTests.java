package com.associago.finance;

import com.associago.membership.UserAssociation;
import com.associago.membership.repository.UserAssociationRepository;
import com.associago.security.ClerkIdentityService;
import com.associago.suite.*;
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

class FinancialAccessScopeTests {
    FinancialAccess access;
    UserAssociation membership;
    JwtAuthenticationToken actor;
    @BeforeEach void setup() {
        MockEnvironment env=new MockEnvironment(); env.setActiveProfiles("cloud");
        ClerkIdentityService identities=mock(ClerkIdentityService.class);
        UserAssociationRepository memberships=mock(UserAssociationRepository.class);
        User user=new User(); user.setId(7L);
        membership=new UserAssociation(); membership.setStatus("ACTIVE");
        when(identities.resolve(any())).thenReturn(Optional.of(user));
        when(memberships.findByUserIdAndAssociationId(7L,1L)).thenReturn(Optional.of(membership));
        when(memberships.findByUserIdAndAssociationId(7L,2L)).thenReturn(Optional.empty());
        access=new FinancialAccess(new SuiteAccess(new SuiteSettings(env),env,identities,memberships),env);
        actor=new JwtAuthenticationToken(Jwt.withTokenValue("fixture").header("alg","none").subject("verified-user").build(),List.of(new SimpleGrantedAuthority("ROLE_RESPONSABILE_AMMINISTRATIVO")));
    }
    @Test void verifiedMembershipAndFinancialRoleAuthorizeOnlyTheirOwnAssociation() {
        assertEquals(1L,access.readAssociation(actor,1L,null));
        assertEquals(403,assertThrows(SuiteException.class,() -> access.requireRecord(actor,2L,null)).getStatus());
        assertEquals(403,assertThrows(SuiteException.class,() -> access.requireRecord(actor,1L,2L)).getStatus());
        membership.setStatus("SUSPENDED");
        assertEquals(403,assertThrows(SuiteException.class,() -> access.requireRecord(actor,1L,null)).getStatus());
    }
    @Test void cloudReadRequiresExplicitScopeAndCoordinatorCannotReadFinance() {
        assertEquals(400,assertThrows(SuiteException.class,() -> access.readAssociation(actor,null,null)).getStatus());
        var coordinator=new JwtAuthenticationToken(actor.getToken(),List.of(new SimpleGrantedAuthority("ROLE_COORDINATORE_SERVIZI")));
        assertEquals(403,assertThrows(SuiteException.class,() -> access.requireRecord(coordinator,1L,null)).getStatus());
    }
    @Test void desktopReadRemainsCompatibleWithoutAnAssociationHeader() {
        var env=new MockEnvironment(); env.setActiveProfiles("desktop");
        var local=new FinancialAccess(new SuiteAccess(new SuiteSettings(env),env,null,null),env);
        assertNull(local.readAssociation(null,null,null));
    }
}
