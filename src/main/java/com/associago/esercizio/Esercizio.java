package com.associago.esercizio;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * L'esercizio: che cosa appartiene a un anno.
 *
 * <p>La regola sta qui, sul server, e una volta sola. Prima viveva nel browser,
 * ripetuta in ogni schermata: funzionava finché i dati erano pochi, ma
 * scaricare dieci anni per mostrarne uno è un lavoro che cresce mentre
 * l'associazione lavora, e una regola ripetuta in dieci punti è una regola che
 * prima o poi diverge in uno.
 *
 * <h2>Due specie di cose, non una</h2>
 *
 * Trattare tutto per data sarebbe sbagliato in modo silenzioso — nessun errore,
 * solo elenchi incompleti che sembrano giusti.
 *
 * <ul>
 *   <li><strong>Puntuali</strong> — un movimento, una fattura, un'assemblea, un
 *       buono consegnato. Sono accadute in un giorno: appartengono a
 *       quell'anno e a nessun altro.</li>
 *   <li><strong>Con un periodo</strong> — una tessera, un volontario nel
 *       registro, un'attività, un evento, una polizza, un oggetto in
 *       inventario. Appartengono a <em>ogni anno che attraversano</em>.</li>
 * </ul>
 *
 * <p>Da cui il criterio: <em>ciò che continua non si archivia, ciò che è finito
 * sì</em>. Una tessera senza scadenza è in essere, quindi compare anche negli
 * anni successivi; quando cessa, resta visibile solo negli anni che ha coperto.
 *
 * <h2>Le assenze contano come «sempre»</h2>
 *
 * Una data mancante non fa sparire una riga da tutti gli anni — dove nessuno la
 * troverebbe più. Un inizio assente vale «da sempre», una fine assente vale
 * «ancora in corso».
 *
 * <h2>Archiviare non sposta niente</h2>
 *
 * I dati restano dove sono: si tengono finché una persona non decide di
 * toglierli, e l'anno è soltanto la lente con cui si guardano. Spostare righe in
 * tabelle di archivio significherebbe avere due posti dove cercare la stessa
 * cosa, e prima o poi trovarne solo uno.
 */
public final class Esercizio {

    private Esercizio() {
    }

    /** Il primo giorno dell'anno. */
    public static LocalDate inizio(int anno) {
        return LocalDate.of(anno, 1, 1);
    }

    /** L'ultimo giorno dell'anno. */
    public static LocalDate fine(int anno) {
        return LocalDate.of(anno, 12, 31);
    }

    /** Il primo istante dell'anno, per le colonne che tengono anche l'ora. */
    public static LocalDateTime inizioIstante(int anno) {
        return inizio(anno).atStartOfDay();
    }

    /**
     * L'ultimo istante dell'anno.
     *
     * <p>Mezzanotte del 31 dicembre e non del 1° gennaio successivo: un
     * confronto {@code <=} con l'inizio del giorno dopo farebbe rientrare
     * nell'anno un evento del primo gennaio.
     */
    public static LocalDateTime fineIstante(int anno) {
        return fine(anno).atTime(23, 59, 59);
    }

    /**
     * @return vero se l'anno indicato è utilizzabile come esercizio.
     *
     * <p>Serve ai controller: un anno arrivato dalla query è un numero scritto
     * da chiunque, e {@code YEAR(colonna) = 0} restituirebbe un elenco vuoto
     * senza spiegare perché.
     */
    public static boolean valido(Integer anno) {
        return anno != null && anno >= 2000 && anno <= 2100;
    }
}
