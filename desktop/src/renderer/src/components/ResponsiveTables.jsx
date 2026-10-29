import { useEffect } from 'react';

/**
 * Rende leggibili su telefono le tabelle già scritte.
 *
 * Il gestionale ha una quarantina di tabelle sparse in venti componenti, quasi
 * tutte a cinque o sei colonne. Sotto i 768px Bootstrap le fa scorrere in
 * orizzontale, che sposta il problema invece di risolverlo: per leggere lo
 * stato di un socio bisogna trascinare la tabella e a quel punto non si vede
 * più di chi è la riga.
 *
 * La soluzione in `assets/mobile.css` trasforma ogni riga in una scheda con
 * coppie etichetta/valore. Perché funzioni, ogni cella deve sapere a quale
 * colonna appartiene — un'informazione che in HTML sta nel `<thead>` e che il
 * CSS non può andare a leggere.
 *
 * Questo componente colma quel vuoto: copia l'intestazione di colonna in un
 * `data-label` su ciascuna cella. Non disegna nulla e non altera il contenuto.
 *
 * ## Perché così e non riscrivendo le tabelle
 *
 * L'alternativa era passare per venti file e aggiungere `data-label` a mano su
 * ogni `<td>`: circa duecento modifiche, ognuna con la sua possibilità di
 * sbagliare colonna, e nessun modo di accorgersene se non guardando. Qui la
 * corrispondenza è presa dalla struttura stessa della tabella, quindi non può
 * disallinearsi, e una colonna aggiunta domani è etichettata da sola.
 *
 * Se questo componente non gira, le tabelle restano quelle di prima con lo
 * scorrimento orizzontale di Bootstrap: è un miglioramento che si aggiunge,
 * non un pezzo da cui il resto dipende.
 *
 * @param {React.RefObject<HTMLElement>} props.rootRef Sottoalbero da sorvegliare.
 *   Limitarlo al contenuto evita di ripassare tutta la pagina a ogni modifica.
 */
export default function ResponsiveTables({ rootRef }) {
    useEffect(() => {
        const root = rootRef?.current;
        if (!root || typeof MutationObserver === 'undefined') return undefined;

        const labelTable = (table) => {
            // `:scope >` e non un discendente qualsiasi: una tabella dentro una
            // cella prenderebbe le intestazioni di quella esterna.
            const headerRow = table.querySelector(':scope > thead > tr');
            if (!headerRow) return;

            const headers = [...headerRow.children].map((cell) =>
                (cell.textContent || '').trim()
            );
            if (headers.every((text) => text === '')) return;

            table.classList.add('jl-cards-sm');

            // Il contenitore di Bootstrap ritaglia con overflow-x: da schede
            // quella finestra di scorrimento non serve più.
            const wrapper = table.parentElement;
            if (wrapper?.classList.contains('table-responsive')) {
                wrapper.classList.add('jl-cards-host');
            }

            for (const row of table.querySelectorAll(':scope > tbody > tr')) {
                let column = 0;
                for (const cell of row.children) {
                    const span = Number.parseInt(cell.getAttribute('colspan') || '1', 10);

                    // Una cella che ne attraversa più d'una non appartiene a una
                    // colonna sola: è il caso della riga "nessun risultato", che
                    // il CSS tratta a parte. Etichettarla sarebbe fuorviante.
                    const label = span > 1 ? '' : (headers[column] ?? '');

                    if (cell.getAttribute('data-label') !== label) {
                        cell.setAttribute('data-label', label);
                    }
                    column += span;
                }
            }
        };

        const scan = () => {
            for (const table of root.querySelectorAll('table')) labelTable(table);
        };

        // React sostituisce le righe a ogni ricarica dei dati: senza osservatore
        // le etichette varrebbero solo per il primo disegno.
        let scheduled = 0;
        const observer = new MutationObserver(() => {
            if (scheduled) return;
            // Un fotogramma di attesa accorpa le mutazioni di un intero
            // aggiornamento in una sola passata.
            scheduled = requestAnimationFrame(() => {
                scheduled = 0;
                scan();
            });
        });

        scan();
        observer.observe(root, { childList: true, subtree: true });

        return () => {
            observer.disconnect();
            if (scheduled) cancelAnimationFrame(scheduled);
        };
    }, [rootRef]);

    return null;
}
