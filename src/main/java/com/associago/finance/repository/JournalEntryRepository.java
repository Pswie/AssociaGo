package com.associago.finance.repository;

import com.associago.finance.JournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {
    List<JournalEntry> findByAssociationIdAndDateBetween(Long associationId, LocalDate startDate, LocalDate endDate);
    List<JournalEntry> findByTransactionId(Long transactionId);
    @Query("SELECT e FROM JournalEntry e WHERE (:associationId IS NULL OR e.associationId=:associationId) AND (:startDate IS NULL OR e.date>=:startDate) AND (:endDate IS NULL OR e.date<=:endDate)")
    List<JournalEntry> findScoped(@Param("associationId") Long associationId,@Param("startDate") LocalDate startDate,@Param("endDate") LocalDate endDate);
}
