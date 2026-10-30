package com.associago.attachment;

import jakarta.persistence.*;
import org.hibernate.Length;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;

/**
 * Un file allegato a qualcosa.
 *
 * <p>Sostituisce ventiquattro colonne {@code *_path} sparse in diciassette
 * tabelle, che puntavano tutte al disco locale del contenitore — su Cloud Run
 * effimero — mentre la colonna continuava a sostenere che il file ci fosse.
 *
 * <p>{@code content} è {@code LAZY}: elencare gli allegati di qualcosa non tira
 * su i byte finché qualcuno non chiede davvero il contenuto. Senza, ogni
 * elenco si trascinerebbe i megabyte.
 */
@Entity
@Table(name = "attachments")
public class Attachment {

    /* Tipi ricorrenti. Non un enum: `kind` dipende dal proprietario, e un
     * elenco chiuso costringerebbe a toccare questo file ogni volta che una
     * tabella vuole allegare qualcosa di nuovo. */
    public static final String DOCUMENTO = "documento";
    public static final String RICEVUTA = "ricevuta";
    public static final String IMMAGINE = "immagine";
    public static final String VERBALE = "verbale";
    public static final String STATUTO = "statuto";
    public static final String REGOLAMENTO = "regolamento";
    public static final String POLIZZA = "polizza";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 48)
    private AttachmentOwner ownerType;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(nullable = false, length = 48)
    private String kind = DOCUMENTO;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "mime_type", length = 128)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Basic(fetch = FetchType.LAZY)
    // Bind bytes directly: PostgreSQL BYTEA is not a JDBC/OID large object.
    // The length selects LONGBLOB on MySQL while SQLite still stores binary data.
    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(nullable = false, length = Length.LONG32)
    private byte[] content;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "created_by")
    private Long createdBy;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public AttachmentOwner getOwnerType() { return ownerType; }
    public void setOwnerType(AttachmentOwner ownerType) { this.ownerType = ownerType; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public byte[] getContent() { return content; }
    public void setContent(byte[] content) { this.content = content; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
}
