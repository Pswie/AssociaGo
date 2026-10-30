# Collegamento tra LibrePM, AssociaGo e Bandi

Data: 29 ottobre 2026. Analisi iniziale, conservata come motivazione delle scelte.
L'implementazione è stata successivamente realizzata nelle tre repository:
vedere [LAVORI-INTEGRAZIONE.md](LAVORI-INTEGRAZIONE.md) per funzionalità,
collaudi e stato di pubblicazione. Le osservazioni al futuro nelle sezioni
seguenti descrivono la situazione prima dell'intervento.

## Risultato

È possibile scegliere un progetto e ritrovarlo nelle altre applicazioni. Oggi il
collegamento, al momento dell'analisi iniziale, non era implementato: mancavano un'identità comune del progetto, le
corrispondenze tra gli ID e il passaggio del contesto alle schermate.

La soluzione proposta mantiene le tre applicazioni con responsabilità distinte:
**Bandi prepara la candidatura, LibrePM organizza il lavoro, AssociaGo gestisce
persone e consuntivi**. Il progetto comune collega queste rappresentazioni;
non rende equivalenti ogni tabella, stato o importo.

Questa analisi non modifica codice applicativo, database, credenziali o servizi.
Non sono stati avviati processi di raccolta dati, generazione o sincronizzazione.
Non sono stati eseguiti collaudi autenticati delle applicazioni online.

## Repository effettivamente esaminate

| Repository | Versione locale | Funzione verificata |
| --- | --- | --- |
| LibrePM | `cloud/adf-bandi`, `bf1dd5947af659bcfde8552883e94456bd153c75` | Progetti operativi, task, pianificazione, team, risorse, budget, gestione candidature e obblighi |
| AssociaGo | `master`, `836333dc1dfa0d1f8569e41266bc7dabbc50c785` | Associazioni, soci, volontari, attività, documenti di spesa, movimenti e rendiconto dei progetti |
| Bandi | `agent/phase-4-structured-extraction`, commit `e106f8f`, un commit avanti a origin | ETS Intelligence Graph: ricerca e catalogo di fonti/progetti pubblici, idee, fascicoli progettuali e candidature |

**Attenzione ai nomi online:** il servizio `adf-bandi-collaudo` e il sito
`adf-bandi-collaudo.vercel.app` appartengono alla versione modificata di
**LibrePM**. Non sono la repository Python/Next.js chiamata `Bandi`.
Il portale `terzo-settore` mostra già due ingressi: “Bandi e progetti”
(LibrePM) e “Gestionale” (AssociaGo). I collegamenti aprono la radice delle app,
senza trasmettere il progetto selezionato.

Fonti: `LibrePM/DEPLOY.md:8`,
`LibrePM/MODIFICHE-ANGELI-DEL-FUTURO.md:3`,
`terzo-settore/sito/src/data/sezioni.ts:52` e `:64`.

## Tre significati diversi di progetto

### LibrePM: progetto da organizzare ed eseguire

`Project` ha un UUID, proprietario, team, visibilità `PERSONAL/TEAM/SHARED`,
task e membri. Esistono pianificazione, finanza e gestione bandi. La salute
`OK/WARNING/CRITICAL` riguarda l'andamento del lavoro, non l'approvazione di un
finanziamento. L'archiviazione è separata dalla salute.

Sono già presenti API dei progetti, membri, risorse e bandi. `GrantCall` può
contenere URL, ente erogatore, riferimento ufficiale, scadenza e importo.
JSON/CSV di progetto e feed calendario sono basi riutilizzabili per uno scambio
iniziale; non costituiscono un collegamento continuo con le altre app.

Fonti: `LibrePM/src/main/java/com/lorenzodm/librepm/core/entity/Project.java:33`,
`BaseSyncEntity.java:35`, `GrantCall.java:17`,
`api/controller/ProjectController.java:18`, `GrantsController.java:20`,
`ImportExportController.java:19`.

### AssociaGo: progetto dell'associazione e rendiconto

