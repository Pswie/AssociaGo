package com.associago.member;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Il certificato di idoneità di un socio, ridotto a quel che serve.
 *
 * <p>Resta il tipo e la scadenza: bastano a sapere se una persona può fare un
 * turno, ed è l'unica domanda a cui l'associazione deve rispondere.
 *
 * <p>Sono stati tolti il nome del medico, la struttura sanitaria, una nota
 * libera e il file del certificato. Erano dati sulla salute — art. 9 GDPR —
 * appoggiati accanto a nome, cognome, codice fiscale e indirizzo in
 * {@code members}, raggiungibili con un {@code JOIN} banale. Il file, poi, non
 * esisteva davvero: veniva scritto sul disco locale del contenitore e spariva
 * al riavvio dell'istanza, mentre la colonna continuava a sostenere che ci
 * fosse. Nessun endpoint lo rileggeva.
 *
 * <p>Che il certificato esista resta un dato art. 9 e va nel registro dei
 * trattamenti: la potatura riduce l'esposizione, non toglie l'adempimento.
 */
@Entity
@Table(name = "medical_certificates")
public class MedicalCertificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "association_id", nullable = false)
    private Long associationId;

    @Column(name = "certificate_type", nullable = false)
    private String certificateType = "NON_AGONISTIC"; // AGONISTIC, NON_AGONISTIC

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(nullable = false)
    private String status = "MISSING"; // VALID, EXPIRED, EXPIRING_SOON, MISSING

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getMemberId() { return memberId; }
    public void setMemberId(Long memberId) { this.memberId = memberId; }

    public Long getAssociationId() { return associationId; }
    public void setAssociationId(Long associationId) { this.associationId = associationId; }

    public String getCertificateType() { return certificateType; }
    public void setCertificateType(String certificateType) { this.certificateType = certificateType; }

    public LocalDate getIssueDate() { return issueDate; }
    public void setIssueDate(LocalDate issueDate) { this.issueDate = issueDate; }

    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
