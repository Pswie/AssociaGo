package com.associago.volunteer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * I limiti dell'art. 17 CTS sui rimborsi ai volontari.
 *
 * <p>Sono controlli che proteggono l'associazione in sede di verifica: un
 * forfettario oltre soglia è un rilievo, e chi inserisce il dato non ha modo
 * di accorgersene da solo.
 *
 * <p>La presenza del giustificativo è un parametro e non più un campo del
 * rimborso: prima si deduceva da {@code receipt_path}, una stringa che puntava
 * a un file sul disco locale del contenitore e che quindi poteva dire
 * «documentato» su una spesa senza documento. Ora chi chiama la ricava
 * dall'allegato vero, e la regola resta provabile senza database.
 */
@DisplayName("Rimborsi ai volontari, art. 17 CTS")
class Art17RulesTest {

    private static final boolean CON_GIUSTIFICATIVO = true;
    private static final boolean SENZA_GIUSTIFICATIVO = false;

    private static VolunteerExpense rimborso(String tipo, String importo, LocalDate data) {
        VolunteerExpense expense = new VolunteerExpense();
        expense.setVolunteerId(1L);
        expense.setReimbursementType(tipo);
        expense.setAmount(new BigDecimal(importo));
        expense.setExpenseDate(data);
        expense.setDescription("Trasferta");
        return expense;
    }

    private static final LocalDate GIORNO = LocalDate.of(2026, 7, 24);

    @Test
    @DisplayName("l'analitico senza giustificativo non passa")
    void analiticoSenzaGiustificativo() {
        Art17Rules.Check check = Art17Rules.verify(
                rimborso(Art17Rules.ANALITICO, "250.00", GIORNO), List.of(), SENZA_GIUSTIFICATIVO);

        assertThat(check.allowed()).isFalse();
        assertThat(check.reason()).contains("giustificativo");
    }

    @Test
    @DisplayName("l'analitico documentato passa senza tetti")
    void analiticoDocumentatoNonHaTetti() {
        VolunteerExpense expense = rimborso(Art17Rules.ANALITICO, "1200.00", GIORNO);

        assertThat(Art17Rules.verify(expense, List.of(), CON_GIUSTIFICATIVO).allowed()).isTrue();
    }

    @Test
    @DisplayName("il forfettario senza autocertificazione non passa")
    void forfettarioSenzaAutocertificazione() {
        Art17Rules.Check check = Art17Rules.verify(
                rimborso(Art17Rules.FORFETTARIO, "8.00", GIORNO), List.of(), SENZA_GIUSTIFICATIVO);

        assertThat(check.allowed()).isFalse();
        assertThat(check.reason()).contains("autocertificazione");
    }

    @Test
    @DisplayName("al forfettario il giustificativo non serve: basta l'autocertificazione")
    void forfettarioEntroIlGiornaliero() {
        VolunteerExpense expense = rimborso(Art17Rules.FORFETTARIO, "10.00", GIORNO);
        expense.setSelfCertified(true);

        assertThat(Art17Rules.verify(expense, List.of(), SENZA_GIUSTIFICATIVO).allowed()).isTrue();
    }

    @Test
    @DisplayName("due forfettari nello stesso giorno che sommati sforano non passano")
    void sommaGiornalieraOltreSoglia() {
        VolunteerExpense esistente = rimborso(Art17Rules.FORFETTARIO, "7.00", GIORNO);
        esistente.setSelfCertified(true);

        VolunteerExpense nuovo = rimborso(Art17Rules.FORFETTARIO, "4.00", GIORNO);
        nuovo.setSelfCertified(true);

        Art17Rules.Check check =
                Art17Rules.verify(nuovo, List.of(esistente), SENZA_GIUSTIFICATIVO);

        assertThat(check.allowed()).isFalse();
        assertThat(check.reason()).contains("giornaliero");
    }

    @Test
    @DisplayName("il tetto mensile di 150 euro vale sull'insieme del mese")
    void sommaMensileOltreSoglia() {
        // Quindici giorni da 10 euro fanno 150: il sedicesimo sfora, pur
        // restando entro il limite giornaliero.
        List<VolunteerExpense> precedenti = java.util.stream.IntStream.rangeClosed(1, 15)
                .mapToObj(day -> {
                    VolunteerExpense expense = rimborso(
                            Art17Rules.FORFETTARIO, "10.00", LocalDate.of(2026, 7, day));
                    expense.setSelfCertified(true);
                    return expense;
                })
                .toList();

        VolunteerExpense nuovo = rimborso(Art17Rules.FORFETTARIO, "10.00", LocalDate.of(2026, 7, 20));
        nuovo.setSelfCertified(true);

        Art17Rules.Check check = Art17Rules.verify(nuovo, precedenti, SENZA_GIUSTIFICATIVO);

        assertThat(check.allowed()).isFalse();
        assertThat(check.reason()).contains("mensile");
    }

    @Test
    @DisplayName("i rimborsi analitici non consumano il plafond del forfettario")
    void analiticoNonConsumaIlPlafond() {
        VolunteerExpense analitico = rimborso(Art17Rules.ANALITICO, "500.00", GIORNO);

        VolunteerExpense forfettario = rimborso(Art17Rules.FORFETTARIO, "10.00", GIORNO);
        forfettario.setSelfCertified(true);

        assertThat(Art17Rules.verify(forfettario, List.of(analitico), SENZA_GIUSTIFICATIVO)
                .allowed()).isTrue();
    }

    @Test
    @DisplayName("il mese precedente non entra nel conteggio")
    void mesePrecedenteNonConta() {
        List<VolunteerExpense> giugno = java.util.stream.IntStream.rangeClosed(1, 15)
                .mapToObj(day -> {
                    VolunteerExpense expense = rimborso(
                            Art17Rules.FORFETTARIO, "10.00", LocalDate.of(2026, 6, day));
                    expense.setSelfCertified(true);
                    return expense;
                })
                .toList();

        VolunteerExpense luglio = rimborso(Art17Rules.FORFETTARIO, "10.00", GIORNO);
        luglio.setSelfCertified(true);

        assertThat(Art17Rules.verify(luglio, giugno, SENZA_GIUSTIFICATIVO).allowed()).isTrue();
    }

    @Test
    @DisplayName("un importo nullo o negativo non passa nemmeno col giustificativo")
    void importoNonPositivo() {
        assertThat(Art17Rules.verify(rimborso(Art17Rules.ANALITICO, "0.00", GIORNO),
                List.of(), CON_GIUSTIFICATIVO).allowed()).isFalse();
        assertThat(Art17Rules.verify(rimborso(Art17Rules.ANALITICO, "-5.00", GIORNO),
                List.of(), CON_GIUSTIFICATIVO).allowed()).isFalse();
    }
}
