# AssociaGo — analisi e interventi

Data: 29 ottobre 2026. Base iniziale: `a53197c` (`master`).

## Obiettivo

Verificare funzionalità incomplete, problemi di interfaccia e affidabilità, correggere i difetti dimostrabili, eseguire i controlli e aggiornare il servizio Google esistente quando accesso e verifiche lo consentono. Conservare database, file e configurazione della versione pubblicata.

## Interventi completati

- [x] Stabilito lo stato iniziale: test, lint e build riuscivano, ma non coprivano numerosi percorsi incompleti.
- [x] Recuperato il backend effettivamente distribuito da Google Cloud Build e il frontend dal commit esatto pubblicato di `Pswie/terzo-settore`. Integrati con merge a tre vie in questa repository, preservando Clerk, ruoli, viste annuali, tema, allegati nel database, progetti e funzionalità già online.
- [x] Ripristinato il corretto confine desktop/cloud: nel cloud valgono JWT Clerk, CORS esplicito e rifiuto delle vecchie API di login, recupero via codice fiscale e setup, incluse le varianti `/api/v1`.
- [x] Corretta la validazione JWT: l'audience opzionale non elimina la verifica dell'issuer; un ruolo organizzativo vuoto o malformato non ripristina privilegi da metadati precedenti.
- [x] Collegata l'identità Clerk all'ID numerico interno tramite `/api/v1/me`; invii e approvazioni attribuiscono l'autore sul server e rifiutano identificativi falsificati. Gli account non collegati ricevono un'indicazione utilizzabile nell'interfaccia.
- [x] Comunicazioni: destinatari da utenti/iscrizioni dell'associazione corretta, filtri esatti, deduplicazione, riuso del pubblico risolto, nuovi tentativi che saltano le consegne già riuscite. L'interfaccia distingue invio riuscito, parziale e fallito.
- [x] Prestiti: controllo disponibilità e appartenenza del socio, date coerenti, restituzione ripetibile, ripristino disponibilità e restituzione dei prestiti scaduti dall'interfaccia.
- [x] Rimborsi: associazione corretta, protezione dai doppi pagamenti e dai cambi di stato incoerenti, preservando allegati e regole cloud esistenti.
- [x] Import CSV soci: creazione effettiva di utenti e iscrizioni visibili nell'elenco, deduplicazione, delimitatori virgola/punto e virgola e UTF-8 BOM. La conferma richiede il file originale e verifica il checksum; nessun falso stato COMPLETED.
- [x] Modifica soci e attività: caricamento del record e aggiornamento con PUT; nessuna duplicazione. Stato attività allineato al campo reale `isActive`. Eliminati campi per percorsi allegati che il server ignorava: gli allegati si gestiscono nel dettaglio.
- [x] Corretti errore di inizializzazione della pagina Attività e aggiornamento del filtro annuale Eventi.
- [x] Volontari: ricerca sui dati effettivi, cancellazione, apertura dettaglio, errori e operazioni in corso gestiti.
- [x] Azione Nuovo socio nella palette collegata al modulo; navigazione della palette limitata ai ruoli; ripristino dopo errore di un modulo; tema salvato; campi etichettati; layout mobile e modalità scura preservati.
- [x] Anteprime HTML isolate in iframe sandbox; allegati scaricati con autenticazione e gestione dei Blob, anche per XML/testo; URL temporanei rilasciati.
- [x] Login desktop ricordato solo con sessione verificata sul server; logout effettivo sul backend o su Clerk, senza conservare una falsa sessione locale.
- [x] Backup: rimossa la simulazione dell'interfaccia. SQLite usa `VACUUM INTO` sul datasource attivo e include il WAL. MySQL richiede il backup dal servizio database. Prima dell'aggiornamento desktop attraverso V41 viene creata automaticamente una copia coerente; un errore nel backup arresta l'aggiornamento.
- [x] Electron: URL remoti validati senza credenziali, prefissi conservati, collegamenti esterni limitati a HTTP/HTTPS/mailto, navigazioni esterne protette, configurazione `backend.json` inclusa quando presente. Impediti falsi export JSON e ripristini SQLite sul database in uso o remoto.
- [x] Packaging: dipendenze installate da lockfile anche su Windows/macOS, JRE macOS coerente con l'architettura del runner. CI estesa a test API/policy desktop e build web.
- [x] Individuati servizio Google, revisione, immagine precedente e origine Vercel. Procedura candidata senza traffico e ripristino documentati in `CLOUD-DEPLOYMENT.md`.

