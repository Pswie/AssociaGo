# AssociaGo su PostgreSQL / Supabase

Il profilo `cloud-postgres` usa Supabase come database PostgreSQL. Il backend
Spring Boot continua a essere eseguito su Google Cloud Run; autenticazione,
ruoli e collegamento degli utenti restano gestiti da Clerk.

Questa configurazione inizializza un database applicativo **vuoto**. L'utente
ha confermato che Aiven non conteneva dati: non sono previsti esportazione,
recupero o trasferimento di record dal vecchio servizio.

## Attivazione esplicita

Impostare `SPRING_PROFILES_ACTIVE=cloud-postgres`. Il gruppo di profili definito
in `application.yaml` include automaticamente `cloud`: restano attivi JWT,
controlli dei ruoli, CORS e gli endpoint cloud. I bean desktop SQLite/MariaDB
restano esclusi. `cloud` da solo continua a usare MySQL; `desktop` continua a
usare SQLite. Il Dockerfile conserva il proprio valore predefinito `cloud`:
per PostgreSQL va sovrascritto nell'ambiente di esecuzione.

Configurazione necessaria, da fornire mediante segreti e variabili del servizio:

| Variabile | Contenuto |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `cloud-postgres` |
| `SPRING_DATASOURCE_URL` | JDBC PostgreSQL con TLS, database e host dal pannello Connect |
| `SPRING_DATASOURCE_USERNAME` | Ruolo applicativo, per esempio `associago_app` |
| `SPRING_DATASOURCE_PASSWORD` | Password del ruolo applicativo |
| `SPRING_FLYWAY_USER` | Ruolo proprietario dello schema, per esempio `associago_owner` |
| `SPRING_FLYWAY_PASSWORD` | Password del ruolo per le migrazioni |
| `SPRING_FLYWAY_URL` | Opzionale: URL JDBC dedicato alle migrazioni |
| `CLERK_ISSUER_URI` | Issuer dell'istanza Clerk già usata dal frontend |
| `CORS_ALLOWED_ORIGINS` | Origini esatte del frontend, separate da virgola |

Esempio di formato senza credenziali:

```text
jdbc:postgresql://HOST_DAL_PANNELLO:5432/postgres?sslmode=verify-full
```

Per `verify-full` va predisposta la catena CA del server nel client/contenitore.
Non disabilitare la verifica del certificato per correggere problemi di rete.
Le credenziali del database non vanno inviate al browser. Non serve una chiave
Supabase `service_role` per il collegamento JDBC.

## Schema e ruoli

Tabelle e storia Flyway sono nello schema privato `associago`, non in `public`
né in `auth`. La baseline qualifica esplicitamente i nomi delle tabelle;
Hibernate usa `hibernate.default_schema=associago`. La baseline revoca
l'accesso allo schema al ruolo PostgreSQL `PUBLIC`.

Prima dell'avvio predisporre lo schema con il suo proprietario e i privilegi:

- Il ruolo delle migrazioni deve poter creare e modificare oggetti solo nello
  schema applicativo e deve essere il proprietario degli oggetti creati.
- Il ruolo runtime deve avere `USAGE` sullo schema, operazioni sui dati delle
  tabelle e uso delle sequenze, senza `CREATE`, proprietà dello schema o
  privilegi amministrativi.
- I privilegi predefiniti del proprietario devono assegnare al runtime le
  operazioni sulle nuove tabelle e sequenze. La storia Flyway va poi esclusa
  dalle modifiche del ruolo runtime.
- Non esporre `associago` attraverso Data API/GraphQL. Se il progetto serve
  soltanto backend JDBC, disabilitare la Data API. Non concedere accesso alle
  tabelle a `anon` o `authenticated`.

L'autorizzazione degli utenti continua a essere applicata dal backend. Un JWT
Clerk non viene inoltrato automaticamente attraverso JDBC e non crea una
sessione Supabase Auth. L'adozione futura di accesso diretto dal browser
richiederebbe un progetto distinto di RLS e integrazione Clerk/Supabase.

