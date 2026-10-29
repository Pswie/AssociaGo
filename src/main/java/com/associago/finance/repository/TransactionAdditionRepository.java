package com.associago.finance.repository;

import com.associago.finance.TransactionAddition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface TransactionAdditionRepository extends JpaRepository<TransactionAddition, Long> {

    List<TransactionAddition> findByTransactionIdOrderByCreatedAtAsc(Long transactionId);

    /**
     * La somma delle aggiunte di un movimento.
     *
     * <p>{@code COALESCE} perché un movimento senza aggiunte deve dare zero, non
     * {@code null}: è il caso normale, e senza questo ogni ricalcolo su un
     * movimento appena creato solleverebbe.
     */
    @Query("SELECT COALESCE(SUM(a.amount), 0) FROM TransactionAddition a "
            + "WHERE a.transactionId = :transactionId")
    BigDecimal sumByTransactionId(@Param("transactionId") Long transactionId);

    void deleteByTransactionId(Long transactionId);
}
