# AssociaGo, LibrePM e Bandi — lavoro eseguito

**Stato aggiornato:** la pubblicazione attuale, il passaggio a Supabase e il
portale Bandi con nodo PC sono descritti in
[PUBBLICAZIONE-SUITE.md](PUBBLICAZIONE-SUITE.md). Aiven era vuoto e non deve
essere ripristinato. Le sezioni datate 29 ottobre sotto restano il resoconto
della prima fase, non l'elenco operativo attuale.

Aggiornamento del 30 ottobre: l'utente ha confermato che Aiven non conteneva dati.
È stata quindi avviata la preparazione dei tre archivi per PostgreSQL/Supabase,
senza recupero o trasferimento da Aiven. Lo stato attuale di questo intervento è
in [MIGRAZIONE-SUPABASE.md](MIGRAZIONE-SUPABASE.md). Il resoconto seguente conserva
le verifiche e le decisioni dell'integrazione del 29 ottobre.

Aggiornamento: 29 ottobre 2026. Repository locali: AssociaGo, LibrePM e Bandi sotto `C:\Users\lange\Desktop\Github`.

Ho realizzato il collegamento fra le tre applicazioni mantenendo tre siti distinti. Il progetto ha un'identità comune, può essere condiviso da ciascun applicativo e aperto negli altri attraverso un collegamento preciso. Sono state aggiunte API, persistenza, interfacce, migrazioni e verifiche automatiche.

Il collaudo locale finale fra i tre backend reali ha superato 46 verifiche HTTP. La pubblicazione e l'attivazione con i dati reali restano da completare dopo il ripristino Aiven, che abbiamo concordato di affrontare insieme. Non ho sostituito il database con uno vuoto né cambiato traffico, domini o credenziali di produzione.

## Architettura scelta

| Applicazione | Responsabilità autorevole | Dati ricevuti dalle altre |
| --- | --- | --- |
| LibrePM | Registro comune, piano operativo, attività e preventivo | Rendiconto e ore da AssociaGo; stato candidatura da Bandi |
| AssociaGo | Soci, volontari, assegnazioni, turni e movimenti contabili effettivi | Progetto operativo, preventivo e collegamenti comuni |
| Bandi | Preparazione della candidatura, revisioni, valutazione e documenti | Progetto comune e riepiloghi di gestione |

Il registro vive nel backend LibrePM. `suiteProjectId` coincide con l'UUID del progetto LibrePM; gli ID numerici AssociaGo e gli UUID dei fascicoli Bandi restano distinti e sono collegati esplicitamente. Titoli uguali non producono abbinamenti automatici.

Ho mantenuto database operativi separati: Bandi usa PostgreSQL, le applicazioni Java SQLite/MySQL. Un database condiviso avrebbe richiesto una conversione dei motori e avrebbe accoppiato migrazioni, permessi e rilasci. Si potrà condividere l'infrastruttura di hosting mantenendo il possesso delle tabelle in ciascun applicativo. L'integrazione passa da API controllate.

L'allineamento è esplicito e periodico, con stato e ultimo successo per ciascun sito. Non è una transazione distribuita e non garantisce aggiornamento istantaneo durante un'interruzione. Un peer indisponibile viene segnalato; i dati locali restano utilizzabili e il comando può essere ripetuto.

## Registro delle attività

- [x] Analizzare modelli, identificativi, login, repository e servizi pubblicati.
- [x] Definire responsabilità, contratto v1 e schemi verificabili condivisi.
- [x] Implementare registro, provenienza, revisioni e corrispondenze in LibrePM.
- [x] Implementare importazioni idempotenti, proiezioni e gestione dei conflitti.
- [x] Aggiungere autenticazione fra server e controlli di organizzazione/associazione.
- [x] Collegare selezione e navigazione nelle tre interfacce.
- [x] Sviluppare Progetti AssociaGo con persone, turni e movimenti esistenti.
- [x] Collegare i fascicoli Bandi preservando catalogo e revisioni.
- [x] Aggiungere migrazioni additive, configurazione e documentazione.
- [x] Collaudare contratti, componenti e tre backend reali su dati sintetici.
- [ ] Ripristinare/verificare Aiven e i dati reali, insieme all'utente.
- [ ] Eseguire backup e prova migrazioni su MySQL/PostgreSQL reali.
- [ ] Configurare chiavi, organizzazione, associazione, profilo e URL pubblicati.
- [ ] Pubblicare e verificare le nuove revisioni conservando siti, Clerk e dati.

