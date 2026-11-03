package com.associago.access;

import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import java.util.*;
import java.util.function.Supplier;

/** Finite route map: an unclassified endpoint stays owner-only. */
public class SuitePermissionAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {
    @Override public AuthorizationDecision check(Supplier<Authentication> supplier, RequestAuthorizationContext context) {
        var request = context.getRequest();
        var authentication = supplier.get();
        String path = applicationPath(request);
        String plain = path.startsWith("/api/v1/") ? "/api/" + path.substring(8) : path;
        if (blocked(plain)) return new AuthorizationDecision(false);
        if (authentication == null || !authentication.isAuthenticated()) return new AuthorizationDecision(false);
        Object raw = request.getAttribute(SuiteAccessService.REQUEST_ATTRIBUTE);
        if (!(raw instanceof Map<?, ?> view)) return new AuthorizationDecision(false);
        if (Boolean.TRUE.equals(view.get("owner"))) return new AuthorizationDecision(true);
        if (!"active".equals(view.get("status"))) return new AuthorizationDecision(false);
        boolean read = Set.of("GET", "HEAD").contains(request.getMethod());
        var permissions = authentication.getAuthorities().stream().map(a -> a.getAuthority()).toList();
        if ((plain.equals("/api/associations") || plain.equals("/api/me")) && read || plain.startsWith("/api/app/"))
            return new AuthorizationDecision(permissions.stream().anyMatch(p -> p.startsWith("SCOPE_associago.")) && read);
        String module = module(plain);
        if (module == null) return new AuthorizationDecision(false);
        String permission = "associago." + module + (read ? ".read" : ".write");
        return new AuthorizationDecision(SuiteAccessService.permits(authentication, permission));
    }
    static boolean blocked(String path) {
        return starts(path, "/api/auth", "/api/setup", "/api/bootstrap", "/api/associations/setup");
    }
    static String applicationPath(jakarta.servlet.http.HttpServletRequest request) {
        String servletPath = request.getServletPath();
        if (servletPath != null && !servletPath.isEmpty()) return servletPath;
        return org.springframework.web.util.UrlPathHelper.defaultInstance.getPathWithinApplication(request);
    }
    static String module(String path) {
        if (path.equals("/api/me") || path.startsWith("/api/me/")) return "personal";
        if (starts(path, "/api/integrations/v1/projects")) return "projects";
        if (starts(path, "/api/dashboard", "/api/stats/general")) return "dashboard";
        if (starts(path, "/api/stats/financial", "/api/reports/finance",
                "/api/finance", "/api/fiscal", "/api/expense-documents", "/api/budgets", "/api/balances",
                "/api/cash-registers", "/api/payment-methods", "/api/audit", "/api/transactions")) return "finance";
        if (path.matches("/api/activities/[^/]+/(costs|financial-summary)(/.*)?") || starts(path, "/api/activities/costs")) return "finance";
        if (starts(path, "/api/members", "/api/memberships", "/api/users", "/api/medical-certificates", "/api/reports/members")) return "members";
        if (starts(path, "/api/activities", "/api/reports/activities", "/api/stats/activities")) return "activities";
        if (starts(path, "/api/attendance")) return "attendance";
        if (starts(path, "/api/events", "/api/reports/events")) return "events";
        if (starts(path, "/api/volunteers")) return "volunteers";
        if (starts(path, "/api/inventory")) return "inventory";
        if (starts(path, "/api/projects")) return "projects";
        if (starts(path, "/api/communications")) return "communications";
        if (starts(path, "/api/certificates")) return "certificates";
        if (starts(path, "/api/assemblies", "/api/reports/assemblies", "/api/stats/assemblies")) return "assemblies";
        if (starts(path, "/api/associations", "/api/signatures", "/api/settings")) return "settings";
        return null;
    }
    private static boolean starts(String path, String... roots) {
        return Arrays.stream(roots).anyMatch(root -> path.equals(root) || path.startsWith(root + "/"));
    }
}
