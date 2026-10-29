package com.associago.coupon.repository;

import com.associago.coupon.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, Long> {
    List<Coupon> findByAssociationId(Long associationId);
    Optional<Coupon> findByAssociationIdAndCode(Long associationId, String code);

    /**
     * I buoni di un esercizio.
     *
     * <p>Un buono consegnato è un fatto <strong>puntuale</strong>: appartiene
     * all'anno in cui è uscito il denaro e a nessun altro. Il periodo di
     * spendibilità ({@code startDate}, {@code endDate}) non c'entra — quello
     * dice fino a quando si può usare, non a quale esercizio si imputa.
     *
     * <p>Una data di consegna assente lo fa comparire comunque: è meglio una
     * riga da sistemare che una riga invisibile in ogni anno.
     */
    @Query("SELECT c FROM Coupon c WHERE c.associationId = :associationId "
            + "AND (c.issuedDate IS NULL OR YEAR(c.issuedDate) = :anno)")
    List<Coupon> findByAssociationIdAndAnno(@Param("associationId") Long associationId,
                                            @Param("anno") int anno);
}
