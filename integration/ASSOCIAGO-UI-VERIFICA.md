# Interfaccia Progetti: funzionalità e collaudo

Aggiornamento del 29 ottobre 2026. La nuova voce **Progetti** rispetta il tema esistente di AssociaGo e i permessi applicativi: presidente, responsabile amministrativo e coordinatore servizi; sul desktop rimane disponibile all’amministratore locale.

## Percorsi disponibili

- Creare, modificare e selezionare un progetto dell’associazione: nome, stato operativo, referente, area, previsione economica, fine prevista, finanziamento e note.
- Aprire il progetto comune indicato da `?suiteProject=<UUID>` dopo l’accesso Clerk esistente. Il contesto si risolve esclusivamente attraverso la corrispondenza AssociaGo nel registro: titolo e posizione nell’elenco non vengono usati come identificativi.
- Condividere una scheda locale come nuovo progetto comune oppure collegarla esplicitamente a un progetto già presente. Se il progetto comune non ha ancora la scheda AssociaGo, si può richiederne l’allineamento o scegliere un progetto locale da collegare.
- Aprire il medesimo progetto in LibrePM e Bandi, vedere ultimo successo/errore per sito e richiedere un nuovo allineamento. L’interfaccia rifiuta link eseguibili o con credenziali incorporate.
- Assegnare soci e volontari attraverso i candidati autorizzati dal backend. I tipi di persona rimangono distinti: non si confondono identificativi delle iscrizioni con quelli dei volontari.
- Registrare turni dei volontari assegnati e aggiornarne date, stato e ore svolte. La candidatura e le presenze mantengono stati indipendenti.
- Per i ruoli amministrativi, vedere preventivo, entrate, uscite e saldo effettivi e collegare un movimento contabile esistente dell’associazione. Il comando non crea una seconda registrazione. La disponibilità dei movimenti è letta dall’endpoint specifico del progetto, con controllo associazione sul server.

Il progetto attraversa più esercizi: il rendiconto della scheda comprende il progetto complessivo, mentre la contabilità ordinaria mantiene il proprio filtro annuale.

Sul web, un backend con una sola associazione mantiene l’ingresso automatico. Se le associazioni sono diverse, compare una scelta esplicita: non viene più usata la prima riga dell’elenco. Si può fissare l’installazione con `VITE_ASSOCIATION_ID`, variabile pubblica numerica da configurare coerentemente con `SUITE_ASSOCIATION_ID` del backend; un valore errato blocca l’ingresso con un errore invece di selezionare un’altra associazione. Il browser non decide l’organizzazione dei peer. Non è stato possibile verificare la cardinalità del database pubblicato mentre Aiven è indisponibile: questo elimina un rischio osservato nel codice, senza affermare che la produzione contenesse già più associazioni.

## Permessi e indisponibilità

Il coordinatore non richiede gli endpoint di rendiconto o movimenti. Un ruolo senza accesso ai progetti riceve un messaggio esplicito quando apre un link diretto; il backend continua a proteggere le risorse. La pagina mantiene i progetti locali utilizzabili anche quando la suite è disattivata o il registro non è raggiungibile. Un UUID inesistente, non autorizzato o non associato a questa associazione non apre silenziosamente un altro progetto.

## Verifiche eseguite

- `npm run lint`: passato.
- `npm run test:unit`: 16 test passati, inclusi nuovi controlli di corrispondenza esplicita, assenza di ripiego sul titolo, scelta dell’associazione, link sicuri, ruoli finanziari, condivisione del solo ID locale e collegamento contabile senza duplicazione.
- `npm run build:web` e `npm run build`: passati per web ed Electron. Restano gli avvisi esistenti su dimensione bundle e dati Browserslist.
- Collaudo browser reale con fixture di sviluppo: selezione del progetto, assegnazione volontario, turno svolto di 2,5 ore e collegamento del movimento esistente; payload controllati.
- A 390 pixel, nessuno scorrimento orizzontale del documento; schermata in `desktop/.mobile-smoke/projects-390.png` (artefatto locale ignorato da Git).
- Con ruolo coordinatore: rendiconto assente e zero chiamate agli endpoint finanziari.
- Con UUID sconosciuto: errore visibile, nessun progetto locale selezionato e nessun pannello operativo di un altro progetto.
- Con suite disattivata: selezione e operatività locale disponibili, nessuna chiamata al catalogo remoto.

Il collaudo interfaccia usa dati fittizi e non sostituisce il test HTTP fra i tre backend, documentato nel report generale. La fixture `projects-harness.html` e il relativo ingresso React sono esclusi dalla build di produzione dal singolo ingresso previsto da `vite.web.config.mjs`. Per riaprirla in sviluppo: avviare `npm run dev:web` e visitare `/projects-harness.html?suiteProject=a5cf26bb-2601-47d7-ac55-3b514920f703`; `role=coordinatore-servizi` e `disabled=1` attivano gli altri scenari.
