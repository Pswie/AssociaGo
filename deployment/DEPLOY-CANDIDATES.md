# Migrazioni iniziali e revisioni candidate

Questi strumenti separano i privilegi di migrazione dai servizi che rispondono
agli utenti. Le impostazioni pubbliche, gli issuer, le origini e i digest delle
immagini arrivano da `suite-public-settings.json`. I dati privati restano fuori
dal repository, oppure nelle directory ignorate `.private`/`.artifacts`.
Non conservare password nei comandi o nei documenti di rilascio.

Usare Python dell'ambiente Bandi, JDK21, Docker e Google Cloud SDK già installati.
Le immagini preparate devono essere disponibili localmente per il migratore.
Nessuno degli strumenti attiva Supabase a pagamento o modifica il piano.

## File privati e connessione verificata

`prepare_runtime_bundles.py` richiede il nome host **esatto** del Session Pooler
del progetto dal pannello Supabase. Non deduce un indice AWS dal nome della
regione e non usa il Transaction Pooler sulla porta 6543.

```text
python deployment/prepare_runtime_bundles.py --roles PRIVATE/roles.json --integration-keys PRIVATE/integration-keys.json --session-pooler-host HOST_VERIFICATO --output PRIVATE/runtime-initial
```

La directory nuova riceve tre JSON runtime, uno per applicazione, e un diverso
`owner-migrations.private.json` destinato esclusivamente al migratore sul PC.
Le credenziali owner non entrano nei bundle runtime. Le directory sono protette
con ACL per l'utente corrente/SYSTEM su Windows e permessi 0700 su POSIX.

TLS è `verify-full`: JDBC usa `org.postgresql.ssl.DefaultJavaSSLFactory`,
quindi la trust store del JDK; psycopg usa `sslrootcert=system`. Il gateway
riceve anche il percorso della CA bundle presente nella sua immagine Debian.
La CA provider in `certs/supabase-root-2021.crt` viene verificata con il pin
file SHA256 `700723581420dd1ac98fd7e9ac529f0ef210eadcaf87fc868a3ad7d114c2f3b7`.
Le immagini runtime preparate importano questa CA mantenendo la verifica del
nome host. Sul PC il migratore crea una JKS temporanea con il solo certificato
provider e la applica soltanto al processo Java; non modifica Windows o il JDK.
La password della JKS passa tramite ambiente/file protetto, mai negli argomenti.
Il processo gateway di migrazione monta la CA in sola lettura, senza cambiare
il bundle runtime o disabilitare alcun controllo TLS.
La validazione rifiuta progetto/ruolo/schema diversi, porta transaction-pooler
e parametri di connessione che potrebbero sostituire host o credenziali.

## Primo schema, poi identità reale

Il comando seguente **valida soltanto**, senza connettersi al database:

```text
python deployment/migrate_owner_once.py --config PRIVATE/runtime-initial/owner-migrations.private.json --ca-file deployment/certs/supabase-root-2021.crt --ca-sha256 700723581420dd1ac98fd7e9ac529f0ef210eadcaf87fc868a3ad7d114c2f3b7 --receipt deployment/.artifacts/owner-validation.json
```

Solo aggiungendo `--apply` si applicano le migrazioni. Gli SQL e le librerie
Flyway vengono estratti dalle immagini Java identificate dal digest pubblico.
`OwnerMigration.java` esegue soltanto l'API Flyway e termina: nessun server Spring,
bootstrap business o account fittizio. Il gateway usa il proprio modulo di
migrazione, dalla sua immagine leggera. Non viene esposto alcun listener HTTP.
Le credenziali passano tramite file temporanei protetti, non negli argomenti;
stdout/stderr dei sottoprocessi non vengono pubblicati. Il receipt contiene
soltanto schemi, digest e conteggi. Un errore interrompe la sequenza; leggere
lo stato del database prima di ripetere un rilascio parziale.

`prepare_initial_bootstrap.py` prepara l'identità dell'amministratore soltanto
dal risultato privato del lookup Clerk: deve combaciare con `ownerSubject`,
avere nome/cognome reali ed email primaria verificata. Non stampa questi dati.

```text
python deployment/prepare_initial_bootstrap.py --identity PRIVATE/clerk-bootstrap-result.json --output PRIVATE/initial-bootstrap
```

Il file `owner-bootstrap.env` viene letto dal processo psql amministrativo
tramite ambiente, senza valori personali sulla linea di comando. Eseguire
`initial_owner_bootstrap.psql` soltanto dopo le migrazioni, usando un ruolo
amministrativo autorizzato su entrambi gli schemi. La transazione richiede
tabelle identità vuote e si rifiuta di sovrascrivere utenti già esistenti.
Restituisce soltanto gli ID da usare nella configurazione runtime.

