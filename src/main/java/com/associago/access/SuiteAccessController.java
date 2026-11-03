package com.associago.access;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/access/v1")
public class SuiteAccessController {
    private final SuiteAccessService service;
    public SuiteAccessController(SuiteAccessService service) { this.service = service; }
    @ModelAttribute
    public void privateResponse(jakarta.servlet.http.HttpServletResponse response) { response.setHeader("Cache-Control", "no-store"); }
    @GetMapping("/me")
    @SuppressWarnings("unchecked")
    public Map<String, Object> me(Authentication authentication, jakarta.servlet.http.HttpServletRequest request) {
        // Reuse only the decision made by our filter for this exact request.
        // No cache survives the request, so revocations still apply immediately.
        Object current = request.getAttribute(SuiteAccessService.REQUEST_ATTRIBUTE);
        if (current instanceof Map<?, ?> view && service.subject(authentication).equals(view.get("subject")))
            return (Map<String, Object>) view;
        return service.self(authentication);
    }
    @GetMapping("/catalog")
    public Map<String, Object> catalog(Authentication authentication) { return service.catalog(authentication); }
    @GetMapping("/users")
    public Map<String, Object> users(Authentication authentication,
            @RequestParam(defaultValue = "0") int offset, @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "") String q) { return service.list(authentication, offset, limit, q); }
    @PostMapping("/users/sync")
    public Map<String, Object> sync(Authentication authentication, @RequestBody(required = false) JsonNode body) {
        int offset = 0;
        if (body != null) {
            if (!body.isObject() || body.size() > 1 || body.has("offset") &&
                    (!body.path("offset").isIntegralNumber() || !body.path("offset").canConvertToInt()))
                throw new SuiteAccessException(400, "INVALID_OFFSET", "Pagina non valida.");
            body.fieldNames().forEachRemaining(key -> { if (!key.equals("offset")) throw new SuiteAccessException(400, "INVALID_OFFSET", "Pagina non valida."); });
            offset = body.path("offset").asInt(0);
        }
        return service.sync(authentication, offset);
    }
    @PutMapping("/users/{subject}")
    public Map<String, Object> update(Authentication authentication, @PathVariable String subject, @RequestBody JsonNode body) {
        return service.update(authentication, subject, body);
    }
}
