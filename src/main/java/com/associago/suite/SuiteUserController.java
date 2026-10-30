package com.associago.suite;

import com.associago.project.Project;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/integrations/v1")
public class SuiteUserController {
    private final SuiteSettings settings;
    private final SuiteAccess access;
    private final SuiteGateway gateway;
    private final SuiteLocalProjectService local;

    public SuiteUserController(SuiteSettings settings, SuiteAccess access, SuiteGateway gateway, SuiteLocalProjectService local) {
        this.settings = settings;
        this.access = access;
        this.gateway = gateway;
        this.local = local;
    }

    @GetMapping("/status")
    public Map<String, Boolean> status() { return Map.of("enabled", settings.enabled()); }

    @GetMapping("/projects")
    public JsonNode list(Authentication authentication) {
        access.requireUser(authentication);
        return gateway.userView(gateway.request("GET", "", null), access.mayReadFinancial(authentication));
    }

    @GetMapping("/projects/{id}")
    public JsonNode get(@PathVariable String id, Authentication authentication) {
        access.requireUser(authentication);
        SuiteSettings.uuid(id, "Progetto condiviso");
        return gateway.userView(gateway.request("GET", "/" + id, null), access.mayReadFinancial(authentication));
    }

    @PostMapping("/projects/{id}/sync")
    public JsonNode sync(@PathVariable String id, Authentication authentication) {
        access.requireUser(authentication);
        SuiteSettings.uuid(id, "Progetto condiviso");
        return gateway.userView(gateway.request("POST", "/" + id + "/sync", Map.of()), access.mayReadFinancial(authentication));
    }

    @PostMapping("/projects/share")
    public JsonNode share(@RequestBody SuitePayloads.LocalLink body, Authentication authentication) {
        access.requireUser(authentication);
        Project project = local.local(SuiteLocalProjectService.localId(body.localId()));
        var existing = local.linkedProject(project.getId());
        if (existing.isPresent()) {
            // Recover a lost peer reply through the durable local mapping.
            // Never seed a second planning project for a projection already received.
            JsonNode context = registerInstance(existing.get().getSuiteProjectId(), project);
            return gateway.userView(context, access.mayReadFinancial(authentication));
        }
        Map<String, Object> seed = new LinkedHashMap<>();
        seed.put("schemaVersion", 1);
        seed.put("organizationId", settings.organizationId());
        seed.put("source", Map.of("system", "associago", "entity", "project", "localId", project.getId().toString(),
                "revision", project.getUpdatedAt() == null ? "initial" : project.getUpdatedAt().toString()));
        seed.put("title", project.getName());
        seed.put("description", project.getNotes());
        seed.put("plannedBudget", project.getBudget() == null ? null : new SuitePayloads.Money(project.getBudget().toPlainString(), "EUR"));
        seed.put("projectDeadline", project.getDeadline());
        seed.put("actorSubject", access.actorSubject(authentication));
        JsonNode context = gateway.request("POST", "", seed);
        local.linkExisting(context.path("suiteProjectId").asText(), project.getId());
        return gateway.userView(context, access.mayReadFinancial(authentication));
    }

    @PostMapping("/projects/{id}/link")
    public JsonNode link(@PathVariable String id, @RequestBody SuitePayloads.LocalLink body, Authentication authentication) {
        access.requireUser(authentication);
        SuiteSettings.uuid(id, "Progetto condiviso");
        Project project = local.local(SuiteLocalProjectService.localId(body.localId()));
        local.checkLinkTarget(id, project.getId());
        JsonNode context = registerInstance(id, project);
        local.linkExisting(id, project.getId());
        return gateway.userView(context, access.mayReadFinancial(authentication));
    }

    private JsonNode registerInstance(String id, Project project) {
        return gateway.request("POST", "/" + id + "/instances", Map.of("system", "associago", "entity", "project",
                "localId", project.getId().toString(), "revision", project.getUpdatedAt() == null ? "initial" : project.getUpdatedAt().toString()));
    }
}
