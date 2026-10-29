package com.associago.association;

import com.associago.association.repository.AssociationDocumentRepository;
import com.associago.attachment.AttachmentOwner;
import com.associago.attachment.AttachmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * I documenti sociali dell'associazione: statuto, regolamenti, verbali, delibere.
 *
 * <p>Prima il file veniva scritto in {@code assetsPath/<id>/documents/} e la
 * riga ne conservava il percorso. Su Cloud Run quel disco è effimero: al riavvio
 * dell'istanza il documento non c'era più, mentre {@code file_path} continuava a
 * dire il contrario. Per documenti che l'ente è tenuto a conservare — lo statuto
 * non è un allegato qualunque — era il posto peggiore possibile.
 *
 * <p>Ora il contenuto sta in {@code attachments}, legato a questa riga da
 * {@link AttachmentOwner#ASSOCIATION_DOCUMENT}: sopravvive ai riavvii, finisce
 * nei backup del database e si cancella insieme alla riga a cui appartiene.
 */
@Service
public class AssociationDocumentService {

    private final AssociationDocumentRepository documentRepository;
    private final AttachmentService attachments;

    public AssociationDocumentService(AssociationDocumentRepository documentRepository,
                                      AttachmentService attachments) {
        this.documentRepository = documentRepository;
        this.attachments = attachments;
    }

    public List<AssociationDocument> findByAssociationId(Long associationId) {
        return documentRepository.findByAssociationId(associationId);
    }

    public AssociationDocument findById(Long id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + id));
    }

    /**
     * Registra un documento e ne conserva il contenuto.
     *
     * <p>La riga si scrive per prima perché l'allegato ha bisogno del suo
     * identificativo. Il metodo è transazionale: se il salvataggio del file
     * fallisce — formato non ammesso, dimensione oltre il limite — la riga non
     * resta a indicare un documento che non c'è.
     */
    @Transactional
    public AssociationDocument upload(Long associationId, String documentType, String title,
                                      MultipartFile file, Long uploadedBy) throws IOException {
        AssociationDocument doc = new AssociationDocument();
        doc.setAssociationId(associationId);
        doc.setDocumentType(documentType);
        doc.setTitle(title);
        doc.setFileSize(file.getSize());
        doc.setMimeType(file.getContentType());
        doc.setUploadedBy(uploadedBy);
        AssociationDocument salvato = documentRepository.save(doc);

        attachments.upload(AttachmentOwner.ASSOCIATION_DOCUMENT, salvato.getId(),
                documentType, file, uploadedBy);

        return salvato;
    }

    /**
     * Cancella il documento e il suo contenuto.
     *
     * <p>L'allegato va tolto esplicitamente: {@code owner_type} +
     * {@code owner_id} non è una chiave esterna, quindi il database non pulisce
     * da solo. È il prezzo dichiarato del meccanismo unico degli allegati.
     */
    @Transactional
    public void delete(Long id) {
        AssociationDocument doc = findById(id);
        attachments.deleteAllOf(AttachmentOwner.ASSOCIATION_DOCUMENT, doc.getId());
        documentRepository.deleteById(id);
    }
}
