package com.associago.communication;

import com.associago.security.ActorResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/communications")
public class CommunicationController {

    private final CommunicationService communicationService;
    private final ActorResolver actorResolver;

    public CommunicationController(CommunicationService communicationService, ActorResolver actorResolver) {
        this.communicationService = communicationService;
        this.actorResolver = actorResolver;
    }

    // --- Templates ---

    @GetMapping("/templates")
    public List<CommunicationTemplate> getTemplates(@RequestParam Long associationId) {
        return communicationService.getTemplates(associationId);
    }

    @PostMapping("/templates")
    public CommunicationTemplate createTemplate(@RequestBody CommunicationTemplate template) {
        return communicationService.saveTemplate(template);
    }

    @PutMapping("/templates/{id}")
    public CommunicationTemplate updateTemplate(@PathVariable Long id, @RequestBody CommunicationTemplate template) {
        template.setId(id);
        return communicationService.saveTemplate(template);
    }

    @DeleteMapping("/templates/{id}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable Long id) {
        communicationService.deleteTemplate(id);
        return ResponseEntity.noContent().build();
    }

    // --- Communications ---

    @GetMapping
    public List<Communication> getAll(@RequestParam Long associationId) {
        return communicationService.getCommunications(associationId);
    }

    @GetMapping("/{id}")
    public Communication getById(@PathVariable Long id) {
        return communicationService.getById(id);
    }

    @PostMapping
    public Communication create(@RequestBody Communication communication) {
        return communicationService.create(communication);
    }

    @PutMapping("/{id}")
    public Communication update(@PathVariable Long id, @RequestBody Communication communication) {
        return communicationService.update(id, communication);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        communicationService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/resolve-recipients")
    public List<CommunicationRecipient> resolveRecipients(@PathVariable Long id) {
        return communicationService.resolveRecipients(id);
    }

    @GetMapping("/{id}/recipients")
    public List<CommunicationRecipient> getRecipients(@PathVariable Long id) {
        return communicationService.getRecipients(id);
    }

    @PostMapping("/{id}/send")
    public Communication send(@PathVariable Long id, @RequestParam(required = false) Long sentBy,
                              Authentication authentication) {
        return communicationService.send(id, actorResolver.resolve(authentication, sentBy));
    }
}
