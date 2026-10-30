# Preparazione PostgreSQL / Supabase

Data: 30 ottobre 2026. Conversione completata e migrazioni applicate sul progetto
Supabase Free **Angeli del futuro**, ref `tfeplskriazdybiampgg`. I tre backend
sono pubblicati su Google e i frontend su Vercel. Questo documento conserva
preparazione e verifiche tecniche; lo stato operativo, incluso il collaudo del
login comune, è in [PUBBLICAZIONE-SUITE.md](PUBBLICAZIONE-SUITE.md).

Gli archivi Aiven non contenevano dati da conservare. Sono stati inizializzati
nuovi schemi privati senza export, ETL o recupero dei backup Aiven.

## Destinazione

Un progetto Supabase dedicato all'associazione, con tre schemi privati:

| Schema | Applicazione | Migrazioni | Ruolo di esecuzione |
| --- | --- | --- | --- |
| associago | AssociaGo | Flyway PostgreSQL | associago_app |
| librepm | LibrePM | Flyway PostgreSQL | librepm_app |
| bandi_portal | Portale Bandi: bozze, coda e progetti comuni | Migrazioni portal separate | bandi_portal_app |

L'utente ha successivamente chiarito che il grande archivio Bandi deve restare
sul PC. La conversione completa nello schema `bandi`, con Alembic e pgvector,
rimane un'opzione collaudata ma non è la destinazione della pubblicazione
attuale. Il gateway online usa il solo schema leggero `bandi_portal` e un nodo
PC che si collega in uscita; vedere il resoconto aggiornato sopra.

I ruoli `*_owner` applicano le migrazioni; i ruoli `*_app` possono usare i dati
e le sequenze del proprio schema, senza possedere le tabelle. Le credenziali
devono essere distinte e rimanere nei backend/secret manager.

Le app conservano API, login e responsabilità precedenti. La sincronizzazione
dei progetti continua tramite il contratto della suite, anche quando le tre
applicazioni condividono la stessa infrastruttura PostgreSQL. Gli schemi non
vanno aggiunti agli schemi esposti dalla Data API Supabase.

Nel collegamento Supabase disponibile compare un progetto di un'altra attività.
Non è stato modificato, né è stato creato un progetto nuovo o sottoscritto un
piano a pagamento in questa fase.

## Lavori

- [x] Verificare PostgreSQL/Supabase, documentazione e disponibilità del progetto collegato.
- [x] Accertare che non occorre trasferire dati Aiven.
- [x] Preparare provisioning separato di schemi, ruoli, privilegi e pgvector.
- [x] Avviare PostgreSQL 17 locale isolato, con supervisore e scadenza automatica.
- [x] Completare e collaudare baseline PostgreSQL AssociaGo.
- [x] Completare e collaudare baseline PostgreSQL LibrePM.
- [x] Collaudare tutte le migrazioni Bandi nello schema privato.
- [x] Verificare allegati binari, importi, sequenze e isolamento dei ruoli.
- [x] Ripetere i controlli di regressione e lo scambio progetti fra app.
- [x] Documentare inizializzazione degli archivi vuoti e configurazione di rilascio.
- [ ] Creare/configurare il progetto Supabase e pubblicare i backend: fase successiva.

## File comuni

- `scripts/supabase/provision_portal.psql`: provisioning transazionale del nuovo progetto;
  rifiuta nomi già esistenti, un progetto con tabelle pubbliche preesistenti e
  password ripetute/corte, senza cambiare oggetti preesistenti.
- `scripts/supabase/finalize_portal.psql`: dopo le migrazioni, esclude le tabelle di
  storia dai privilegi runtime, protegge le revisioni append-only e verifica
  l'isolamento degli schemi. Le versioni senza `_portal` sono riservate alla
  variante opzionale con archivio Bandi interamente online.
- `scripts/supabase/verify_local_privileges.py`: prova 21 operazioni reali sul
  sandbox: dati e sequenze consentiti, DDL persistente, letture fra schemi e
  scritture nella storia migrazioni rifiutati; funzioni senza grant non eseguibili.
- `scripts/supabase/local_database.py`: database di collaudo temporaneo su loopback,
  supervisore indipendente e arresto automatico; credenziali e stato in `.artifacts/`,
  escluso da Git. Non usa i container o i database dell'utente.