Se l'amministrazione avviene tramite MCP, `render_initial_bootstrap.py` traduce
gli stessi parametri in un file SQL privato con un blocco `DO`, senza comandi
psql, tabelle temporanee o rigenerazione degli UUID applicativi. Usa
`--identity`, `--parameters PRIVATE/initial-bootstrap/parameters.private.json`
e `--output PRIVATE/initial-bootstrap/prepared-bootstrap.sql`.
Il file viene creato in modo esclusivo, con un manifest SHA256, e non viene
stampato. Il suo ultimo risultato contiene soltanto i tre ID del bootstrap.

Si crea una sola associazione con nome/email pubblici confermati dal sorgente
ufficiale `terzo-settore/sito/src/data/site.ts`, il profilo Clerk effettivo in
AssociaGo e LibrePM e un accesso tecnico `ADMIN`. Non si attribuisce la carica
legale di presidente; le cariche, i codici fiscali, le quote, i numeri RUNTS,
i soci, i progetti e i movimenti non vengono inventati. Il flag consiglio
mantiene il valore applicativo iniziale `false`, da verificare nell'anagrafica.
L'hash richiesto da LibrePM è un valore casuale inutilizzabile: l'autenticazione
cloud resta esclusivamente Clerk. Le proprietà/ruoli Clerk non sono modificati.

Infine l'amministratore applica `scripts/supabase/finalize_portal.psql` e verifica
i ruoli runtime: niente DDL, accesso agli altri schemi o cronologie di migrazione;
le revisioni del portale restano append-only. Il codice runtime usa soltanto
`*_app`. Questa verifica precede qualsiasi attivazione dei nuovi backend.

## Tre bundle e tre identità runtime

Copiare `runtime-candidates.example.json` in una directory privata e sostituire
UUID tecnici, ID reali del bootstrap, origine gateway verificata, identificativo
PC e percorsi dei tre file runtime. Le origini API dei peer devono puntare ai
partner del rilascio che si sta collaudando: un candidato a traffico zero non
riceve automaticamente le chiamate inviate all'URL stabile della revisione vecchia.
L'elenco CORS e l'issuer provengono sempre dalle impostazioni pubbliche condivise.

```text
python deployment/deploy_runtime_candidates.py --config PRIVATE/runtime-candidates.json --output deployment/.artifacts/pg-plan
```

Senza `--execute` il comando produce soltanto il piano e tre file di variabili
pubbliche. Non legge/upload i valori dei bundle e non effettua chiamate Google.
Il piano contiene riferimenti Secret Manager con versione numerica esplicita.

Le tre fasi remote sono distinte e richiedono un output nuovo per ciascuna:

```text
python deployment/deploy_runtime_candidates.py --config PRIVATE/runtime-candidates.json --output deployment/.artifacts/pg-secrets --execute secrets
python deployment/deploy_runtime_candidates.py --config PRIVATE/runtime-candidates.json --output deployment/.artifacts/pg-iam --execute iam
python deployment/deploy_runtime_candidates.py --config PRIVATE/runtime-candidates.json --output deployment/.artifacts/pg-candidates --execute runtime
```

La fase `secrets` crea **tre** segreti, con i valori su stdin. Rifiuta nomi già
esistenti, senza aggiornare versioni o vecchi servizi. Per Java ogni bundle è
una flatmap JSON di sole chiavi sensibili uppercase, montata come
`SPRING_APPLICATION_JSON`; per il gateway il JSON viene convertito in memoria
in un file dotenv montato read-only, letto da `uvicorn --env-file`.
L'allowlist esclude ogni owner/migration setting e verifica che le chiavi peer
coincidano nei due bundle interessati. Tre versioni iniziali consentono un
rollback con una seconda versione per applicazione senza moltiplicare i segreti.
Questo conteggio non certifica l'uso della quota gratuita dell'intero progetto.

La fase `iam` usa tre service account distinti e assegna a ciascuno soltanto
`secretAccessor` sul proprio bundle. La fase `runtime` sostituisce l'intero
insieme env/secret della revisione, con Flyway disabilitato e senza owner.
Java usa pool massimo3, il gateway2; ogni servizio/revisione ha massimo1 istanza,
minimo0. Le revisioni di servizi esistenti sono taggate `pg-candidate` a traffico
zero e non cambiano la policy IAM o instradano utenti alla nuova revisione.

Il primo servizio gateway non ha una vecchia revisione: `initialServices` e
`--allow-initial-service` devono essere entrambi espliciti, e il controllo Google
deve confermare che il servizio non esiste. L'URL nuovo è raggiungibile, ma
l'applicazione richiede JWT/header nodo/cron; nessun sito viene reindirizzato
dal piano. Un servizio esistente non può essere marcato erroneamente come nuovo.