`Project` usa un ID numerico e appartiene a `associationId`. Contiene nome,
stato, area, referente, fonte di finanziamento, URL dell'opportunità, budget e
scadenza. Gli stati sono `proposto`, `attivo`, `da_completare`, `completato`.
Le API sotto `/api/v1/projects` gestiscono anche il rendiconto.

Movimenti e documenti di spesa hanno già `projectId`: questo rende praticabile
la consultazione dei costi effettivi del progetto nelle altre applicazioni.
Manca però la schermata Progetti e il relativo modulo nel client API del
renderer. Il nome `projects` nella matrice dei ruoli non crea una pagina.

I turni dei volontari collegano volontario, attività ed evento, ma non il
progetto. Per attribuire ore e partecipazioni a un progetto bisogna aggiungere
una relazione esplicita, direttamente o attraverso attività/eventi collegati.

Fonti: `AssociaGo/src/main/java/com/associago/project/Project.java:27`,
`ProjectController.java:25`, `ProjectService.java:88`,
`finance/dto/TransactionCreateDTO.java:19`, `expense/ExpenseDocument.java:72`,
`volunteer/VolunteerShift.java:10`,
`desktop/src/renderer/src/App.jsx:241`, `desktop/src/renderer/src/api.js`.

### Bandi: distinguere catalogo pubblico e fascicolo proprio

Il `Project` del catalogo descrive progetti acquisiti da fonti pubbliche:
UUID, provenienza, riferimento della fonte, eventuale CUP, beneficiario,
territorio e importi documentati. Una scheda OpenCoesione può essere un esempio
o una fonte di ricerca; non diventa per questo un progetto dell'associazione.
Le API impediscono di modificare liberamente i record gestiti dalle fonti.

Per collegare un progetto da realizzare, l'oggetto utile è normalmente
`ProjectPreparation`, oppure l'eventuale `ApplicationWorkspace` già in uso.
Il fascicolo ha UUID, profilo `aps`/`cooperativa`, bando o fonte di destinazione,
brief, versione, revisione e ricevute. Esistono anche `ProjectIdea` collegate
alla preparazione. Il brief contiene titolo, obiettivo, durata, budget totale
e contributo richiesto.

La rotta `/applications/prepare/<UUID>` apre già un fascicolo specifico.
L'endpoint `/api/v1/project-preparations/{id}/package` esporta PDF, JSON,
Markdown e CSV budget con manifest, versione e checksum: è una buona base per
un import esplicito, ma manca ancora la ricevuta del collegamento esterno.

Fonti: `Bandi/apps/api/models.py:994`, `domain_service.py:118`,
`project_catalog_schemas.py:96`,
`applications/preparation_models.py:28`, `ideation_models.py:67`,
`applications/models.py:25`, `applications/schemas.py:77`,
`applications/preparation_router.py:183`,
`apps/web/src/lib/project-ideas-client.ts:37`.

## Dove il collegamento è utile e possibile

| Collegamento | Proposta | Modifiche necessarie |
| --- | --- | --- |
| Stesso progetto visibile nelle tre app | Un identificativo comune con mappa degli ID locali e pulsanti “Apri in…” | Registro, API di risoluzione e gestione del contesto nei frontend |
| Bandi → LibrePM | Importare un fascicolo scelto come progetto operativo; collegare il bando a `GrantCall` | Import con conferma, versione di origine, idempotenza e ricevuta |
| Bandi → AssociaGo | Collegare anagrafica, provenienza e riferimento del fascicolo | Creazione/associazione del progetto locale; schermata Progetti |
| LibrePM → AssociaGo | Collegare attività operative a attività/eventi dell'associazione | Mappa task/work package ↔ attività; regole sulle date e sui responsabili |
| AssociaGo → LibrePM | Mostrare persone assegnabili, disponibilità, ore svolte e costi effettivi | Mappa delle persone, collegamento dei turni al progetto, viste autorizzate |
| AssociaGo → Bandi | Fornire dati verificati dell'ente e riepiloghi per progettazione/rendicontazione | Snapshot datato con provenienza, separato dagli scenari ipotetici |
| Documenti e scadenze | Riferimenti ai documenti autorevoli e scadenze tipizzate | Download autenticato, permessi e distinzione tra scadenza bando, progetto e rendicontazione |

