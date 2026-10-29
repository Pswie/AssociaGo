package com.associago.member;

import com.associago.member.dto.MedicalCertificateDTO;
import com.associago.member.dto.MedicalCertificateUpsertDTO;
import com.associago.member.repository.MedicalCertificateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * I certificati di idoneità dei soci.
 *
 * <p>Il caricamento del file non c'è più. Scriveva su
 * {@code storagePath/medical-certificates/}, cioè sul disco locale del
 * contenitore: su Cloud Run quel disco è effimero e al riavvio dell'istanza il
 * PDF non c'era più, mentre {@code file_path} continuava a dire il contrario.
 * Non esisteva nemmeno un endpoint per riscaricarlo. Era una funzione che
 * prometteva una conservazione che non avveniva, per giunta su un documento
 * sanitario: toglierla riduce l'esposizione e non perde nulla che ci fosse
 * davvero.
 */
@Service
@Transactional
public class MedicalCertificateService {

    private final MedicalCertificateRepository repository;

    public MedicalCertificateService(MedicalCertificateRepository repository) {
        this.repository = repository;
    }

    public Optional<MedicalCertificateDTO> getByMember(Long memberId, Long associationId) {
        return repository.findByMemberIdAndAssociationId(memberId, associationId)
                .map(this::toDTO);
    }

    public List<MedicalCertificateDTO> getAllByAssociation(Long associationId) {
        return repository.findByAssociationId(associationId).stream()
                .map(this::toDTO)
                .toList();
    }

    public List<MedicalCertificateDTO> getExpiring(Long associationId, int days) {
        LocalDate now = LocalDate.now();
        LocalDate limit = now.plusDays(days);
        return repository.findByAssociationIdAndExpiryDateBetween(associationId, now, limit).stream()
                .map(this::toDTO)
                .toList();
    }

    public List<MedicalCertificateDTO> getExpired(Long associationId) {
        return repository.findByAssociationIdAndExpiryDateBefore(associationId, LocalDate.now()).stream()
                .map(this::toDTO)
                .toList();
    }

    public MedicalCertificateDTO save(MedicalCertificateUpsertDTO dto) {
        MedicalCertificate cert = repository
                .findByMemberIdAndAssociationId(dto.memberId(), dto.associationId())
                .orElseGet(MedicalCertificate::new);

        cert.setMemberId(dto.memberId());
        cert.setAssociationId(dto.associationId());
        if (dto.certificateType() != null) cert.setCertificateType(dto.certificateType());
        if (dto.issueDate() != null) cert.setIssueDate(dto.issueDate());
        if (dto.expiryDate() != null) cert.setExpiryDate(dto.expiryDate());

        cert.setStatus(computeStatus(cert.getExpiryDate()));

        return toDTO(repository.save(cert));
    }

    public void delete(Long id) {
        repository.findById(id).ifPresent(repository::delete);
    }

    private String computeStatus(LocalDate expiryDate) {
        if (expiryDate == null) return "MISSING";
        LocalDate now = LocalDate.now();
        if (expiryDate.isBefore(now)) return "EXPIRED";
        if (expiryDate.isBefore(now.plusDays(30))) return "EXPIRING_SOON";
        return "VALID";
    }

    private MedicalCertificateDTO toDTO(MedicalCertificate c) {
        return new MedicalCertificateDTO(
                c.getId(), c.getMemberId(), c.getAssociationId(),
                c.getCertificateType(), c.getIssueDate(), c.getExpiryDate(),
                c.getStatus(), c.getCreatedAt(), c.getUpdatedAt()
        );
    }
}
