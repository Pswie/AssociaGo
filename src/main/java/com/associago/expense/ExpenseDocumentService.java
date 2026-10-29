package com.associago.expense;

import com.associago.expense.repository.ExpenseDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Documenti di spesa e import delle fatture elettroniche.
 *
 * @since 0.2.0
 */
@Service
public class ExpenseDocumentService {

    private static final Set<String> VALID_STATUSES =
            Set.of(ExpenseDocument.DA_PAGARE, ExpenseDocument.PAGATA, ExpenseDocument.REGISTRATA);

    private final ExpenseDocumentRepository repository;
    private final com.associago.attachment.AttachmentService attachments;

    public ExpenseDocumentService(ExpenseDocumentRepository repository,
                                  com.associago.attachment.AttachmentService attachments) {
        this.repository = repository;
        this.attachments = attachments;
    }

    public List<ExpenseDocument> findByAssociation(Long associationId) {
        return repository.findByAssociationIdOrderByDocumentDateDesc(associationId);
    }

    /**
     * Le fatture di un esercizio, o tutte se l'anno non è indicato.
     *
     * <p>Una fattura è un fatto puntuale: appartiene all'anno della sua data. La
     * scadenza non c'entra — dice quando va pagata, non a quale esercizio si
     * imputa.
     */
    public List<ExpenseDocument> findByAssociation(Long associationId, Integer anno) {
        return com.associago.esercizio.Esercizio.valido(anno)
                ? repository.findByAssociationIdAndAnno(associationId, anno)
                : repository.findByAssociationIdOrderByDocumentDateDesc(associationId);
    }

    public Optional<ExpenseDocument> findById(Long id) {
        return repository.findById(id);
    }

    /** Documenti scaduti e non pagati: la lista che serve al tesoriere. */
    public List<ExpenseDocument> overdue(Long associationId, LocalDate today) {
        return repository.findByAssociationIdAndStatus(associationId, ExpenseDocument.DA_PAGARE)
                .stream()
                .filter(document -> document.isOverdue(today))
                .toList();
    }

    /** Esito dell'import: il documento e se era già presente. */
    public record ImportResult(ExpenseDocument document, boolean alreadyPresent) {
    }

    /**
     * Importa una fattura elettronica.
     *
     * <p>Se lo stesso file era già stato caricato, non ne crea un duplicato ma
     * restituisce quello esistente: reimportare la stessa fattura è l'errore
     * più facile da commettere, e un doppione in prima nota è un problema
     * contabile, non un fastidio.
     */
    @Transactional
    public ImportResult importFattura(byte[] xml, Long associationId) {
        ExpenseDocument parsed = FatturaXmlParser.parse(xml, associationId);

        Optional<ExpenseDocument> existing = repository.findBySourceHash(parsed.getSourceHash());
        if (existing.isPresent()) {
            return new ImportResult(existing.get(), true);
        }

        return new ImportResult(repository.save(parsed), false);
    }

    @Transactional
    public ExpenseDocument save(ExpenseDocument document) {
        validate(document);
        return repository.save(document);
    }

    @Transactional
    public Optional<ExpenseDocument> update(Long id, ExpenseDocument details) {
        return repository.findById(id).map(existing -> {
            existing.setDocumentType(details.getDocumentType());
            existing.setNumber(details.getNumber());
            existing.setSupplierName(details.getSupplierName());
            existing.setSupplierVat(details.getSupplierVat());
            existing.setSupplierTaxCode(details.getSupplierTaxCode());
            existing.setDocumentDate(details.getDocumentDate());
            existing.setDueDate(details.getDueDate());
            existing.setTotalAmount(details.getTotalAmount());
            existing.setVatAmount(details.getVatAmount());
            existing.setStatus(details.getStatus());
            existing.setProjectId(details.getProjectId());
            existing.setNotes(details.getNotes());
            validate(existing);
            return repository.save(existing);
        });
    }

    /**
     * Cancella la fattura e il file allegato.
     *
     * <p>L'allegato va tolto esplicitamente: {@code owner_type} +
     * {@code owner_id} non è una chiave esterna — una polimorfica non esiste —
     * quindi il database non pulisce da solo. È il prezzo dichiarato di avere
     * un meccanismo unico invece di ventiquattro colonne, e va pagato qui: un
     * blob orfano non lo trova più nessuno, e resta a occupare spazio con
     * dentro i dati di un fornitore.
     */
    @Transactional
    public boolean delete(Long id) {
        if (!repository.existsById(id)) {
            return false;
        }
        attachments.deleteAllOf(com.associago.attachment.AttachmentOwner.EXPENSE_DOCUMENT, id);
        repository.deleteById(id);
        return true;
    }

    private void validate(ExpenseDocument document) {
        if (document.getAssociationId() == null) {
            throw new IllegalArgumentException("Il documento deve appartenere a un'associazione");
        }
        if (document.getSupplierName() == null || document.getSupplierName().isBlank()) {
            throw new IllegalArgumentException("Il fornitore è obbligatorio");
        }
        if (document.getDocumentDate() == null) {
            throw new IllegalArgumentException("La data del documento è obbligatoria");
        }
        if (document.getTotalAmount() == null || document.getTotalAmount().signum() < 0) {
            throw new IllegalArgumentException("L'importo totale non può essere negativo");
        }
        if (!VALID_STATUSES.contains(document.getStatus())) {
            throw new IllegalArgumentException(
                    "Stato non ammesso: " + document.getStatus() + ". Ammessi: " + VALID_STATUSES);
        }
    }
}
