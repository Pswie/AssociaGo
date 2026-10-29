package com.associago.volunteer.repository;

import com.associago.volunteer.Volunteer;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface VolunteerRepository extends CrudRepository<Volunteer, Long> {

    /**
     * Scheda volontario a partire dall'anagrafica.
     * Serve all'area personale per rispondere «quali sono i miei turni»
     * senza scorrere l'intera tabella.
     */
    Optional<Volunteer> findByMemberId(Long memberId);

    /**
     * Chi risultava volontario in un dato giorno — art. 17 c.1.
     *
     * <p>Il filtro è in SQL e non in memoria: la verifica di copertura gira su
     * ogni volontario, e scorrere l'intera tabella per poi scartarne la metà
     * significherebbe leggere tutto il registro a ogni controllo.
     *
     * <p>Una data di inizio assente conta come «da sempre»: sono le righe
     * scritte prima che il registro avesse le date, ed escluderle direbbe che
     * quelle persone non erano volontarie.
     */
    @Query("SELECT v FROM Volunteer v "
            + "WHERE (v.startedAt IS NULL OR v.startedAt <= :giorno) "
            + "  AND (v.endedAt IS NULL OR v.endedAt >= :giorno)")
    List<Volunteer> attiviIl(@Param("giorno") LocalDate giorno);

    /**
     * Chi era volontario in un qualunque giorno di un esercizio.
     *
     * <p>Diversa da {@link #attiviIl}, che risponde su un giorno solo: qui basta
     * che il periodo si sovrapponga all'anno. Chi ha cessato a marzo compare nel
     * suo anno e non in quelli dopo; chi non ha cessato compare sempre.
     */
    @Query("SELECT v FROM Volunteer v "
            + "WHERE (v.startedAt IS NULL OR v.startedAt <= :fine) "
            + "  AND (v.endedAt IS NULL OR v.endedAt >= :inizio)")
    List<Volunteer> nellEsercizio(@Param("inizio") LocalDate inizio,
                                  @Param("fine") LocalDate fine);
}
