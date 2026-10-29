package com.associago.finance.repository;

import com.associago.finance.Transaction;
import com.associago.finance.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {
    List<Transaction> findByDateBetween(LocalDate startDate, LocalDate endDate);

    List<Transaction> findByAssociationIdAndDateBetween(Long associationId, LocalDate startDate, LocalDate endDate);

    @Query("SELECT SUM(t.amount) FROM Transaction t WHERE t.type = :type AND t.date BETWEEN :startDate AND :endDate")
    BigDecimal sumByTypeAndDateRange(@Param("type") TransactionType type, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /**
     * Somma dei movimenti di un progetto per tipo.
     * È la base del rendiconto per progetto: budget dichiarato contro speso reale.
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t "
            + "WHERE t.projectId = :projectId AND t.type = :type")
    BigDecimal sumByProjectAndType(@Param("projectId") Long projectId, @Param("type") TransactionType type);

    List<Transaction> findByProjectId(Long projectId);
}
