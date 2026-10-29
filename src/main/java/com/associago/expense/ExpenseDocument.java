package com.associago.expense;

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
 * Una fattura, ricevuta o nota che l'associazione <strong>riceve</strong>.
 *
 * <p>Da non confondere con {@code Invoice} del baseline, che modella le fatture
 * <em>attive</em>: numero univoco assegnato da noi e destinatario interno.
 * Qui il numero lo assegna il fornitore, quindi non è univoco nel nostro
 * archivio, e due fornitori diversi possono benissimo avere entrambi una
 * «fattura 1».
 *
 * @since 0.2.0
 */
@Entity
@Table(name = "expense_documents")
public class ExpenseDocument {

    public static final String DA_PAGARE = "da_pagare";
    public static final String PAGATA = "pagata";
    public static final String REGISTRATA = "registrata";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "association_id", nullable = false)
    private Long associationId;

    @Column(name = "document_type", nullable = false)
    private String documentType = "fattura";

    private String number;

    @Column(name = "supplier_name", nullable = false)
    private String supplierName;

    @Column(name = "supplier_vat")
    private String supplierVat;

    @Column(name = "supplier_tax_code")
    private String supplierTaxCode;

    @Column(name = "document_date", nullable = false)
    private LocalDate documentDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "vat_amount")
    private BigDecimal vatAmount;

    @Column(nullable = false)
    private String status = DA_PAGARE;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "transaction_id")
    private Long transactionId;

/** SHA-256 del contenuto XML, per non importare due volte la stessa fattura. */
    @Column(name = "source_hash", length = 64)
    private String sourceHash;

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

    /** Scaduta e non ancora pagata. */
    public boolean isOverdue(LocalDate today) {
        return dueDate != null && DA_PAGARE.equals(status) && dueDate.isBefore(today);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAssociationId() { return associationId; }
    public void setAssociationId(Long associationId) { this.associationId = associationId; }
    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }
    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }
    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }
    public String getSupplierVat() { return supplierVat; }
    public void setSupplierVat(String supplierVat) { this.supplierVat = supplierVat; }
    public String getSupplierTaxCode() { return supplierTaxCode; }
    public void setSupplierTaxCode(String supplierTaxCode) { this.supplierTaxCode = supplierTaxCode; }
    public LocalDate getDocumentDate() { return documentDate; }
    public void setDocumentDate(LocalDate documentDate) { this.documentDate = documentDate; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public BigDecimal getVatAmount() { return vatAmount; }
    public void setVatAmount(BigDecimal vatAmount) { this.vatAmount = vatAmount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getTransactionId() { return transactionId; }
    public void setTransactionId(Long transactionId) { this.transactionId = transactionId; }
    public String getSourceHash() { return sourceHash; }
    public void setSourceHash(String sourceHash) { this.sourceHash = sourceHash; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
