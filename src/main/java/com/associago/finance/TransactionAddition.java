package com.associago.finance;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Una voce aggiunta a un movimento già registrato: IVA, commissione, spese.
 *
 * <p>Un movimento si registra quando il denaro si muove, ma la cifra definitiva
 * arriva dopo — con la fattura, con la commissione del fornitore, con l'IVA.
 * Finora l'unico modo di sistemarla era riscrivere l'importo, e un importo
 * riscritto fa sparire la storia: a fine anno non si riconcilia più con quello
 * che si è dichiarato.
 *
 * <p>Qui l'aggiunta è una riga sua e il totale del movimento si ricalcola:
 *
 * <pre>{@code
 *   Buono a Francesco        10,00   ← base_amount
 *   + IVA                     0,50   ← una riga qui
 *   ─────────────────────────────
 *   Uscita                   10,50   ← amount, ricalcolato
 * }</pre>
 *
 * <p>L'importo è <strong>con segno</strong>. Una stima troppo alta si corregge
 * aggiungendo un valore negativo, non riscrivendo l'originale: il totale
 * scende, e resta scritto di quanto e perché.
 */
@Entity
@Table(name = "transaction_additions")
public class TransactionAddition {

    public static final String IVA = "iva";
    public static final String COMMISSIONE = "commissione";
    public static final String SPESE = "spese";
    public static final String ALTRO = "altro";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false)
    private Long transactionId;

    /**
     * Che cosa è questa aggiunta.
     *
     * <p>Aperto e non un enum chiuso perché la differenza cambia caso per caso:
     * sullo stesso buono può essere la commissione di chi lo emette, l'IVA, o le
     * spese di consegna. Raggrupparle per tipo è ciò che permette di rispondere
     * a «quanto abbiamo pagato di commissioni quest'anno».
     */
    @Column(nullable = false)
    private String kind = ALTRO;

    @Column(nullable = false)
    private BigDecimal amount;

    private String description;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "created_by")
    private Long createdBy;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTransactionId() { return transactionId; }
    public void setTransactionId(Long transactionId) { this.transactionId = transactionId; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
}
