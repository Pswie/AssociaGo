package com.associago.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Un progetto dell'associazione: l'unità a cui appendere movimenti, ore di
 * volontariato e scadenze.
 *
 * <p>Colma un vuoto dello schema originale, dove {@code transactions.project_id}
 * esisteva senza una tabella a cui puntare.
 *
 * @since 0.2.0
 */
@Entity
@Table(name = "projects")
public class Project {

    /** Stati ammessi, gli stessi della dashboard Python da cui si migra. */
    public static final String PROPOSTO = "proposto";
    public static final String ATTIVO = "attivo";
    public static final String DA_COMPLETARE = "da_completare";
    public static final String COMPLETATO = "completato";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "association_id", nullable = false)
    private Long associationId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String status = PROPOSTO;

    private String area;

    private String referent;

    @Column(name = "funding_source")
    private String fundingSource;

    /** URL dell'avviso nell'osservatorio bandi, se il progetto nasce da lì. */
    @Column(name = "opportunity_url", length = 1024)
    private String opportunityUrl;

    private BigDecimal budget;

    private LocalDate deadline;

    @Column(columnDefinition = "TEXT")
    private String needs;

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

    /** Un progetto in ritardo: scaduto ma non ancora chiuso. */
    public boolean isOverdue(LocalDate today) {
        if (deadline == null || COMPLETATO.equals(status) || PROPOSTO.equals(status)) {
            return false;
        }
        return deadline.isBefore(today);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAssociationId() { return associationId; }
    public void setAssociationId(Long associationId) { this.associationId = associationId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getArea() { return area; }
    public void setArea(String area) { this.area = area; }
    public String getReferent() { return referent; }
    public void setReferent(String referent) { this.referent = referent; }
    public String getFundingSource() { return fundingSource; }
    public void setFundingSource(String fundingSource) { this.fundingSource = fundingSource; }
    public String getOpportunityUrl() { return opportunityUrl; }
    public void setOpportunityUrl(String opportunityUrl) { this.opportunityUrl = opportunityUrl; }
    public BigDecimal getBudget() { return budget; }
    public void setBudget(BigDecimal budget) { this.budget = budget; }
    public LocalDate getDeadline() { return deadline; }
    public void setDeadline(LocalDate deadline) { this.deadline = deadline; }
    public String getNeeds() { return needs; }
    public void setNeeds(String needs) { this.needs = needs; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
