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
import org.springframework.data.jpa.repository.Modifying;
import java.time.LocalDateTime;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {
    List<Transaction> findByDateBetween(LocalDate startDate, LocalDate endDate);

    List<Transaction> findByAssociationIdAndDateBetween(Long associationId, LocalDate startDate, LocalDate endDate);

    @Query("SELECT SUM(t.amount) FROM Transaction t WHERE t.type = :type AND t.date BETWEEN :startDate AND :endDate")
    BigDecimal sumByTypeAndDateRange(@Param("type") TransactionType type, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT COALESCE(SUM(t.amount),0) FROM Transaction t WHERE (:associationId IS NULL OR t.associationId=:associationId) AND t.type=:type AND t.date BETWEEN :startDate AND :endDate")
    BigDecimal sumScoped(@Param("associationId") Long associationId,@Param("type") TransactionType type,@Param("startDate") LocalDate startDate,@Param("endDate") LocalDate endDate);

    /**
     * Somma dei movimenti di un progetto per tipo.
     * È la base del rendiconto per progetto: budget dichiarato contro speso reale.
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t "
            + "WHERE t.projectId = :projectId AND t.associationId = :associationId AND t.type = :type")
    BigDecimal sumByProjectAndAssociationAndType(@Param("projectId") Long projectId, @Param("associationId") Long associationId, @Param("type") TransactionType type);

    List<Transaction> findByProjectId(Long projectId);
    @Query("SELECT t FROM Transaction t WHERE t.associationId = :associationId AND (t.projectId IS NULL OR t.projectId = :projectId)")
    List<Transaction> findAvailableForProject(@Param("associationId") Long associationId, @Param("projectId") Long projectId);

    boolean existsByProjectId(Long projectId);

    @Modifying(flushAutomatically=true,clearAutomatically=true)
    @Query("UPDATE Transaction t SET t.projectId=:projectId,t.updatedAt=:now WHERE t.id=:id AND t.associationId=:associationId AND (t.projectId IS NULL OR t.projectId=:projectId) AND EXISTS (SELECT p.id FROM Project p WHERE p.id=:projectId AND p.associationId=:associationId)")
    int attachIfAvailable(@Param("id") Long id,@Param("associationId") Long associationId,@Param("projectId") Long projectId,@Param("now") LocalDateTime now);

    @Modifying(flushAutomatically=true,clearAutomatically=true)
    @Query("UPDATE Transaction t SET t.updatedAt=t.updatedAt WHERE t.id=:id AND ((:projectId IS NULL AND t.projectId IS NULL) OR t.projectId=:projectId)")
    int lockIfProjectUnchanged(@Param("id") Long id,@Param("projectId") Long projectId);
}
