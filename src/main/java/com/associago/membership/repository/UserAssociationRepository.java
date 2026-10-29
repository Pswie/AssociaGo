package com.associago.membership.repository;

import com.associago.membership.UserAssociation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserAssociationRepository extends JpaRepository<UserAssociation, Long> {
    List<UserAssociation> findByAssociationId(Long associationId);
    /**
     * Le tessere in essere in un esercizio.
     *
     * <p>Una tessera ha un <strong>periodo</strong>. Quella senza scadenza è
     * aperta: compare nell'anno guardato e in tutti i successivi. Filtrare per
     * data di iscrizione farebbe sparire dal 2027 quasi tutto il libro soci.
     */
    @Query("SELECT ua FROM UserAssociation ua WHERE ua.association.id = :associationId "
            + "AND (ua.joinDate IS NULL OR ua.joinDate <= :fine) "
            + "AND (ua.expirationDate IS NULL OR ua.expirationDate >= :inizio)")
    List<UserAssociation> findNellEsercizio(@Param("associationId") Long associationId,
                                            @Param("inizio") java.time.LocalDate inizio,
                                            @Param("fine") java.time.LocalDate fine);
    List<UserAssociation> findByUserId(Long userId);
    Optional<UserAssociation> findByUserIdAndAssociationId(Long userId, Long associationId);
}