Per il primo collegamento non serve spostare tutti i dati: LibrePM può mostrare
un riepilogo delle spese mantenute in AssociaGo e aprirne il dettaglio. Bandi
può usare conteggi e capacità aggregate senza ricevere tutta l'anagrafica soci.

## Cosa non si può collegare direttamente

| Apparente equivalenza | Perché non vale | Trattamento corretto |
| --- | --- | --- |
| Stesso titolo = stesso progetto | Titoli duplicati, rinominati o derivati da modelli | Collegamento esplicito con ID |
| ID `12` = stesso record | Ogni database assegna ID indipendenti; due app usano UUID | ID locali conservati e mappati |
| CUP = identificativo comune universale | Non esiste nei progetti nuovi e non riguarda tutti i progetti | Riferimento opzionale verificato |
| Progetto pubblico Bandi = progetto proprio | La scheda documenta un fatto/fonte esterna | Collegamento come riferimento, senza alterare i dati della fonte |
| Bozza pronta = progetto approvato o concluso | `ready_for_review` descrive la preparazione della bozza | Stati distinti per candidatura, esecuzione e contabilità |
| Budget richiesto = contributo ottenuto = incasso | Previsione, concessione e pagamento sono fatti differenti | Importi distinti, valuta e provenienza |
| Ore pianificate = presenza verificata | Una pianificazione non prova lavoro svolto | Consuntivo separato e riconciliabile |
| Socio = volontario = utente autenticato | Iscrizione, ruolo operativo e accesso sono concetti distinti | Mappature e permessi espliciti |
| Profilo Bandi `aps` = associazione identificata | È una tipologia/profilo, non un tenant | Associazione reale collegata separatamente |
| Stesso Clerk o stesso server MySQL = dati sincronizzati | Identità, database e modelli restano separati | Contratto e connettori applicativi |

Non è emerso un impedimento tecnico assoluto a collegare le app. Gli ostacoli
sono la semantica dei dati, i collegamenti mancanti e i confini dei permessi.
La replica completa in entrambe le direzioni creerebbe invece più autorità
per gli stessi dati e conflitti difficili da ricostruire.

## Esperienza proposta per l'utente

1. Scegli “Progetto X” da un elenco comune o da una delle app.
2. In LibrePM trovi piano, attività, responsabili e budget operativo.
3. Con “Apri in AssociaGo” trovi lo stesso progetto e le sue persone,
   presenze e spese, secondo i tuoi permessi.
4. Con “Apri fascicolo in Bandi” ritrovi candidatura, documenti, requisiti e
   versione da cui è nato il lavoro.
5. Se una rappresentazione non esiste, l'app propone “Collega esistente” o
   “Crea collegamento”: non crea copie solo perché hai aperto un URL.

Passare il progetto durante la navigazione e aggiornare automaticamente altre
schede già aperte sono due lavori diversi. Il primo richiede un ID nell'URL e
la sua risoluzione. Il secondo richiede anche un contesto salvato sul server
e notifiche o controlli periodici. `localStorage` e `BroadcastChannel` non
condividono il contesto tra le diverse origini delle app.

## Architettura minima proposta

### Identità del progetto e collegamenti

Introdurre un registro contenente un UUID comune e i riferimenti locali:

```text
Progetto comune
  organizationId della suite
  suiteProjectId UUID
  ├── LibrePM: project UUID
  ├── AssociaGo: project ID numerico
  └── Bandi: preparation UUID e, se usato, application workspace UUID

Riferimenti separati, anche multipli
  bandi, fonti, progetti pubblici di esempio, documenti e revisioni
```

