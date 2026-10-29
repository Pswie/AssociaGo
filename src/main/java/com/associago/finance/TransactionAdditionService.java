package com.associago.finance;

import com.associago.finance.repository.TransactionAdditionRepository;
import com.associago.finance.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Le aggiunte su un movimento, e il ricalcolo del totale.
 *
 * <p>L'invariante è una sola e vale sempre:
 *
 * <pre>{@code   amount = baseAmount + somma delle aggiunte   }</pre>
 *
 * <p>Tutto passa da {@link #recompute(Long)}: nessun chiamante calcola il totale
 * per conto suo. È la differenza fra un totale che è sempre giusto e uno che è
 * giusto finché qualcuno non dimentica di aggiornarlo.
 */
@Service
@Transactional
public class TransactionAdditionService {

    private final TransactionAdditionRepository additions;
    private final TransactionRepository transactions;

    public TransactionAdditionService(TransactionAdditionRepository additions,
                                      TransactionRepository transactions) {
        this.additions = additions;
        this.transactions = transactions;
    }

    public List<TransactionAddition> list(Long transactionId) {
        return additions.findByTransactionIdOrderByCreatedAtAsc(transactionId);
    }

    /**
     * Aggiunge una voce e ricalcola il totale del movimento.
     *
     * @param amount con segno: negativo per correggere in diminuzione una stima
     *               troppo alta, invece di riscrivere l'importo originale.
     */
    public TransactionAddition add(Long transactionId, String kind, BigDecimal amount,
                                   String description, Long userId) {
        Transaction movimento = transactions.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Movimento non trovato: " + transactionId));
        if (amount == null) {
            throw new IllegalArgumentException("L'importo dell'aggiunta è obbligatorio.");
        }

        TransactionAddition aggiunta = new TransactionAddition();
        aggiunta.setTransactionId(movimento.getId());
        aggiunta.setKind(kind == null || kind.isBlank() ? TransactionAddition.ALTRO : kind);
        aggiunta.setAmount(amount);
        aggiunta.setDescription(description);
        aggiunta.setCreatedBy(userId);
        TransactionAddition salvata = additions.save(aggiunta);

        recompute(movimento.getId());
        return salvata;
    }

    /** Toglie una voce e ricalcola. */
    public void remove(Long additionId) {
        additions.findById(additionId).ifPresent(aggiunta -> {
            Long transactionId = aggiunta.getTransactionId();
            additions.delete(aggiunta);
            // Il flush prima del ricalcolo non è pignoleria: senza, la somma
            // rileggerebbe la riga appena cancellata e il totale resterebbe
            // quello di prima.
            additions.flush();
            recompute(transactionId);
        });
    }

    /**
     * Riporta {@code amount} a {@code baseAmount} più la somma delle aggiunte.
     *
     * <p>Se {@code baseAmount} manca — una riga scritta prima che questa colonna
     * esistesse, o creata da un percorso che non la valorizza — si assume che
     * l'importo attuale <em>sia</em> la base. È l'unica lettura possibile: quel
     * movimento non ha aggiunte, quindi partiva da dove è arrivato.
     */
    public Transaction recompute(Long transactionId) {
        Transaction movimento = transactions.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Movimento non trovato: " + transactionId));

        if (movimento.getBaseAmount() == null) {
            movimento.setBaseAmount(movimento.getAmount());
        }
        BigDecimal somma = additions.sumByTransactionId(transactionId);
        movimento.setAmount(movimento.getBaseAmount().add(somma == null ? BigDecimal.ZERO : somma));
        return transactions.save(movimento);
    }
}
