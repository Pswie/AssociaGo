package com.associago.volunteer;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * La polizza che copre i volontari — art. 18 D.Lgs. 117/2017.
 *
 * <p>Obbligatoria e non derogabile: l'ente deve assicurare i volontari contro
 * infortuni e malattie connessi all'attività, e per la responsabilità civile
 * verso terzi. Un volontario che opera scoperto è una responsabilità dell'ente,
 * non sua.
 *
 * <p><strong>Cumulativa o nominativa.</strong> Una polizza senza righe in
 * {@code volunteer_policy_coverage} copre tutti i volontari dell'associazione;
 * una con righe copre soltanto quelli elencati. La distinzione è quella che si
 * usa davvero, ed è lì che si annida l'errore più comune: dare per coperto chi
 * è arrivato dopo la firma di una polizza nominativa.
 */
@Entity
@Table(name = "volunteer_insurance_policies")
public class VolunteerInsurancePolicy {

    public static final String INFORTUNI = "infortuni";
    public static final String RC_TERZI = "rc_terzi";
    public static final String INFORTUNI_RC = "infortuni_rc";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "association_id", nullable = false)
    private Long associationId;

    @Column(name = "policy_number", nullable = false, length = 64)
    private String policyNumber;

    @Column(nullable = false)
    private String company;

    /**
     * infortuni | rc_terzi | infortuni_rc
     *
     * <p>Distinti perché l'art. 18 chiede due coperture diverse, e spesso sono
     * due contratti con due scadenze diverse. Una sola delle due non basta, e
     * un modello che non sappia distinguerle lascia credere di essere a posto.
     */
    @Column(name = "coverage_type", nullable = false, length = 32)
    private String coverageType = INFORTUNI_RC;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** Vera se la polizza è in corso nel giorno indicato. */
    public boolean coversDate(LocalDate day) {
        return day != null
                && !day.isBefore(startDate)
                && !day.isAfter(endDate);
    }

    /** Quanti giorni mancano alla scadenza: negativo se è già scaduta. */
    public long daysToExpiry(LocalDate today) {
        return java.time.temporal.ChronoUnit.DAYS.between(today, endDate);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAssociationId() { return associationId; }
    public void setAssociationId(Long associationId) { this.associationId = associationId; }
    public String getPolicyNumber() { return policyNumber; }
    public void setPolicyNumber(String policyNumber) { this.policyNumber = policyNumber; }
    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }
    public String getCoverageType() { return coverageType; }
    public void setCoverageType(String coverageType) { this.coverageType = coverageType; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
