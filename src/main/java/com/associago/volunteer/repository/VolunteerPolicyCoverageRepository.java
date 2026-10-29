package com.associago.volunteer.repository;

import com.associago.volunteer.VolunteerPolicyCoverage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VolunteerPolicyCoverageRepository extends JpaRepository<VolunteerPolicyCoverage, Long> {

    List<VolunteerPolicyCoverage> findByPolicyId(Long policyId);

    boolean existsByPolicyIdAndVolunteerId(Long policyId, Long volunteerId);

    /**
     * Quante persone sono nominate in una polizza.
     *
     * <p>Zero significa «cumulativa»: è l'assenza di righe a dire che la polizza
     * copre tutti, non un campo a parte che qualcuno potrebbe dimenticare di
     * tenere allineato.
     */
    long countByPolicyId(Long policyId);

    void deleteByPolicyIdAndVolunteerId(Long policyId, Long volunteerId);
}
