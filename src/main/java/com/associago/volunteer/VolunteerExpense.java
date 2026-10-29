package com.associago.volunteer;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "volunteer_expenses")
public class VolunteerExpense {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "volunteer_id", nullable = false)
    private Long volunteerId;

    /**
     * L'associazione a cui il rimborso appartiene.
     *
     * <p>Sembra deducibile dal volontario e non lo è: {@code volunteers} punta a
     * {@code members}, e {@code members} non ha né l'associazione né un legame
     * con {@code users}, dove l'appartenenza è registrata. Da un volontario non
     * si arriva al suo ente con nessuna JOIN — per questo il servizio scriveva
     * un {@code 1L} a mano nel movimento contabile.
     *
     * <p>Facoltativo perché le righe già inserite non hanno modo di saperlo, e
     * un valore inventato sarebbe peggio di un valore assente. Chi crea il
     * movimento lo pretende, e lo dice.
     */
    @Column(name = "association_id")
    private Long associationId;

    @Column(name = "shift_id")
    private Long shiftId; // Optional link to a specific shift

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private BigDecimal amount;

@Column(nullable = false)
    private String status; // PENDING, APPROVED, REJECTED, PAID

    @Column(name = "transaction_id")
    private Long transactionId; // Link to the financial transaction when paid

    /**
     * Regime del rimborso ai sensi dell'art. 17 del Codice del Terzo Settore:
     * {@code ANALITICO} (spesa documentata, nessun tetto) oppure
     * {@code FORFETTARIO} (autocertificata, entro i limiti di legge).
     */
    @Column(name = "reimbursement_type", nullable = false)
    private String reimbursementType = "ANALITICO";

    /** Autocertificazione ex art. 46 DPR 445/2000, richiesta dal forfettario. */
    @Column(name = "self_certified", nullable = false)
    private boolean selfCertified = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAssociationId() { return associationId; }
    public void setAssociationId(Long associationId) { this.associationId = associationId; }
    public Long getVolunteerId() { return volunteerId; }
    public void setVolunteerId(Long volunteerId) { this.volunteerId = volunteerId; }
    public Long getShiftId() { return shiftId; }
    public void setShiftId(Long shiftId) { this.shiftId = shiftId; }
    public LocalDate getExpenseDate() { return expenseDate; }
    public void setExpenseDate(LocalDate expenseDate) { this.expenseDate = expenseDate; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getTransactionId() { return transactionId; }
    public void setTransactionId(Long transactionId) { this.transactionId = transactionId; }
    public String getReimbursementType() { return reimbursementType; }
    public void setReimbursementType(String reimbursementType) { this.reimbursementType = reimbursementType; }
    public boolean isSelfCertified() { return selfCertified; }
    public void setSelfCertified(boolean selfCertified) { this.selfCertified = selfCertified; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
