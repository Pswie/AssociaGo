package com.associago.volunteer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * I limiti dell'art. 17 del Codice del Terzo Settore (D.Lgs. 117/2017) sui
 * rimborsi ai volontari.
 *
 * <p>Due regimi distinti:
 * <ul>
 *   <li><strong>Analitico</strong> (comma 3): spesa effettivamente sostenuta e
 *       documentata. Nessun tetto, ma il giustificativo è obbligatorio.</li>
 *   <li><strong>Forfettario</strong> (comma 4): rimborso su autocertificazione
 *       ex art. 46 DPR 445/2000, ammesso solo entro <strong>10 euro
 *       giornalieri</strong> e <strong>150 euro mensili</strong>, e solo se
 *       l'organo sociale ha deliberato tipologie di spesa e attività.</li>
 * </ul>
 *
 * <p>Le soglie sono costanti perché sono di legge, non una preferenza
 * dell'associazione: se cambia la norma si cambia qui, in un punto solo.
 *
 * <p><strong>Questa classe verifica i limiti quantitativi, non sostituisce la
 * delibera.</strong> Il rispetto dei tetti è condizione necessaria, non
 * sufficiente: resta responsabilità dell'associazione avere deliberato le
 * tipologie ammesse.
 *
 * @since 0.2.0
 */
public final class Art17Rules {

    public static final String ANALITICO = "ANALITICO";
    public static final String FORFETTARIO = "FORFETTARIO";

    /** Art. 17 c. 4: tetto giornaliero del rimborso forfettario. */
    public static final BigDecimal LIMITE_GIORNALIERO = new BigDecimal("10.00");

    /** Art. 17 c. 4: tetto mensile del rimborso forfettario. */
    public static final BigDecimal LIMITE_MENSILE = new BigDecimal("150.00");

    private Art17Rules() {
    }

    /**
     * Esito della verifica: ammesso oppure il motivo del rifiuto.
     *
     * @param allowed se il rimborso può essere registrato
     * @param reason motivo del rifiuto, vuoto quando ammesso
     */
    public record Check(boolean allowed, String reason) {

        static Check ok() {
            return new Check(true, "");
        }

        static Check no(String reason) {
            return new Check(false, reason);
        }
    }

    /**
     * Verifica un rimborso rispetto ai limiti di legge.
     *
     * @param candidate il rimborso da registrare
     * @param sameVolunteerSameMonth i rimborsi già registrati allo stesso
     *                               volontario nello stesso mese, escluso il
     *                               candidato
     * @param hasReceipt se il giustificativo della spesa esiste davvero.
     *
     *        <p>Prima questa classe lo deduceva da {@code receipt_path}, cioè da
     *        una stringa che puntava a un file sul disco locale del contenitore
     *        — su Cloud Run azzerato a ogni riavvio. Una regola di ammissibilità
     *        appoggiata a un percorso che non garantisce l'esistenza di niente
     *        rispondeva «documentato» su una spesa senza documento.
     *
     *        <p>Ora è un parametro, e chi chiama lo ricava dalla presenza vera
     *        dell'allegato ({@code AttachmentService.exists}). La regola resta
     *        pura: non sa dove stiano i file, e si può provare senza database.
     */
    public static Check verify(VolunteerExpense candidate,
                               List<VolunteerExpense> sameVolunteerSameMonth,
                               boolean hasReceipt) {
        if (candidate.getAmount() == null || candidate.getAmount().signum() <= 0) {
            return Check.no("L'importo del rimborso deve essere positivo.");
        }
        if (candidate.getExpenseDate() == null) {
            return Check.no("La data della spesa è obbligatoria.");
        }

        if (ANALITICO.equals(candidate.getReimbursementType())) {
            return verifyAnalitico(hasReceipt);
        }
        if (FORFETTARIO.equals(candidate.getReimbursementType())) {
            return verifyForfettario(candidate, sameVolunteerSameMonth);
        }
        return Check.no("Regime di rimborso non riconosciuto: "
                + candidate.getReimbursementType() + ". Ammessi: ANALITICO, FORFETTARIO.");
    }

    private static Check verifyAnalitico(boolean hasReceipt) {
        if (!hasReceipt) {
            return Check.no("Il rimborso analitico (art. 17 c. 3) richiede il "
                    + "giustificativo della spesa sostenuta.");
        }
        return Check.ok();
    }

    private static Check verifyForfettario(VolunteerExpense candidate,
                                           List<VolunteerExpense> sameVolunteerSameMonth) {
        if (!candidate.isSelfCertified()) {
            return Check.no("Il rimborso forfettario (art. 17 c. 4) richiede "
                    + "l'autocertificazione del volontario.");
        }

        BigDecimal sameDay = sumForfettario(sameVolunteerSameMonth,
                expense -> candidate.getExpenseDate().equals(expense.getExpenseDate()));
        BigDecimal dailyTotal = sameDay.add(candidate.getAmount());
        if (dailyTotal.compareTo(LIMITE_GIORNALIERO) > 0) {
            return Check.no(String.format(
                    "Superato il limite giornaliero di %s euro (art. 17 c. 4): "
                            + "il totale del giorno sarebbe %s euro.",
                    LIMITE_GIORNALIERO.toPlainString(), dailyTotal.toPlainString()));
        }

        BigDecimal sameMonth = sumForfettario(sameVolunteerSameMonth,
                expense -> isSameMonth(candidate.getExpenseDate(), expense.getExpenseDate()));
        BigDecimal monthlyTotal = sameMonth.add(candidate.getAmount());
        if (monthlyTotal.compareTo(LIMITE_MENSILE) > 0) {
            return Check.no(String.format(
                    "Superato il limite mensile di %s euro (art. 17 c. 4): "
                            + "il totale del mese sarebbe %s euro.",
                    LIMITE_MENSILE.toPlainString(), monthlyTotal.toPlainString()));
        }

        return Check.ok();
    }

    /**
     * Nel conteggio entrano solo i forfettari: i rimborsi analitici sono spese
     * documentate e non consumano il plafond dell'autocertificazione.
     */
    private static BigDecimal sumForfettario(List<VolunteerExpense> expenses,
                                             java.util.function.Predicate<VolunteerExpense> filter) {
        return expenses.stream()
                .filter(expense -> FORFETTARIO.equals(expense.getReimbursementType()))
                .filter(expense -> expense.getExpenseDate() != null)
                .filter(filter)
                .map(VolunteerExpense::getAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static boolean isSameMonth(LocalDate a, LocalDate b) {
        return a.getYear() == b.getYear() && a.getMonth() == b.getMonth();
    }
}
