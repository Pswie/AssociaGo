package com.associago.attachment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    List<Attachment> findByOwnerTypeAndOwnerId(AttachmentOwner ownerType, Long ownerId);

    Optional<Attachment> findByOwnerTypeAndOwnerIdAndKind(
            AttachmentOwner ownerType, Long ownerId, String kind);

    /**
     * Se esiste almeno un allegato di quel tipo.
     *
     * <p>Serve alle regole che devono sapere «c'è la ricevuta?» senza tirare su
     * i byte: è la domanda che {@code Art17Rules} pone per ogni rimborso.
     */
    boolean existsByOwnerTypeAndOwnerIdAndKind(
            AttachmentOwner ownerType, Long ownerId, String kind);

    boolean existsByOwnerTypeAndOwnerId(AttachmentOwner ownerType, Long ownerId);

    void deleteByOwnerTypeAndOwnerId(AttachmentOwner ownerType, Long ownerId);
}
