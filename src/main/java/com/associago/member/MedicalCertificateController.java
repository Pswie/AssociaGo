package com.associago.member;

import com.associago.member.dto.MedicalCertificateDTO;
import com.associago.member.dto.MedicalCertificateUpsertDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/medical-certificates")
public class MedicalCertificateController {

    private final MedicalCertificateService service;

    public MedicalCertificateController(MedicalCertificateService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<MedicalCertificateDTO> getByMember(
            @RequestParam Long memberId,
            @RequestParam Long associationId) {
        return service.getByMember(memberId, associationId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/by-association")
    public List<MedicalCertificateDTO> getByAssociation(@RequestParam Long associationId) {
        return service.getAllByAssociation(associationId);
    }

    @GetMapping("/expiring")
    public List<MedicalCertificateDTO> getExpiring(
            @RequestParam Long associationId,
            @RequestParam(defaultValue = "30") int days) {
        return service.getExpiring(associationId, days);
    }

    @GetMapping("/expired")
    public List<MedicalCertificateDTO> getExpired(@RequestParam Long associationId) {
        return service.getExpired(associationId);
    }

    @PostMapping
    public MedicalCertificateDTO save(@RequestBody MedicalCertificateUpsertDTO dto) {
        return service.save(dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
