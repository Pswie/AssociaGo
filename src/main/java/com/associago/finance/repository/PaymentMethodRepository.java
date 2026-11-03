package com.associago.finance.repository;

import com.associago.finance.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Repository
public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {
    List<PaymentMethod> findByAssociationId(Long associationId);
    List<PaymentMethod> findByAssociationIdAndIsActiveTrue(Long associationId);
    Optional<PaymentMethod> findByNameAndAssociationId(String name, Long associationId);

    /** Ordinary edits never write provider credentials, including when the browser's copy is stale. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PaymentMethod p SET p.name=:name,p.type=:type,p.isActive=:active,"
            + "p.hasCommission=:commission,p.fixedCommission=:fixed,p.percentageCommission=:percentage,p.updatedAt=:updated "
            + "WHERE p.id=:id AND p.associationId=:association")
    int updateWithoutConfiguration(@Param("id") Long id, @Param("association") Long association,
            @Param("name") String name, @Param("type") String type, @Param("active") boolean active,
            @Param("commission") boolean commission, @Param("fixed") BigDecimal fixed,
            @Param("percentage") BigDecimal percentage, @Param("updated") LocalDateTime updated);
}
