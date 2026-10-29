package com.associago.volunteer;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Una persona nominata in una polizza.
 *
 * <p>Esiste solo per le polizze nominative. L'assenza di righe per una polizza
 * <em>è</em> l'informazione che quella polizza è cumulativa: vedi
 * {@link VolunteerInsurancePolicy}.
 */
@Entity
@Table(name = "volunteer_policy_coverage")
public class VolunteerPolicyCoverage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_id", nullable = false)
    private Long policyId;

    @Column(name = "volunteer_id", nullable = false)
    private Long volunteerId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPolicyId() { return policyId; }
    public void setPolicyId(Long policyId) { this.policyId = policyId; }
    public Long getVolunteerId() { return volunteerId; }
    public void setVolunteerId(Long volunteerId) { this.volunteerId = volunteerId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
