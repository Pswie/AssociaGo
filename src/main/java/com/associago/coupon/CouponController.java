package com.associago.coupon;

import com.associago.esercizio.Esercizio;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/coupons")
public class CouponController {

    private final CouponService couponService;

    @Autowired
    public CouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    /**
     * @param anno l'esercizio da guardare. Assente significa «tutti», perché un
     *             elenco senza filtro deve restare possibile — serve alle
     *             verifiche e a chi cerca qualcosa di cui non ricorda l'anno.
     */
    @GetMapping("/association/{associationId}")
    public List<Coupon> getCouponsByAssociation(@PathVariable Long associationId,
                                                @RequestParam(required = false) Integer anno) {
        return Esercizio.valido(anno)
                ? couponService.findByAssociationIdAndAnno(associationId, anno)
                : couponService.findByAssociationId(associationId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Coupon> getCouponById(@PathVariable Long id) {
        return couponService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Coupon createCoupon(@RequestBody Coupon coupon) {
        return couponService.save(coupon);
    }

    /**
     * Emette un buono e registra l'uscita corrispondente.
     *
     * <p>Diverso da {@code POST /coupons}, che scrive soltanto la riga: qui
     * nasce anche il movimento contabile, perché un buono consegnato è denaro
     * uscito.
     */
    @PostMapping("/issue")
    public ResponseEntity<Coupon> issueCoupon(@RequestBody Coupon coupon,
                                              @RequestParam(required = false) Long userId) {
        try {
            return ResponseEntity.ok(couponService.issue(coupon, userId));
        } catch (IllegalArgumentException errore) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Coupon> updateCoupon(@PathVariable Long id, @RequestBody Coupon details) {
        return couponService.findById(id)
                .map(existing -> {
                    existing.setCode(details.getCode());
                    existing.setDescription(details.getDescription());
                    existing.setNominalValue(details.getNominalValue());
                    existing.setBeneficiaryMemberId(details.getBeneficiaryMemberId());
                    existing.setBeneficiaryRef(details.getBeneficiaryRef());
                    existing.setStartDate(details.getStartDate());
                    existing.setEndDate(details.getEndDate());
                    existing.setActive(details.isActive());
                    existing.setStatus(details.getStatus());
                    // `transactionId` non si tocca da qui: il legame col
                    // movimento in uscita lo crea l'emissione, e riscriverlo da
                    // una modifica scollegherebbe il buono dai suoi soldi.
                    return ResponseEntity.ok(couponService.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCoupon(@PathVariable Long id) {
        if (couponService.findById(id).isPresent()) {
            couponService.deleteById(id);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * Se un buono è spendibile oggi.
     *
     * <p>Ha preso il posto della vecchia {@code /validate}, che calcolava lo
     * sconto da applicare a una quota: qui non c'è nessuno sconto da calcolare,
     * il buono vale il suo nominale. Resta la sola domanda che ha senso —
     * questo codice è valido? — con il motivo quando non lo è, perché «non
     * valido» senza spiegazione manda la persona allo sportello.
     */
    @PostMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateCoupon(@RequestBody Map<String, Object> payload) {
        Long associationId = ((Number) payload.get("associationId")).longValue();
        String code = (String) payload.get("code");

        return couponService.findByCode(associationId, code)
                .map(coupon -> {
                    if (!coupon.isSpendibileIl(LocalDate.now())) {
                        return ResponseEntity.ok(Map.<String, Object>of(
                                "valid", false,
                                "reason", Coupon.ANNULLATO.equals(coupon.getStatus())
                                        ? "Buono annullato"
                                        : "Buono scaduto o non ancora valido"));
                    }
                    return ResponseEntity.ok(Map.<String, Object>of(
                            "valid", true,
                            "value", coupon.getNominalValue() == null
                                    ? BigDecimal.ZERO : coupon.getNominalValue(),
                            "coupon", coupon));
                })
                .orElse(ResponseEntity.ok(Map.of("valid", false, "reason", "Buono non trovato")));
    }
}
