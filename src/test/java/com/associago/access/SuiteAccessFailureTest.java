package com.associago.access;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import jakarta.servlet.FilterChain;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SuiteAccessFailureTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    private JwtAuthenticationToken actor(String authority) {
        return new JwtAuthenticationToken(Jwt.withTokenValue("synthetic-token").header("alg", "RS256")
                .subject("user_synthetic000001").build(), List.of(new SimpleGrantedAuthority(authority)));
    }
    @Test void unavailableAuthorityDoesNotFallBackToAnOldAdministratorClaim() throws Exception {
        var service = mock(SuiteAccessService.class);
        when(service.enabled()).thenReturn(true);
        var authentication = actor("ROLE_PRESIDENTE");
        when(service.self(authentication)).thenThrow(new DataAccessResourceFailureException("synthetic isolated outage"));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        var request = new MockHttpServletRequest("GET", "/api/v1/members"); request.setServletPath("/api/v1/members");
        var response = new MockHttpServletResponse(); var chain = mock(FilterChain.class);
        new SuiteAccessFilter(service, new ObjectMapper()).doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("ACCESS_UNAVAILABLE").doesNotContain("synthetic isolated outage");
        verifyNoInteractions(chain);
    }
    @Test void moduleGrantNeverAuthorizesAnotherAssociation() {
        var env = new MockEnvironment().withProperty("SUITE_ACCESS_ENABLED", "true").withProperty("SUITE_ASSOCIATION_ID", "1");
        assertThatCode(() -> SuiteAssociationScope.require(env, actor("SCOPE_associago.finance.read"), 1L, "associago.finance.read"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> SuiteAssociationScope.require(env, actor("SCOPE_associago.finance.read"), 2L, "associago.finance.read"))
                .isInstanceOf(com.associago.suite.SuiteException.class).hasMessage("Associazione non autorizzata.");
    }
}