Le ultime quattro voci riguardano l'attivazione nell'ambiente reale.

## LibrePM: funzionalità sviluppate

- Registro persistente, corrispondenze locali, stato per applicazione e revisioni sorgenti osservate.
- Import iniziale da AssociaGo/Bandi con un proprietario reale e appartenenza OWNER. Il retry non crea un secondo piano; una nuova revisione sorgente non sovrascrive automaticamente la pianificazione.
- API utente con controllo dell'accesso effettivo al progetto. Un VIEWER può leggere ma non pubblicare/allineare.
- Proiezioni versionate, riepiloghi in sola lettura, errori persistiti e retry separato per ciascun peer.
- Allineamento manuale e periodico con lease persistente contro sovrapposizioni fra istanze.
- Selezione persistente per utente/organizzazione, schermata **Integrazioni → Progetti collegati**, contesto ripristinato dopo accesso e collegamenti agli altri siti.
- Somme di preventivo calcolate con `BigDecimal` nella proiezione. Il modello nativo usa ancora `Double`: gli importi non rappresentabili senza perdita sono rifiutati con 409, senza arrotondamento nascosto.

Dettagli: [LibrePM/INTEGRAZIONE-SUITE.md](../LibrePM/INTEGRAZIONE-SUITE.md).

## AssociaGo: funzionalità e correzioni

La nuova voce **Progetti** completa un modulo che aveva già parte del backend ma non una schermata operativa completa.

- Creazione, modifica e selezione di progetti locali con stato, referente, area, preventivo, scadenza e note.
- Condivisione nel registro o collegamento esplicito a un progetto comune esistente.
- Risoluzione di `?suiteProject=UUID`, riepiloghi, stato di allineamento e apertura dello stesso progetto negli altri siti. Un ID errato/non autorizzato mostra un errore e non seleziona un progetto diverso.
- Assegnazione dei soci attraverso le iscrizioni attive dell'associazione. I volontari sono candidati solo quando il codice fiscale permette di collegarli a un'iscrizione attiva; nomi ed email non vengono usati per indovinare identità.
- Registrazione e aggiornamento dei turni dei volontari assegnati, con intervallo, stato e ore. Le ore condivise provengono dai turni `COMPLETED`.
- Rendiconto dell'intero progetto, anche su più esercizi; la contabilità ordinaria conserva i filtri annuali.
- Collegamento dei movimenti contabili già registrati nell'associazione, senza duplicare movimenti o prima nota.
- Esportazione dei soli aggregati: entrate, uscite, saldo, assegnazioni e ore. Nessuna persona, email, codice fiscale o documento nella proiezione comune.
- Controlli del ruolo e dell'iscrizione attiva all'associazione, oltre al token Clerk; riepiloghi finanziari e contributo richiesto oscurati ai ruoli privi di accesso contabile.
- Controlli di associazione anche sulle API precedenti dei turni, per evitare di aggirare la nuova pagina.
- Conservazione delle note operative locali durante gli aggiornamenti. Lo stato candidatura non cambia automaticamente quello operativo.
- Recupero del collegamento persistente quando si ripete la condivisione dopo una risposta persa: non viene creato un secondo piano remoto. I conflitti locali sono verificati prima di mutare il registro.
- Migrazione SQLite dei dettagli dei turni già usati dall'entità Java ma assenti dal database desktop: il difetto causava HTTP 500 nel primo collaudo reale.
- Risposte 404 per risorse HTTP mancanti e 400 per JSON malformato; parsing rigoroso delle versioni e degli importi suite, senza cambiare il mapper globale.
- Scelta esplicita dell'associazione quando il backend ne contiene più di una. L'eventuale `VITE_ASSOCIATION_ID` pubblico deve identificare un'associazione realmente presente.

