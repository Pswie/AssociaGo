package com.associago.event.repository;

import com.associago.event.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
    /**
     * Gli eventi di un esercizio.
     *
     * <p>Un evento ha un <strong>periodo</strong>, non una data: quello che
     * comincia il 30 dicembre e finisce il 2 gennaio appartiene a due esercizi,
     * e va mostrato in entrambi.
     *
     * <p>Un evento senza fine è ancora aperto e vale anche per gli anni
     * successivi; uno senza inizio vale da sempre. Le assenze contano come
     * «sempre» perché una data mancante non deve far sparire una riga da tutti
     * gli anni, dove nessuno la troverebbe più.
     */
    @Query("SELECT e FROM Event e WHERE "
            + "(e.startDatetime IS NULL OR e.startDatetime <= :fine) "
            + "AND (e.endDatetime IS NULL OR e.endDatetime >= :inizio)")
    List<Event> findNellEsercizio(@Param("inizio") LocalDateTime inizio,
                                  @Param("fine") LocalDateTime fine);

    long countByStartDatetimeAfter(LocalDateTime now);
    Event findFirstByStartDatetimeAfterOrderByStartDatetimeAsc(LocalDateTime now);
}
