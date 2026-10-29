package com.associago.coupon;

import com.associago.coupon.repository.CouponRepository;
import com.associago.finance.Transaction;
import com.associago.finance.TransactionType;
import com.associago.finance.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * I buoni dati alle persone.
 *
 * <p>La verifica di validità di uno sconto e il calcolo dell'importo scontato
 * sono stati tolti insieme alle colonne che li reggevano: qui un coupon è un
 * buono, cioè un'uscita, non una riduzione di un'entrata. Quella macchina non
 * era mai stata collegata a nulla — nessun servizio chiamava
 * {@code calculateDiscount} — e tenerla avrebbe significato conservare due
 * significati opposti sotto lo stesso nome.
 */
@Service
public class CouponService {

    private final CouponRepository couponRepository;
    private final TransactionRepository transactionRepository;

    @Autowired
    public CouponService(CouponRepository couponRepository,
                         TransactionRepository transactionRepository) {
        this.couponRepository = couponRepository;
        this.transactionRepository = transactionRepository;
    }

    public List<Coupon> findByAssociationId(Long associationId) {
        return couponRepository.findByAssociationId(associationId);
    }

    /** I buoni di un esercizio: vedi la nota sul repository per la regola. */
    public List<Coupon> findByAssociationIdAndAnno(Long associationId, int anno) {
        return couponRepository.findByAssociationIdAndAnno(associationId, anno);
    }

    public Optional<Coupon> findById(Long id) {
        return couponRepository.findById(id);
    }

    public Optional<Coupon> findByCode(Long associationId, String code) {
        return couponRepository.findByAssociationIdAndCode(associationId, code);
    }

    @Transactional
    public Coupon save(Coupon coupon) {
        return couponRepository.save(coupon);
    }

    @Transactional
    public void deleteById(Long id) {
        couponRepository.deleteById(id);
    }

    /**
     * Emette un buono e registra l'uscita corrispondente.
     *
     * <p>Un buono da 10 euro dato a qualcuno è denaro uscito: le due scritture
     * nascono insieme, dentro la stessa transazione, perché un buono senza il
     * suo movimento sarebbe denaro sparito dai conti e un movimento senza il suo
     * buono sarebbe un'uscita senza causale.
     *
     * <p>Se poi arrivano IVA, commissioni o spese, non si tocca questo importo:
     * si aggiungono righe sul movimento e il totale si ricalcola da sé — vedi
     * {@code TransactionAdditionService}.
     *
     * @param userId chi sta emettendo, per la tracciabilità del movimento.
     */
    @Transactional
    public Coupon issue(Coupon coupon, Long userId) {
        BigDecimal valore = coupon.getNominalValue();
        if (valore == null || valore.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Il valore del buono deve essere maggiore di zero.");
        }
        if (coupon.getBeneficiaryMemberId() == null
                && (coupon.getBeneficiaryRef() == null || coupon.getBeneficiaryRef().isBlank())) {
            throw new IllegalArgumentException(
                    "Indicare a chi va il buono: un socio, oppure il codice della persona in anagrafica.");
        }
        if (coupon.getIssuedDate() == null) coupon.setIssuedDate(LocalDate.now());

        Transaction uscita = new Transaction();
        uscita.setAssociationId(coupon.getAssociationId());
        uscita.setDate(coupon.getIssuedDate());
        uscita.setType(TransactionType.EXPENSE);
        uscita.setAmount(valore);
        // La base è il nominale: le aggiunte partiranno da qui.
        uscita.setBaseAmount(valore);
        uscita.setCategory("Buoni");
        uscita.setDescription("Buono " + coupon.getCode()
                + (coupon.getDescription() == null ? "" : " — " + coupon.getDescription()));
        uscita.setReferenceId(coupon.getCode());
        uscita.setCreatedBy(userId);
        Transaction registrata = transactionRepository.save(uscita);

        coupon.setTransactionId(registrata.getId());
        coupon.setStatus(Coupon.EMESSO);
        return couponRepository.save(coupon);
    }
}