La revisione finale protegge anche le API finanziarie precedenti e i PDF: autenticazione e iscrizione attiva, associazione reale del movimento, prima nota e confronti limitati all'ente selezionato, report annuale limitato anche all'anno. Il collegamento contabile usa aggiornamenti condizionali atomici: non sposta un movimento già assegnato e non attribuisce dati a un progetto cancellato. La cancellazione di un progetto con movimenti viene rifiutata con 409, conservando la storia. I conflitti concorrenti restituiscono un errore riprovabile senza dettagli SQL.

Dettagli UI: [integration/ASSOCIAGO-UI-VERIFICA.md](integration/ASSOCIAGO-UI-VERIFICA.md).

## Bandi: funzionalità sviluppate

- Mirror persistente del progetto comune, distinto dal catalogo dei progetti pubblici.
- Sezione **Progetti collegati**, dettaglio, riepiloghi, collegamenti e retry.
- Nel fascicolo, comandi per condividere o collegare un progetto esistente. Titolo, budget, profilo e revisione vengono letti dal server dal record autorizzato.
- Un progetto può comparire senza fascicolo: viene proposto un collegamento esplicito, senza inventare un bando.
- Accesso operatore esistente conservato; ritorno dopo login limitato a percorsi interni ammessi.
- API peer distinte dalle API utente; il proxy Next non inoltra chiavi/header peer del browser.
- Controlli di profilo `aps`/`cooperativa`, organizzazione, versioni e unicità. Nessuna modifica a revisioni, approvazioni, ricevute e documenti.

Dettagli: [Bandi/integration/BANDI-SUITE.md](../Bandi/integration/BANDI-SUITE.md).

## Cosa si collega e cosa rimane distinto

| Elemento | Comportamento |
| --- | --- |
| Identità progetto | Comune tramite UUID e corrispondenze persistenti |
| Passaggio fra siti | Progetto preciso, con il login previsto dal sito di destinazione |
| Titolo, descrizione e preventivo | Proiezione operativa dal registro, stato di allineamento visibile |
| Contabilità effettiva | Registrata in AssociaGo e condivisa come riepilogo |
| Persone e presenze | Gestite in AssociaGo, soltanto aggregati agli altri siti |
| Candidatura e revisioni | Gestite in Bandi, riepilogo nel contesto comune |
| Catalogo pubblico | Separato dai progetti propri dell'associazione |
| Budget, contributo e incasso | Restano valori diversi, senza movimenti contabili automatici |
| Utenti e ruoli | Sistemi esistenti conservati, nessun abbinamento automatico fra anagrafiche |
| Documenti e allegati | Restano nell'applicazione che li gestisce |

Il registro non replica tutte le tabelle e non introduce un login unico. La modifica del piano, della candidatura e delle registrazioni effettive segue il possesso dei dati indicato sopra.

## Sicurezza e affidabilità

Le chiavi peer esistono esclusivamente sui server: una per LibrePM↔AssociaGo, una diversa per LibrePM↔Bandi. Ogni richiesta include peer, chiave e organizzazione fissa. Confronto a tempo costante, rifiuto degli header duplicati e verifica delle corrispondenze proteggono gli scambi.

Gli endpoint provengono dalla configurazione server: HTTPS, con HTTP solo su loopback per i test; niente credenziali incorporate, query, frammenti o percorsi arbitrari. I client non seguono redirect, limitano le risposte e applicano un timeout anche al corpo. I peer Java usano HTTP/1.1: è stata risolta un'incompatibilità dell'upgrade HTTP/2 locale con Uvicorn.

Versioni vecchie o contenuti diversi nella stessa versione restituiscono 409. Il denaro viaggia come stringa decimale. AssociaGo rifiuta valute diverse da EUR e frazioni non rappresentabili in centesimi senza convertire/arrotondare. Un errore remoto non diventa un successo fittizio; i dati già ricevuti sono accompagnati dalla data dell'ultimo allineamento.

## Migrazioni e configurazione