## Baseline e compatibilità

`db/migration_postgresql/V1__baseline_schema.sql` consolida lo schema effettivo
ottenuto applicando MySQL V1–V13 a un MySQL 8.4 locale vuoto: 64 tabelle,
54 chiavi esterne e 56 indici non univoci, oltre ai vincoli univoci e ai check.
Include progetti, assegnazioni di persone, collegamenti suite, contabilità,
allegati e registri. Le relazioni mantengono le regole di cancellazione
originarie; le chiavi esterne sono differibili ma inizialmente immediate.

La nuova storia parte da PostgreSQL V1. Le migrazioni MySQL e SQLite esistenti
restano inalterate nei rispettivi percorsi. Non copiare la loro tabella
`flyway_schema_history`, non usare `repair` per far coincidere storie diverse
e non impostare `baseline-on-migrate=true` su uno schema popolato sconosciuto.
Le modifiche successive richiedono nuove migrazioni PostgreSQL.

Nel profilo PostgreSQL Hibernate esegue `validate` a ogni avvio: controlla le
mappature dopo Flyway e arresta il servizio in caso di schema incompatibile,
senza creare o modificare tabelle. Sono impostate anche le proprietà Hibernate
e JPA standard che nel profilo desktop disabilitano la generazione dello schema
e altrimenti prevalgono sulla sola `ddl-auto`.

PostgreSQL V2 conserva l'unicità del codice fiscale ignorando le maiuscole e
aggiunge un indice non univoco sulle email normalizzate. Non introduce vincoli
nuovi su email o slug: più persone possono condividere un contatto. Il primo
collegamento Clerk tramite email richiede una corrispondenza non ambigua;
contatti condivisi vanno collegati esplicitamente con l'identificativo Clerk.

Le conversioni preservano `BIGINT` per gli identificativi Java `Long`, valori
monetari esatti `numeric/decimal`, date e orari senza conversioni implicite di
fuso. Gli identificativi Clerk restano stringhe `user_...`; non vengono
trasformati in UUID di Supabase Auth. Gli identificativi suite restano stringhe
di 36 caratteri, come nel modello Java esistente.

Gli allegati, i loghi e le firme usano `bytea`. `Attachment.content` usa il
binding binario JDBC, senza `@Lob`/OID; MySQL conserva il proprio `LONGBLOB` e
SQLite il proprio contenuto binario. Non viene introdotto Supabase Storage.
La dimensione degli allegati rimane quindi parte dello spazio database.

Le stringhe usano il confronto PostgreSQL predefinito. Il precedente
`utf8mb4_0900_ai_ci` di MySQL ignorava maiuscole e accenti: le ricerche che
richiedono quel comportamento devono dichiararlo esplicitamente. I metodi
`IgnoreCase` continuano a farlo; non viene applicata una collation globale
che renderebbe incompatibili alcune ricerche PostgreSQL con `LIKE`.

## Collegamenti e backup

Preferire il collegamento diretto quando la rete supporta l'endpoint offerto,
oppure il Session pooler Supabase sulla porta indicata dal pannello Connect.
Il Session pooler supporta client JDBC persistenti e IPv4. Non ricostruire host
o nome utente dal solo nome della regione: copiarli dalla configurazione del
progetto. Per un eventuale Transaction pooler, verificare separatamente
prepared statement, lock Flyway e stato di sessione; JDBC può richiedere
`prepareThreshold=0`.

Hikari parte con massimo 3 connessioni e minimo 0. `DB_POOL_MAX` e `DB_POOL_MIN`
permettono la regolazione. Il totale va calcolato su tutte le istanze Cloud
Run, più migrazioni e altri applicativi; limitare anche le istanze del servizio.

