package com.associago.activity.repository;

import com.associago.activity.Activity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActivityRepository extends JpaRepository<Activity, Long> {
    List<Activity> findByAssociationId(Long associationId);
    /**
     * Le attività in essere in un esercizio.
     *
     * <p>Un'attività ha un <strong>periodo</strong>: quella cominciata a ottobre
     * e finita a marzo appartiene a due esercizi, e un corso senza data di fine
     * è ancora in corso — quindi compare anche negli anni successivi.
     */
    @Query("SELECT a FROM Activity a WHERE a.associationId = :associationId "
            + "AND (a.startDate IS NULL OR a.startDate <= :fine) "
            + "AND (a.endDate IS NULL OR a.endDate >= :inizio)")
    List<Activity> findNellEsercizio(@Param("associationId") Long associationId,
                                     @Param("inizio") java.time.LocalDate inizio,
                                     @Param("fine") java.time.LocalDate fine);
    List<Activity> findByAssociationIdAndIsActiveTrue(Long associationId);
}