| Repository | SQLite | Database cloud |
| --- | --- | --- |
| AssociaGo | V43 collegamenti/assegnazioni/progetto turni; V44 dettagli turni | MySQL V13; i dettagli dei turni sono già nella baseline |
| LibrePM | V901 registro comune | MySQL V3 registro comune |
| Bandi | Solo schema sintetico nei test | Alembic 0033_suite_projects, PostgreSQL |

Le migrazioni sono additive. Gli script storici pubblicati non sono stati riscritti. Sono preservati i backup prima dell'aggiornamento dei vecchi database desktop AssociaGo.

Modelli senza credenziali: [AssociaGo](.env.suite.example), [LibrePM](../LibrePM/.env.suite.example), [Bandi](../Bandi/integration/suite.env.example).

Servono lo stesso `SUITE_ORGANIZATION_ID` UUID nei tre backend, due chiavi casuali distinte di almeno 32 caratteri, origini backend/pubbliche corrette, `SUITE_ASSOCIATION_ID` reale, `SUITE_BANDI_PROFILE` corretto e un `SUITE_IMPORT_OWNER_ID` reale LibrePM per gli import senza identità Clerk collegata. La suite parte disattivata: `SUITE_ENABLED=false`.

I backend Java non caricano automaticamente questi file di esempio: le variabili vanno nel processo/servizio cloud. Le chiavi reali devono restare fuori da Git e dalle variabili `VITE_*`. L'eventuale `VITE_ASSOCIATION_ID` è pubblico e coerente con l'installazione.

Il job LibrePM funziona mentre il processo è in esecuzione. Un servizio scalato a zero non garantisce una cadenza periodica: restano allineamento manuale e retry. Una cadenza garantita richiederà un richiamo esterno autenticato o l'esecuzione continua.

## Come usare il flusso completo

1. Aprire un progetto LibrePM, entrare in **Integrazioni**, premere **Condividi questo progetto** e **Allinea applicazioni**. Si può partire anche dalla condivisione AssociaGo o dal fascicolo Bandi.
2. Verificare lo stato di ciascun sito; gli errori indicano dove riprovare.
3. Aprire AssociaGo dal pulsante, assegnare soci/volontari e registrare turni.
4. Con un ruolo amministrativo collegare i movimenti effettivi già registrati. Un preventivo non costituisce un incasso o una spesa.
5. Aprire Bandi dal pulsante e collegare il fascicolo esistente, se presente.
6. Riallineare e leggere rendiconto, ore e stato candidatura dagli altri siti.

Sui siti pubblici questi passaggi richiedono l'attivazione dell'ambiente reale. Le prove locali usano dati sintetici.

## Verifiche ed evidenze

Contratto: [OpenAPI](integration/suite-v1.openapi.json), [regole di dominio](integration/SUITE-V1-CONTRACT.md). Istruzioni riproducibili: [integration/README.md](integration/README.md). Copie identiche e manifest SHA-256 accompagnano gli altri due repository.

| Ambito | Esito locale |
| --- | --- |
| AssociaGo backend | Build Gradle completa: 201 prove, 199 passate, 2 MySQL saltate; nessun errore |
| AssociaGo frontend | 16 test, lint, build web ed Electron passati |
| AssociaGo browser | Progetto, persone, turni, movimento esistente, permessi, ID errato, suite disattivata e mobile 390 px verificati con fixture |
| LibrePM backend | 99 prove: 95 passate, 4 MySQL saltate; comprese 13 nuove prove suite |
| LibrePM frontend | 5 prove API, 5 navigazione, lint, build web/desktop e componente React reale in Chrome passati |
| Bandi backend | 36 prove API/autenticazione, Ruff e mypy del modulo suite passati |
| Bandi frontend | 43 prove, TypeScript, ESLint e build Next passati |
| Tre backend insieme | 46 verifiche HTTP finali passate, risposte conformi OpenAPI |

