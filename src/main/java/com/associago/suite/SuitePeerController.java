package com.associago.suite;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequestMapping("/api/integrations/v1/peer/projects")
public class SuitePeerController {
    private final SuiteAccess access;
    private final SuiteLocalProjectService projects;

    public SuitePeerController(SuiteAccess access, SuiteLocalProjectService projects) {
        this.access = access;
        this.projects = projects;
    }

    @PutMapping("/{id}")
    public Map<String, Object> upsert(@PathVariable String id, @RequestBody SuitePayloads.Projection projection,
                                      HttpServletRequest request) {
        access.requirePeer(request);
        return projects.upsert(id, projection);
    }

    @GetMapping("/{id}")
    public Map<String, Object> snapshot(@PathVariable String id, HttpServletRequest request) {
        access.requirePeer(request);
        return projects.snapshot(id);
    }
}
