package com.associago.volunteer.repository;

import com.associago.volunteer.VolunteerInsurancePolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface VolunteerInsuranceRepository extends JpaRepository<VolunteerInsurancePolicy, Long> {

    List<VolunteerInsurancePolicy> findByAssociationIdOrderByEndDateDesc(Long associationId);

    /** Le polizze in corso in un dato giorno. */
    List<VolunteerInsurancePolicy> findByAssociationIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long associationId, LocalDate day, LocalDate sameDay);

    /** Quelle che scadono entro una data: servono all'avviso, non al controllo. */
    List<VolunteerInsurancePolicy> findByAssociationIdAndEndDateBetween(
            Long associationId, LocalDate from, LocalDate to);
}