Verifica finale AssociaGo: `gradlew.bat build` passato, compresi migrazioni/backup, 57 casi del modulo suite e 9 nuovi casi di isolamento finanziario e concorrenza. Le fixture cloud sono state aggiornate con identità Clerk e iscrizione ACTIVE reali nel database di test, senza allentare i controlli. Il controllo del contratto verifica 14 operazioni, 57 fixture, 46 risposte HTTP e 2 copie distribuite; la verifica è stata aggiunta anche alla CI. La build produce avvisi Checkstyle di stile, senza errori bloccanti.

Il collaudo nativo avvia due Spring Boot e un FastAPI reali su loopback con porte e archivi temporanei, senza leggere i file .env delle app. Applica le migrazioni desktop Java; Bandi usa lo schema SQLite dei modelli per questa prova. Verifica idempotenza, snapshot, credenziali, organizzazione, profilo, versioni, valuta/precisione e indisponibilità di un peer senza bloccare l'altro. I processi figli vengono terminati entro quattro minuti. Le risposte senza chiavi/cookie sono in `integration/.artifacts/native-responses.json`, ignorato da Git.

Non sono certificati MySQL/PostgreSQL reali: le prove MySQL saltate non sono conteggiate come riuscite. Il browser con fixture prova l'interfaccia; il test HTTP prova i backend; nessuno sostituisce Clerk e dati reali sul servizio ripristinato.

In Bandi restano due problemi preesistenti esterni al modulo: mypy globale in `models.py:2392` (`FromClause`/`Table`); un test storico Alembic su SQLite fallisce nella vecchia migrazione PostgreSQL 0027 (`CREATE FUNCTION`), prima di arrivare alla nuova 0033 (49 passati, uno fallito nell'esecuzione ampliata). Non ho riscritto la storia delle migrazioni per quel test. Restano anche avvisi già presenti sulle dipendenze e sulla dimensione dei bundle.

## Pubblicazione Google e lavoro da fare insieme

AssociaGo usa `adf-gestionale-collaudo`, progetto Google `flawless-star-503422-q3`, regione `europe-west8`, e il frontend Vercel esistente. Il servizio chiamato `adf-bandi-collaudo` ospita LibrePM: non va confuso con la repository Python/Next Bandi. L'hosting della terza applicazione va identificato/configurato prima di dichiarare pubblicato il collegamento completo.

Il controllo precedente ha trovato il server Aiven non risolvibile e l'app in 503. Hai indicato una probabile sospensione per inattività e chiesto di affrontare il recupero insieme se non risolvibile autonomamente. Il risultato DNS non dimostra che i dati siano cancellati. Senza accesso Aiven disponibile non ho potuto riattivare il servizio o verificarne i backup.

Per completare la pubblicazione:

1. Verificare servizio/backup Aiven, ripristinare l'endpoint e controllare i dati.
2. Fare backup e prova delle migrazioni su copie MySQL/PostgreSQL.
3. Configurare i tre backend/frontend, conservando Clerk e i segreti esistenti.
4. Pubblicare revisioni candidate sui servizi esistenti, verificare login, permessi e un progetto condiviso.
5. Spostare il traffico sulla revisione verificata, conservando inizialmente la precedente per il ripristino. La nuova versione sostituisce quella in uso allo stesso indirizzo.

Non ho eseguito deploy, cambio traffico, commit o push in questa fase. Le modifiche sono nelle tre copie di lavoro locali. Procedura Google e provenienza del codice recuperato: [CLOUD-DEPLOYMENT.md](CLOUD-DEPLOYMENT.md). Correzioni del precedente audit generale: [AUDIT-TODO.md](AUDIT-TODO.md).


La pulizia di un vecchio archivio sintetico di collaudo è stata rifiutata dalla policy automatica, sia con rimozione ricorsiva sia indicando soltanto i file noti e le directory vuote; il messaggio disponibile è `blocked by policy`, senza altra motivazione. Resta `C:\Users\lange\AppData\Local\Temp\suite-native-smoke-u52mejaj`, circa 212 MB, senza processi attivi. Percorso e contenuto sono documentati in [integration/VERIFICA-SUITE.md](integration/VERIFICA-SUITE.md). Gli archivi degli ultimi collaudi sono stati rimossi correttamente.
