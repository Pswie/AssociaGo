package com.associago.attachment;

/**
 * A che cosa può essere attaccato un allegato.
 *
 * <p>È un enum e non una stringa libera perché {@code owner_type} +
 * {@code owner_id} è un riferimento che il database non può garantire: una
 * chiave esterna polimorfica non esiste. Il vincolo sta qui — un valore
 * inventato non arriva mai alla query — ed è l'unico che ci sia.
 *
 * <p>Ogni voce corrisponde a una tabella che prima aveva una colonna
 * {@code *_path}. L'elenco è la mappa di quel che è stato sostituito.
 */
public enum AttachmentOwner {

    /** Immagine e documento di un'attività. */
    ACTIVITY,
    /** Ricevuta di un costo di attività. */
    ACTIVITY_COST,
    /** Verbale di assemblea firmato e avviso di convocazione: da conservare. */
    ASSEMBLY,
    ASSEMBLY_DOCUMENT,
    /** Statuto, regolamento, delibere. */
    ASSOCIATION_DOCUMENT,
    /** Bilancio depositato, verbale di approvazione, relazione di missione. */
    ANNUAL_BALANCE,
    /** Intestazione, piè di pagina e sfondo di un modello di attestato. */
    CERTIFICATE_TEMPLATE,
    /** Copia firmata di un attestato consegnato. */
    ISSUED_CERTIFICATE,
    /** Fattura ricevuta: PDF o XML della fattura elettronica. */
    EXPENSE_DOCUMENT,
    /** Giustificativo di un movimento. */
    TRANSACTION,
    /** Foto, garanzia o manuale di un oggetto in inventario. */
    INVENTORY_ITEM,
    /** Ricevuta di un rimborso a un volontario — art. 17 la richiede. */
    VOLUNTEER_EXPENSE,
    /** Il contratto della polizza dei volontari — art. 18. */
    INSURANCE_POLICY;

    /*
     * Quattro voci sono state tolte, e vale la pena dire perché invece di
     * lasciarle in attesa di un collegamento che non avrebbe senso:
     *
     *  * FISCAL_COMPARISON e INVOICE_CHECK — le loro colonne `pdf_path` e
     *    `csv_path` non erano allegati caricati da qualcuno: erano il posto
     *    dove salvare un file **generato** dal programma. Un rapporto che si
     *    ricalcola da capo quando serve non ha bisogno di essere conservato, e
     *    infatti nessun servizio ci scriveva mai.
     *
     *  * RESOURCE — il modulo delle risorse prenotabili è commentato fuori
     *    dalla navigazione. Collegare un pannello a una schermata irraggiungibile
     *    sarebbe codice morto che sembra una funzione.
     *
     *  * ASSOCIATION — statuto e regolamento passano da ASSOCIATION_DOCUMENT,
     *    che è la tabella fatta apposta e che tiene anche tipo, titolo e data.
     *    Due strade per lo stesso documento sono la premessa perché fra sei
     *    mesi metà stia di qua e metà di là.
     *
     *  * INVOICE — le fatture emesse non hanno nessun endpoint: la tabella
     *    esiste e `InvoiceRepository` lo usa solo `FiscalService` per i suoi
     *    controlli. Non c'è modo di crearne una, quindi non c'è niente a cui
     *    allegare un file. Se un domani si costruisce quella parte, la voce si
     *    rimette insieme al resto.
     */

    /** @return la voce corrispondente, oppure {@code null} se il nome non esiste. */
    public static AttachmentOwner from(String valore) {
        if (valore == null) return null;
        try {
            return valueOf(valore.trim().toUpperCase());
        } catch (IllegalArgumentException sconosciuto) {
            return null;
        }
    }
}