La funzione di backup locale dell'applicazione resta riservata a SQLite.
PostgreSQL richiede backup gestiti dal fornitore o un dump logico con prova di
ripristino. Un backup del database include i file conservati in `bytea`.

## Verifiche

Prima del primo accesso operativo occorre registrare l'associazione e collegare
le persone reali: lo schema vuoto non contiene un amministratore inventato.
Gli endpoint desktop di setup restano chiusi nel cloud.

Un operatore autorizzato deve creare l'ente con i suoi dati reali, registrare
l'anagrafica dell'utente e la sua posizione nell'ente, e collegare
`users.clerk_user_id` al soggetto Clerk verificato della persona. I ruoli di
accesso al gestionale si assegnano nell'istanza Clerk; non vengono dedotti dal
primo ID, dall'email, né da un ruolo scritto in un'altra tabella. Prima di
creare il collegamento occorre verificare che appartenga alla persona giusta.
La password master locale non viene abilitata per completare questa procedura.

Finché manca il collegamento, `/api/v1/me` restituisce `404` e il frontend
segnala l'account non collegato. Finché manca l'ente, il servizio database può
essere sano mentre il gestionale attende la configurazione: non interpretare
questi stati come una migrazione di dati completata o come un guasto di Clerk.

`CloudPostgresSchemaIT` usa PostgreSQL 17 reale tramite Testcontainers oppure
un database di prova esplicitamente fornito. Il database esterno deve essere
dedicato al collaudo; lo schema `associago` viene inizializzato da Flyway.

```text
POSTGRES_TEST_URL=jdbc:postgresql://127.0.0.1:PORTA/DATABASE_DI_TEST
POSTGRES_TEST_USER=associago_app
POSTGRES_TEST_PASSWORD=<segreto locale>
POSTGRES_TEST_MIGRATION_USER=associago_owner
POSTGRES_TEST_MIGRATION_PASSWORD=<segreto locale>
```

Eseguire `./gradlew test --tests com.associago.cloud.CloudPostgresSchemaIT`.
Il test verifica Flyway, tutte le mappature Hibernate con `validate`, schema
privato, separazione runtime/DDL, allegati di 128 KiB con roundtrip `bytea`,
assenza di colonne OID e persistenza di identità Clerk, progetti, collegamenti
suite e importi decimali. Le righe di prova vengono annullate al termine dei
test transazionali. Verificare anche i test MySQL e SQLite prima del rilascio.

Il 30 ottobre 2026 `gradlew test bootJar` ha completato 232 test con zero errori
e nessun test saltato: PostgreSQL 17 reale con ruoli separati, MySQL 8.4 reale e
SQLite. Le prove includono roundtrip binari da 128 KiB su tutti e tre i motori,
la baseline PostgreSQL fino a V2, la validazione delle mappature e le regole
esistenti di sicurezza e integrazione. È stata prodotta la nuova JAR; questa
verifica locale non costituisce una pubblicazione o una connessione a Supabase.

Le prove aggiuntive rifiutano URL di collaudo PostgreSQL/MySQL non loopback
prima che Flyway possa scrivere. Il ramo Testcontainers prepara ruoli DDL e
runtime separati; la CI usa servizi database dedicati e rifiuta risultati con
test database saltati. Il modello senza credenziali è in
[.env.postgres.example](../.env.postgres.example).

Riferimenti ufficiali consultati:

- [Connessioni PostgreSQL Supabase](https://supabase.com/docs/guides/database/connecting-to-postgres)
- [Sicurezza della Data API](https://supabase.com/docs/guides/api/securing-your-api)
- [Clerk e Supabase](https://supabase.com/docs/guides/auth/third-party/clerk)
- [Modulo PostgreSQL di Flyway](https://documentation.red-gate.com/flyway/reference/database-driver-reference/postgresql-database)
- [Mapping LOB in Hibernate](https://docs.hibernate.org/orm/6.6/introduction/html_single/#lobs)