Una candidatura può riferirsi a più fonti e un bando può finanziare più progetti:
non basta una colonna generica `bandi_id` con una corrispondenza uno a uno.
Gli ID locali vanno scambiati come identificativi opachi, senza conversioni
arbitrarie tra UUID e numeri.

Proposta di collocazione: piccolo modulo nel backend LibrePM, che possiede già
il progetto operativo e i suoi permessi; il portale esistente può fornire il
selettore. Non serve una quarta applicazione completa. È una proposta, non una
decisione già applicata; il registro deve introdurre l'organizzazione comune,
oggi assente dal modello `Project` LibrePM.

### Contratto e responsabilità dei dati

Prima dei connettori, definire un unico contratto OpenAPI versionato per
risolvere il progetto e registrarne i collegamenti. Il contratto deve
distinguere identificativi, campi facoltativi, tipi di relazione, errori,
versioni e permessi. Un minimo concettuale è:

```json
{
  "schemaVersion": 1,
  "suiteProjectId": "UUID comune",
  "organizationId": "UUID dell'organizzazione",
  "title": "Progetto X",
  "instances": [
    { "system": "librepm", "entity": "project", "localId": "UUID" },
    { "system": "associago", "entity": "project", "localId": "42" },
    { "system": "bandi", "entity": "preparation", "localId": "UUID" }
  ]
}
```

L'esempio è illustrativo, non un endpoint esistente né un payload da inviare.
Le destinazioni si costruiscono da app consentite; i token di accesso non
viaggiano negli URL. Ogni backend risolve il collegamento e verifica accesso
al progetto e all'organizzazione: conoscere l'UUID non concede autorizzazione.

| Informazione autorevole | Applicazione proposta |
| --- | --- |
| Fonti ufficiali, opportunità e versione della candidatura | Bandi |
| Piano operativo, task, dipendenze, capacità assegnata e previsione | LibrePM |
| Anagrafica associativa, presenze verificate, documenti di spesa e pagamenti | AssociaGo |
| Corrispondenze tra rappresentazioni e preferenza di selezione | Registro comune |

Le modifiche dalla candidatura al piano inizializzano o propongono una nuova
versione; non sovrascrivono a ogni salvataggio task già riorganizzati. I
consuntivi alimentano viste/righe collegate, senza trasformarsi in nuove spese
quando un invio viene ripetuto. Conservare `sourceSystem`, ID della fonte,
versione, data di aggiornamento e chiave di idempotenza. Gli importi devono
avere decimale e valuta espliciti: LibrePM usa attualmente anche `Double`,
AssociaGo `BigDecimal`.

Per i flussi continui servono modifiche registrate in modo persistente,
tentativi ripetibili e riconciliazione. Non richiedere che tutte le app siano
online per salvare qualunque modifica; mostrare aggiornamenti pendenti, errori
e ultimo allineamento. Archiviazioni e cancellazioni richiedono regole proprie:
non propagare cancellazioni automatiche a movimenti contabili o revisioni.

### Accesso comune

LibrePM e AssociaGo hanno già supporto Clerk satellite. I loro utenti interni
hanno comunque ID diversi e regole differenti di collegamento. Bandi usa
invece un cookie firmato per un operatore configurato e non ha il medesimo
confine utenti/ruoli/organizzazioni. Per accesso comune completo va aggiunto
un adapter o supporto JWT e l'autorizzazione delle singole risorse.

Non condividere segreti/cookie tra applicazioni. Nemmeno `X-Association-Id`
o un ruolo mostrato nel menu sostituiscono una verifica del backend. Prima di
includere progetti privati tra utenti o enti diversi bisogna completare i
controlli di appartenenza: oggi LibrePM autorizza alcune famiglie di API per
ruolo e AssociaGo seleziona la prima associazione restituita.

