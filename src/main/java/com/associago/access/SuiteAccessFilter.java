package com.associago.access;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.*;

/** Installed inside the JWT chain only, never as a global servlet filter. */
public class SuiteAccessFilter extends OncePerRequestFilter {
    private final SuiteAccessService service;
    private final ObjectMapper mapper;
    public SuiteAccessFilter(SuiteAccessService service, ObjectMapper mapper) { this.service = service; this.mapper = mapper; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        String path = SuitePermissionAuthorizationManager.applicationPath(request);
        if (!service.enabled() || !(authentication instanceof JwtAuthenticationToken jwt) ||
                !path.startsWith("/api/") || path.startsWith("/api/integrations/v1/peer/")) {
            chain.doFilter(request, response); return;
        }
        try {
            Map<String, Object> view = service.self(jwt);
            request.setAttribute(SuiteAccessService.REQUEST_ATTRIBUTE, view);
            var authorities = new ArrayList<SimpleGrantedAuthority>();
            for (var permission : (Collection<?>) view.get("permissions")) authorities.add(new SimpleGrantedAuthority("SCOPE_" + permission));
            if (Boolean.TRUE.equals(view.get("owner"))) authorities.add(new SimpleGrantedAuthority("ROLE_PRESIDENTE"));
            var constrained = new JwtAuthenticationToken(jwt.getToken(), authorities, jwt.getName());
            constrained.setDetails(jwt.getDetails());
            SecurityContextHolder.getContext().setAuthentication(constrained);
        } catch (SuiteAccessException error) {
            problem(response, error.status(), error.code(), error.getMessage()); return;
        } catch (org.springframework.dao.DataAccessException error) {
            problem(response, 503, "ACCESS_UNAVAILABLE", "Verifica degli accessi temporaneamente non disponibile."); return;
        }
        chain.doFilter(request, response);
    }
    private void problem(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status); response.setContentType("application/json"); response.setHeader("Cache-Control", "no-store");
        mapper.writeValue(response.getOutputStream(), Map.of("code", code, "message", message));
    }
}
