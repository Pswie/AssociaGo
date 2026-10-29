package com.associago.volunteer;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Le polizze dei volontari e la verifica di copertura — art. 18 CTS.
 */
@RestController
@RequestMapping("/api/v1/volunteer-insurance")
public class VolunteerInsuranceController {

    private final VolunteerInsuranceService service;

    public VolunteerInsuranceController(VolunteerInsuranceService service) {
        this.service = service;
    }

    @GetMapping("/association/{associationId}")
    public List<VolunteerInsurancePolicy> byAssociation(@PathVariable Long associationId) {
        return service.findByAssociation(associationId);
    }

    @PostMapping
    public ResponseEntity<?> save(@RequestBody VolunteerInsurancePolicy policy) {
        try {
            return ResponseEntity.ok(service.save(policy));
        } catch (IllegalArgumentException errore) {
            return ResponseEntity.badRequest().body(Map.of("message", errore.getMessage()));
        }
    }

    /**
     * Se una persona era coperta in un dato giorno.
     *
     * <p>Il giorno è un parametro: dopo un infortunio la domanda riguarda sempre
     * una data passata. Senza {@code giorno} si intende oggi.
     */
    @GetMapping("/coverage")
    public VolunteerInsuranceService.Copertura coverage(
            @RequestParam Long associationId,
            @RequestParam Long volunteerId,
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso =
                    org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate giorno) {
        return service.coperturaDi(associationId, volunteerId, giorno);
    }

    /** I volontari in attività che oggi non risultano coperti da entrambe le garanzie. */
    @GetMapping("/uncovered")
    public List<Volunteer> uncovered(
            @RequestParam Long associationId,
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso =
                    org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate giorno) {
        return service.scoperti(associationId, giorno);
    }

    @GetMapping("/expiring")
    public List<VolunteerInsurancePolicy> expiring(@RequestParam Long associationId,
                                                   @RequestParam(defaultValue = "60") int giorni) {
        return service.inScadenza(associationId, giorni);
    }

    @PostMapping("/{policyId}/covered/{volunteerId}")
    public ResponseEntity<?> addCovered(@PathVariable Long policyId, @PathVariable Long volunteerId) {
        try {
            service.addCovered(policyId, volunteerId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException errore) {
            return ResponseEntity.badRequest().body(Map.of("message", errore.getMessage()));
        }
    }

    @DeleteMapping("/{policyId}/covered/{volunteerId}")
    public ResponseEntity<Void> removeCovered(@PathVariable Long policyId, @PathVariable Long volunteerId) {
        service.removeCovered(policyId, volunteerId);
        return ResponseEntity.noContent().build();
    }
}