Prima del provisioning si impostano sei password casuali diverse di almeno
32 caratteri nelle variabili `SUPABASE_ASSOCIAGO_OWNER_PASSWORD`,
`SUPABASE_ASSOCIAGO_APP_PASSWORD`, `SUPABASE_LIBREPM_OWNER_PASSWORD`,
`SUPABASE_LIBREPM_APP_PASSWORD`, `SUPABASE_BANDI_PORTAL_OWNER_PASSWORD`,
`SUPABASE_BANDI_PORTAL_APP_PASSWORD`. Lo script si esegue con `psql` e il ruolo
amministrativo del progetto dedicato. Non contiene credenziali reali.

## Inizializzazione

Un database vuoto non contiene automaticamente l'associazione, le iscrizioni,
gli utenti Clerk collegati e il proprietario LibrePM. Questi dati dovranno essere
inizializzati in modo esplicito prima di attivare la suite. Non vengono creati
utenti o credenziali amministrative fittizie in produzione.

Le impostazioni per il sito reale e il controllo periodico saranno configurati
nella fase di pubblicazione. Il piano Free Supabase prevede limiti di spazio e
possibile pausa per inattività; il controllo programmato non modifica il piano
o le condizioni del provider. Riferimento: [piani Supabase](https://supabase.com/pricing).

## Cosa è stato convertito

AssociaGo ha una baseline PostgreSQL separata, consolidata dallo schema MySQL
effettivo dopo V1–V13: **64 tabelle e 54 chiavi esterne**, seguita da V2 per
identità fiscali e ricerca delle email. Non viene introdotta un'unicità delle
email che il modello associativo non prevedeva. Le storie MySQL e SQLite
restano intatte. Gli allegati usano `bytea`, senza large object/OID. Il profilo
`cloud-postgres` include `cloud` e conserva Clerk, ruoli e CORS.

LibrePM ha una baseline PostgreSQL separata di **100 tabelle**, indici,
relazioni e metadati iniziali. Sono preservati anche identificativi testuali
come `wp-personal`. Allegati binari e descrizioni lunghe sono compatibili con
PostgreSQL. La verifica ha individuato e corretto anche la cancellazione
logica di note, progetti e attività versionati. Il nuovo profilo mantiene
il backend Google e l'autenticazione Clerk.

Bandi usa le proprie migrazioni PostgreSQL originali fino a **0033**, applicate
nello schema privato con tabella Alembic separata. La configurazione di schema,
estensioni, pool e credenziali del migrator è opzionale; l'installazione locale
precedente conserva il proprio comportamento. Gli indici vettoriali usano
pgvector nello schema `extensions`. Accesso operatore, fascicoli e revisioni
immutabili rimangono quelli esistenti.

## Configurazione successiva

1. Preparare un progetto Supabase dedicato e vuoto, in una regione adatta ai
   servizi Google. Non riutilizzare il progetto di un'altra attività.
2. Eseguire `provision_portal.psql` come amministratore del progetto, con sei password
   distinte tramite ambiente. Tenere gli schemi fuori dalla Data API.
3. Configurare AssociaGo e LibrePM con `SPRING_PROFILES_ACTIVE=cloud-postgres`:
   connessione runtime `*_app`, connessione Flyway `*_owner`, TLS verificato,
   Clerk e CORS già previsti. Java non carica automaticamente i file di esempio.
4. Per il portale Bandi usare `BANDI_MODE=gateway`, connessione runtime
   `bandi_portal_app` e migrator `bandi_portal_owner`. Applicare solo le migrazioni
   `apps/api/collaboration/migrations`; l'archivio locale non viene trasferito.
5. Eseguire le migrazioni come proprietari, quindi `finalize_portal.psql`. Non copiare
   le storie MySQL/SQLite in quelle PostgreSQL e non usare `repair` per
   adottare un archivio di un altro motore.
6. Inizializzare associazione e identità reali; poi configurare la suite con
   l'associazione e il proprietario effettivi, verificare login e un progetto
   condiviso e pubblicare le revisioni dei servizi esistenti.

Preferire la connessione diretta oppure il pooler in modalità sessione, usando
host, porta e nomi utente forniti dal progetto. Non usare il pooler in modalità
transazione per le migrazioni o il SQL storico di Bandi che richiede stato di
sessione. I pool sono piccoli e vanno dimensionati contando tutte le istanze.

Guide per applicazione e modelli senza credenziali:

- [AssociaGo](docs/SUPABASE-POSTGRES.md) e
  [.env.postgres.example](.env.postgres.example).
- [LibrePM](../LibrePM/SUPABASE-POSTGRES.md) e
  [.env.postgres.example](../LibrePM/.env.postgres.example).
- [Bandi](../Bandi/integration/SUPABASE-POSTGRES.md) e
  [supabase.env.example](../Bandi/integration/supabase.env.example).

## Verifiche

Il sandbox usa **PostgreSQL 17.10 e pgvector 0.8.5** reali in Docker, su una
porta loopback casuale. I container e i database già presenti sulla macchina
non sono stati usati per il collaudo. Non sono state eseguite ingestioni,
generazioni AI, scritture Supabase o modifiche ai servizi pubblicati.

AssociaGo ha superato **232 test su 232, senza test saltati**, con PostgreSQL
17, MySQL 8.4 e SQLite reali. Sono inclusi migrazioni e validazione completa
Hibernate, allegati binari da 128 KiB sui tre motori, identità, fiscalità,
sicurezza e progetti. La revisione ha aggiunto 23 prove sugli URL di collaudo:
prima di Flyway vengono rifiutati host remoti, URL multihost e override del
server mediante query. Anche il ramo Testcontainers prepara due ruoli veri;
la CI usa servizi PostgreSQL/MySQL e fallisce se le prove database sono saltate.
È stata prodotta la nuova JAR.

LibrePM ha superato **107 test su 107, senza test saltati**, inclusi sette casi
PostgreSQL, quattro MySQL e quello di compatibilità delle date. Sono state
verificate descrizioni Unicode oltre 64 KiB, allegati da 180000 byte, registro
condiviso, selezione, budget, cancellazione logica e univocità delle identità.

Bandi ha superato **66 test mirati su 66, senza test saltati** in una sola
esecuzione deduplicata: suite progetti, autenticazione, schema privato e query
RNA. Comprende tutte le migrazioni PostgreSQL reali, controllo Alembic,
pgvector, immutabilità e rifiuto di URL runtime/migrator appartenenti a due
progetti Supabase differenti, anche quando entrambi si chiamano `postgres`.

Il provisioning è stato provato su PostgreSQL: creazione effettiva dei ruoli,
schemi e privilegi; rifiuto di un target già esistente; rifiuto di un progetto
con tabelle preesistenti in `public`; rifiuto di password duplicate e
conservazione degli oggetti preesistenti. Le 21 prove di permessi sono passate.
È stata verificata anche la posizione di pgvector: uno schema incompatibile
viene rifiutato prima delle migrazioni applicative.

Il collaudo finale ha superato **19 verifiche sui tre backend reali collegati
allo stesso PostgreSQL**, con ruoli runtime e migrator distinti, veri filtri
JWT nei backend Java e issuer/JWKS sintetico locale. Comprende accesso negato
senza token, login operatore Bandi, condivisione e retry senza duplicati,
allineamento dei tre siti, rendiconto di 250 e ore svolte 2,5, oscuramento del
rendiconto al coordinatore e indisponibilità di Bandi con AssociaGo ancora
funzionante. Nove risposte sono validate contro il contratto OpenAPI comune;
le verifiche delle risposte del filtro di sicurezza controllano lo stato HTTP.

Script riproducibile: `scripts/supabase/run_postgres_smoke.py` (eseguire `--help`).
Richiede le JAR aggiornate e l'ambiente Python Bandi. Accetta esclusivamente il
sandbox Docker locale con la label del supervisore, crea un nuovo database
UUID e rimuove soltanto quello al termine. Deadline di quattro minuti,
terminazione dei processi figli e stato finale verificati. Le risposte
sintetiche senza credenziali sono in `integration/.artifacts/postgres-responses.json`
e lo stato in `postgres-responses.state.json`, esclusi da Git.

L'ultima esecuzione ha verificato anche il trigger Bandi con `EXECUTE` negato
al ruolo runtime: le revisioni continuano a essere protette. Il database UUID
del collaudo, i processi figli, il contenitore PostgreSQL temporaneo principale
e il suo volume sono stati rimossi. Il supervisore ha registrato lo stato
terminale `succeeded/stopped`. I container dell'utente sono rimasti operativi.

L'issuer locale prova la verifica dei token e l'autorizzazione del codice: il
login con l'istanza Clerk reale rimane un controllo della pubblicazione.
