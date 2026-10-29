package com.associago.expense.repository;

import com.associago.expense.ExpenseDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExpenseDocumentRepository extends JpaRepository<ExpenseDocument, Long> {

    List<ExpenseDocument> findByAssociationIdOrderByDocumentDateDesc(Long associationId);
    /**
     * Le fatture di un esercizio.
     *
     * <p>Una fattura è un fatto <strong>puntuale</strong>: appartiene all'anno
     * della sua data e a nessun altro. La scadenza non c'entra — dice quando va
     * pagata, non a quale esercizio si imputa.
     */
    @Query("SELECT d FROM ExpenseDocument d WHERE d.associationId = :associationId "
            + "AND (d.documentDate IS NULL OR YEAR(d.documentDate) = :anno) "
            + "ORDER BY d.documentDate DESC")
    List<ExpenseDocument> findByAssociationIdAndAnno(@Param("associationId") Long associationId,
                                                     @Param("anno") int anno);

    List<ExpenseDocument> findByAssociationIdAndStatus(Long associationId, String status);

    List<ExpenseDocument> findByProjectId(Long projectId);

    /** Guardia contro il doppio import della stessa fattura elettronica. */
    Optional<ExpenseDocument> findBySourceHash(String sourceHash);
}
