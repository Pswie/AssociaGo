package com.associago.coupon;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Un buono dato a una persona.
 *
 * <p>Un buono da 10 euro consegnato a qualcuno è denaro che <strong>esce</strong>
 * dall'associazione: all'emissione nasce un movimento in uscita da 10, e se poi
 * arrivano IVA, commissioni o spese si aggiungono su quel movimento — vedi
 * {@code TransactionAddition} — senza riscrivere l'importo originale.
 *
 * <p><strong>Non è uno sconto.</strong> Le colonne dello sconto sulle quote
 * ({@code discount_type}, {@code discount_value}, {@code min_amount}, i
 * contatori d'uso e le tabelle {@code coupon_activities} /
 * {@code coupon_events}) sono state tolte in V11: uno sconto è denaro che non
 * entra, un buono è denaro che esce, e sommarli darebbe un rendiconto falso in
 * entrambe le colonne. Quella macchina non era comunque mai stata collegata —
 * né {@code ActivityService} né {@code EventService} la chiamavano.
 */
@Entity
@Table(name = "coupons")
public class Coupon {

    public static final String EMESSO = "emesso";
    public static final String CONSEGNATO = "consegnato";
    public static final String ANNULLATO = "annullato";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "association_id", nullable = false)
    private Long associationId;

    @Column(nullable = false)
    private String code;

    private String description;

    /** Quanto vale il buono per chi lo riceve, e quanto esce dal conto. */
    @Column(name = "nominal_value")
    private BigDecimal nominalValue;

    /**
     * A chi è andato, quando è un socio o un volontario.
     *
     * <p>Per una persona assistita resta {@code null} e si usa
     * {@link #beneficiaryRef}: vedi la nota lì.
     */
    @Column(name = "beneficiary_member_id")
    private Long beneficiaryMemberId;

    /**
     * A chi è andato, quando non è qualcuno di {@code members}.
     *
     * <p>Qui va il <strong>codice</strong> della persona in anagrafica assistiti
     * — {@code ASS-0042} — non il suo nome. L'anagrafica degli assistiti è un
     * altro database, su un altro sistema, con i nomi dentro buste cifrate:
     * scrivere qui «Francesco Esposito» farebbe del gestionale un secondo posto
     * dove i nomi delle persone fragili stanno in chiaro, che è esattamente ciò
     * che quella cifratura serve a evitare.
     */
    @Column(name = "beneficiary_ref", length = 64)
    private String beneficiaryRef;

    @Column(name = "issued_date")
    private LocalDate issuedDate;

    /** Il movimento in uscita nato dall'emissione. */
    @Column(name = "transaction_id")
    private Long transactionId;

    /** emesso | consegnato | annullato */
    @Column(nullable = false)
    private String status = EMESSO;

    /** Da quando è spendibile. Nullo se non ha una finestra di validità. */
    @Column(name = "start_date")
    private LocalDate startDate;

    /** Fino a quando è spendibile. */
    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "is_active")
    private boolean isActive = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** Vero se il buono è spendibile nel giorno indicato. */
    public boolean isSpendibileIl(LocalDate giorno) {
        if (!isActive || giorno == null || ANNULLATO.equals(status)) return false;
        if (startDate != null && giorno.isBefore(startDate)) return false;
        return endDate == null || !giorno.isAfter(endDate);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAssociationId() { return associationId; }
    public void setAssociationId(Long associationId) { this.associationId = associationId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getNominalValue() { return nominalValue; }
    public void setNominalValue(BigDecimal nominalValue) { this.nominalValue = nominalValue; }
    public Long getBeneficiaryMemberId() { return beneficiaryMemberId; }
    public void setBeneficiaryMemberId(Long beneficiaryMemberId) { this.beneficiaryMemberId = beneficiaryMemberId; }
    public String getBeneficiaryRef() { return beneficiaryRef; }
    public void setBeneficiaryRef(String beneficiaryRef) { this.beneficiaryRef = beneficiaryRef; }
    public LocalDate getIssuedDate() { return issuedDate; }
    public void setIssuedDate(LocalDate issuedDate) { this.issuedDate = issuedDate; }
    public Long getTransactionId() { return transactionId; }
    public void setTransactionId(Long transactionId) { this.transactionId = transactionId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
