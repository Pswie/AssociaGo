package com.associago.project;

import com.associago.project.dto.ProjectBalance;
import com.associago.suite.SuiteAccess;
import com.associago.suite.SuiteException;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * API dei progetti.
 *
 * @since 0.2.0
 */
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final SuiteAccess access;

    public ProjectController(ProjectService projectService, SuiteAccess access) {
        this.projectService = projectService;
        this.access = access;
    }

    @GetMapping
    public List<Project> list(@RequestParam Long associationId, Authentication authentication) {
        access.requireAssociation(authentication, associationId);
        return projectService.findByAssociation(associationId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Project> byId(@PathVariable Long id, Authentication authentication) {
        check(id, authentication);
        return projectService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** Rendiconto di tutti i progetti: budget contro speso reale. */
    @GetMapping("/balances")
    public List<ProjectBalance> balances(@RequestParam Long associationId, Authentication authentication) {
        access.requireAssociation(authentication, associationId);
        financial(authentication);
        return projectService.balances(associationId, LocalDate.now());
    }

    @GetMapping("/{id}/balance")
    public ResponseEntity<ProjectBalance> balance(@PathVariable Long id, Authentication authentication) {
        check(id, authentication);
        financial(authentication);
        return projectService.balance(id, LocalDate.now())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Project project, Authentication authentication) {
        access.requireAssociation(authentication, project.getAssociationId());
        project.setId(null);
        try {
            return ResponseEntity.ok(projectService.save(project));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Project details, Authentication authentication) {
        check(id, authentication);
        try {
            return projectService.update(id, details)
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        check(id, authentication);
        return projectService.delete(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    private void check(Long id, Authentication authentication) {
        var project = projectService.findById(id).orElseThrow(() -> new SuiteException(404, "PROJECT_MISSING", "Progetto non trovato."));
        access.requireAssociation(authentication, project.getAssociationId());
    }

    private void financial(Authentication authentication) {
        if (!access.mayReadFinancial(authentication)) throw new SuiteException(403, "FINANCIAL_FORBIDDEN", "Il ruolo non consente di consultare il rendiconto.");
    }
}