Fonti: `LibrePM/desktop/src/main.web.jsx:39`,
`LibrePM/src/main/java/com/lorenzodm/librepm/security/CloudSecurityConfig.java:58`,
`AssociaGo/desktop/src/renderer/src/main.jsx:35`,
`AssociaGo/desktop/src/renderer/src/components/WebAuthGate.jsx:79`,
`Bandi/apps/api/routes/auth.py:42`,
`Bandi/apps/web/src/lib/session-cookie.ts:1`, `Bandi/apps/api/main.py:127`.

## Funzioni esistenti da non scambiare per sincronizzazione comune

- LibrePM `SyncService.applyMerge` registra audit/conflitti, ma non applica
  una replica tra queste app. Inoltre `/api/sync/**` è negato nel cloud.
  Non basta attivare quel percorso.
- La sincronizzazione quotidiana di Bandi acquisisce fonti pubbliche. Non
  aggiorna LibrePM e AssociaGo.
- Il portale `terzo-settore` legge anche un vecchio mirror Supabase di
  `progetti`, `movimenti`, `persone` e `presenze`. Il writer è la vecchia
  dashboard Python/SQLite `ets_scout`, non i due backend Java. Lo script
  cancella e ricopia le righe del mirror: scriverci il nuovo registro lo
  renderebbe esposto a sovrascritture.

Se la vecchia dashboard contiene ancora lavoro reale, occorre censire e
collegare quei progetti durante la migrazione. Non è stata letta né modificata
la sua base dati per stabilirlo.

Fonti: `LibrePM/src/main/java/com/lorenzodm/librepm/service/impl/SyncServiceImpl.java:51`,
`security/CloudSecurityConfig.java:147`,
`terzo-settore/supabase/README.md:3` e `:65`,
`terzo-settore/scripts/sync_supabase.py:214`,
`terzo-settore/sito/src/pages/api/admin/gestionale.ts:60`.

## Ordine di sviluppo e criteri di verifica

- [x] Confrontare modelli, identificativi, API, accesso e navigazione delle tre repository.
- [x] Distinguere LibrePM online dalla repository Bandi e individuare il vecchio mirror.
- [ ] Scegliere un solo progetto pilota reale e collegare esplicitamente i suoi record; non abbinare automaticamente l'intero patrimonio dati per titolo.
- [ ] Versionare il contratto comune e aggiungere il registro con vincoli sulle corrispondenze e sull'organizzazione.
- [ ] Implementare selettore e “Apri in…” in LibrePM/AssociaGo, usando le rotte del fascicolo in Bandi; aggiungere schermata/API client Progetti ad AssociaGo.
- [ ] Completare autenticazione/permessi Bandi per il modo di accesso scelto e verificare appartenenza alle risorse in tutte le app.
- [ ] Importare il fascicolo Bandi scelto in modo idempotente, conservando versione e ricevuta; non riaprire/aggirare le verifiche di revisione e consegna già esistenti.
- [ ] Collegare persone, attività/turni e movimenti con responsabilità per campo; iniziare da viste in sola lettura e aggiornamenti in una direzione.
- [ ] Aggiungere sincronizzazione incrementale, errori visibili, nuovi tentativi e riconciliazione; solo dopo valutare aggiornamenti live tra schede già aperte.

Il primo rilascio utile è verificabile così: scelgo il progetto una volta,
apro le altre due app e ritrovo le rappresentazioni collegate; un secondo
tentativo non crea duplicati; un account non autorizzato non apre il progetto;
una spesa mantenuta in AssociaGo compare nel riepilogo LibrePM senza diventare
una seconda registrazione contabile; un'app non raggiungibile mostra un errore
o un aggiornamento pendente senza corrompere il lavoro nelle altre.

I documenti di rilascio di LibrePM e AssociaGo riportano, nell'ultima verifica
del 29 ottobre, il blocco Aiven/DNS e risposte 503. Non è stata ripetuta una
misurazione live in questa analisi. Si può progettare e sviluppare localmente;
il collaudo cloud richiede prima il ripristino del database, già concordato
con l'utente. Condividere il servizio Aiven non significa condividere lo stesso
database: LibrePM usa `librepm`, separato da quello del gestionale.