Prima della sua creazione è ammesso omettere `backendOrigins.bandiPortal`:
non viene inventato un indirizzo. Dopo le fasi `secrets` e `iam`, distribuire
soltanto il gateway con `--execute runtime --application bandiPortal
--allow-initial-service`. Leggere quindi l'URI effettivo del servizio Google,
registrarlo nelle impostazioni, impostare `initialServices.bandiPortal=false`
e preparare i candidati Java usando l'URI verificato. Il runtime Java viene
rifiutato se quel collegamento non è ancora disponibile. Per collaudare peer
candidati a traffico zero usare i loro URI tag verificati, senza cambiare gli
indirizzi stabili prima della decisione di promozione.

Promozione del traffico, aggiornamento frontend, verifica Clerk reale e rimozione
delle vecchie revisioni sono passaggi successivi gestiti dal responsabile del
rilascio. Lo strumento non li esegue né elimina automaticamente risorse vecchie.

## Controllo dopo la promozione e inventario delle copie precedenti

`check_runtime_release.py` legge soltanto i metadati Cloud Run. Il receipt contiene
traffico, revisioni, immagini, service account, **nomi** delle variabili e
riferimenti Secret Manager con versione. Non salva gli oggetti Google grezzi:
anche una revisione precedente potrebbe contenere password in chiaro.
L'output deve avere un percorso nuovo, per conservare le evidenze precedenti.

```text
python deployment/check_runtime_release.py --config PRIVATE/release-config.private.json --output deployment/.artifacts/release-verification-UNIQUE.json --http
```

Con `--http` esegue solo GET sugli URL stabili verificati: health, rifiuto anonimo,
chiave peer assente/errata, organizzazione diversa e progetto UUID inesistente.
Il 404 deve avere il codice applicativo della ricerca nel database; un 404
generico del proxy non passa. Non richiama cron/maintenance, perché quella GET
aggiorna il timestamp. Non crea progetti, bozze, eventi o sessioni Clerk.
Senza `--http` non legge i valori dei bundle. Nessuna modalità modifica risorse.

Il primo gateway può omettere il proprio `SUITE_BANDI_API_URL`, che non utilizza:
il receipt lo segnala esplicitamente. L'omissione non è ammessa per i peer Java.
Un `value` mancante per una variabile vuota è la rappresentazione normale Google.

Prima di eliminare vecchie revisioni, il responsabile del rilascio deve verificare
il frontend effettivo e rileggere traffico/tag. Una revisione con traffico o un tag
va conservata. Eliminare soltanto nomi esatti approvati, nel progetto e nella
regione espliciti; non usare filtri di cancellazione per prefisso.

```text
gcloud run revisions delete REVISIONE_APPROVATA --project=flawless-star-503422-q3 --region=europe-west8
```

L'inventario immagini richiede un controllo globale dei servizi e dei job di
tutte le regioni, includendo anche le loro revisioni. Per questa release il
receipt privato `live-cleanup-inventory-20261030.json` contiene quell'elenco e
limita le immagini ai tre repository esatti della suite. Prima di cancellare
occorre aggiornare l'inventario: risorse nuove possono introdurre riferimenti.

Il digest pubblico di un'immagine può identificare un indice OCI, mentre Cloud
Run espone il digest del manifest della piattaforma. Conservare l'indice e tutti
i suoi manifest raggiungibili, comprese le attestazioni; i digest diversi non
sono automaticamente copie obsolete. L'inventario della release conserva nove
digest per i tre indici attivi. Le immagini ancora taggate richiedono una
decisione esplicita anche quando nessuna revisione le usa.

```text
gcloud artifacts docker images delete REPOSITORY_ESATTO@sha256:DIGEST_APPROVATO --project=flawless-star-503422-q3
```

Non usare prune dell'intero registro né `--delete-tags` in blocco. Il checker
non elimina immagini/revisioni e non promette rollback automatico; dopo la
rimozione della vecchia copia si torna indietro ridistribuendo un'immagine
conservata compatibile con lo schema corrente.

## Evidenza disponibile

I guard offline sono in `test_runtime_preparation.py`. Il migratore è stato
provato con le immagini reali su PostgreSQL17 temporaneo: due migrazioni
AssociaGo, quattro LibrePM e schema portale separato, senza utenti/progetti
creati; il finalizer originale è passato. Il bootstrap è stato provato in una
transazione locale: una sola identità reale, un solo accesso amministrativo,
zero progetti; una seconda esecuzione è stata respinta senza sovrascritture.

`verify_java_runtime_bundles.py` verifica su JAR reali che la flatmap JSON
uppercase fornisca datasource e chiavi peer senza variabili duplicate, che il
runtime usi soltanto i ruoli `*_app` con Flyway disabilitato e che le cronologie
non cambino. I dati/issuer di quel collaudo sono sintetici e non certificano
una sessione Clerk reale o la connettività TLS al nuovo progetto remoto.