## Pubblicazione: bloccata dal database esterno

- [ ] Ripristinare Aiven: l'hostname MySQL configurato restituisce NXDOMAIN. Nei log Cloud Run Flyway non riesce ad aprire la connessione, il backend non parte e risponde HTTP 503. La causa amministrativa (sospensione per inattività o altro) non è verificabile senza la console Aiven.
- [ ] Dopo il ripristino, salvare/verificare il backup e la storia Flyway; provare accesso Clerk e operazioni reali sulla revisione candidata, quindi assegnarle il traffico sul servizio esistente.
- [ ] Pubblicare il frontend sullo stesso progetto Vercel, preservando le sue variabili e il sito primario Clerk. Il progetto è collegato a `Pswie/terzo-settore`, non a `Pswie/AssociaGo`: vedere la procedura nel documento di rilascio.
- [ ] Verificare la nuova versione online prima di rimuovere eventuali artefatti precedenti. Conservare inizialmente la revisione per il ripristino.

Come concordato con l'utente, Aiven sarà ripristinato insieme in un secondo momento. Nessun servizio, dato di produzione, segreto, traffico o pubblicazione esistente è stato modificato. Nessun push remoto effettuato.

## Funzionalità da distinguere dal prodotto già disponibile

Sono presenti package descritti come scheletri per billing, fundraising, documenti, progetti, bandi e reporting. Saranno verificati rispetto alle schermate e ai servizi realmente esposti: un package vuoto non dimostra da solo una funzione rotta. Gli sviluppi che richiedono regole di prodotto o integrazioni esterne verranno descritti esplicitamente.

## Verifiche e limiti

- Build completa Gradle riuscita: **134 test**, **0 errori/fallimenti**, **2 saltati** perché richiedono MySQL/Docker dedicato. Inclusi test reali di upgrade SQLite `32→42` e `40→42` con recuperabilità dei dati rimossi dalla V41.
- Test API/policy desktop: **12 superati**. Lint frontend senza errori o avvisi; build web e desktop e verifica asset packaging riuscite.
- Controllo mobile a **320, 375, 390, 768 e 1280 px**, apertura/chiusura e focus del cassetto, modale a schermo intero. Schermate ispezionate.
- Browser su database temporaneo separato: login, caricamento dati e modifica di socio e attività con verifica di ID e conteggio invariati; creazione di una nuova attività verificata con `isActive=true`. Health locale `UP`. Nessun dato reale usato.
- I 12 script MySQL mantengono i checksum recuperati dalla distribuzione; nessuna nuova migrazione cloud aggiunta. V1 ha checksum Flyway `-1914481742`.
- Restano avvisi di stile Java preesistenti e bundle web superiori alla soglia consigliata da Vite; non bloccano la build.
- La compilazione web locale usa configurazione di esempio/assente: il collaudo autenticato del sito e del database MySQL reale resta legato al ripristino Aiven.

Artefatti locali: backend `build/libs/AssociaGo-0.1.0.jar`, frontend web
`desktop/dist-web`, bundle Electron `desktop/out`. Una copia dei sorgenti
verificati e il riepilogo dei controlli sono in `build/audit-release/`:
`AssociaGo-audit-2026-10-29-source.zip` e `verification.json`. Il pacchetto
esclude credenziali locali, database, dipendenze e cache. Non è stato generato
un nuovo installer né pubblicata una versione remota.

## Sviluppi ulteriori separati dalle correzioni

- [ ] Importazione partecipanti attività/eventi: il flusso incompleto ora restituisce un errore esplicito invece di dichiarare un'importazione mai avvenuta. Va definito e conservato il contesto del destinatario prima di abilitarlo.
- [ ] Import/export JSON completo e ripristino SQLite guidato a backend fermo. I vecchi handler non dichiarano più successi fittizi né sovrascrivono un database in uso.
- [ ] Se il prodotto dovrà ospitare più associazioni indipendenti nella stessa installazione, completare l'isolamento delle vecchie API `Member` e unificare le due anagrafiche `Member` e `User`/iscrizioni. Il sito attuale è un'installazione monoassociazione.
- [ ] Se aumentano i client concorrenti, introdurre controllo di concorrenza distribuito dei prestiti e invio comunicazioni tramite coda/idempotenza persistente. I test correnti coprono i flussi sequenziali e i nuovi tentativi.
- [ ] Valutare caricamento dei moduli su richiesta per ridurre il bundle iniziale; non nascondere gli avvisi aumentando solo la soglia.
