# Backup dei tre schemi cloud dal PC

`backup_cloud_schemas.py` salva `associago`, `librepm` e `bandi_portal` in un archivio PostgreSQL custom. L'archivio completo di Bandi, i documenti e i modelli rimasti sul PC richiedono un backup separato: non sono compresi in questi tre schemi.

Le sole tabelle cache `bandi_portal.catalog_snapshots`, `catalog_chunks` e `catalog_active` conservano lo schema ma escludono i dati: il manifest elenca `cacheExcludedData` e richiede la rigenerazione. Dopo un ripristino tutte e tre sono vuote, incluso il puntatore attivo; prima di usare l'archivio online occorre ripubblicare uno snapshot verificato dall'export locale conservato. Bozze, revisioni, lavori, collegamenti suite e tutti gli altri dati dei tre schemi restano inclusi.

Il comando usa `pg_dump` e `pg_restore` PostgreSQL 17 nell'immagine Docker ufficiale `postgres:17`. Docker Desktop deve essere avviato e l'immagine deve essere disponibile. Non avvia alcuna generazione AI. Il primo backup Supabase reale è stato completato il 30 ottobre 2026; il task Windows è stato successivamente collaudato e abilitato.

## Configurazione privata

Copia `backup-connection.example.json` in una cartella privata fuori dalle repository, per esempio `%LOCALAPPDATA%\BandiSuite\private\backup.local.json`, e sostituisci i segnaposto. Proteggi anche questa cartella per il solo utente Windows e SYSTEM; non salvare password nel file di esempio, negli argomenti del comando o in Git.

Usa un account operatore dedicato al backup, distinto dagli account `*_app` dei servizi. Deve poter leggere tutti gli oggetti dei tre schemi e le relative sequenze. In alternativa, esegui backup separati con l'owner di ciascuno schema usando `--schema associago`, `--schema librepm` e `--schema bandi_portal`. I ruoli runtime conosciuti sono rifiutati. Questo script non crea ruoli, non modifica privilegi e non abilita nuovi accessi remoti. Le credenziali dell'operatore vanno predisposte insieme alla configurazione Supabase.

Per una connessione remota sono obbligatori `sslmode: "verify-full"` e `sslrootcert` con il certificato CA verificato del servizio. Il certificato deve essere un file locale leggibile. È consentito disabilitare TLS soltanto per il sandbox locale. `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD`, `PGSSLMODE` e `PGSSLROOTCERT` possono sostituire il JSON, ma il file privato evita di lasciare segreti nella cronologia della shell.

La password viene copiata in un `PGPASSFILE` temporaneo con accesso ristretto, montato in sola lettura e normalizzato con permessi 600 nella memoria del container. Non è inclusa negli argomenti dei processi, nell'ambiente visibile con `docker inspect`, nei manifest o nei log. Non stampare il file di configurazione.

```powershell
python deployment/backup_cloud_schemas.py --config "$env:LOCALAPPDATA\BandiSuite\private\backup.local.json"
```

La destinazione predefinita è `%LOCALAPPDATA%\BandiSuite\backups`. `--output-dir` consente un altro disco, purché fuori da qualsiasi repository Git. Ogni esecuzione crea una directory unica con ACL Windows per utente corrente e SYSTEM. Lo script non cancella backup precedenti, nemmeno quando un tentativo fallisce. La rotazione e lo spazio libero vanno gestiti esplicitamente.

## Controllo dell'esito

Un'esecuzione riuscita contiene `archive.dump`, `archive.list`, `manifest.json` e `state.json`. Il manifest registra SHA256 dell'archivio e dell'indice, dimensione, schemi e versione del client. `state.json` contiene stato terminale, fase, PID e aggiornamento; gli errori riportano codici senza credenziali. Il processo ha un limite complessivo di circa 210 secondi, oltre alla breve chiusura dei soli figli identificati. Ogni container ha nome e nonce univoci; la pulizia verifica l'identità prima di rimuoverlo.

`pg_restore --list` verifica che l'indice dell'archivio sia leggibile; non dimostra da solo che un ripristino completo riesca. Per questo `restore_tested` resta `false` nei backup ordinari. Gli archivi non includono owner e grant: per un ripristino servono anche gli script di provisioning dei ruoli e dei privilegi. Non usare questi file per sovrascrivere un database operativo senza un piano di ripristino verificato.

## Prova reale eseguita su dati sintetici

`verify_portal_backup_restore.py` accetta esclusivamente il sandbox PostgreSQL locale identificato dai metadati privati del test. Non è un comando per ripristinare sul cloud. Clona lo schema `bandi_portal` in un nuovo database UUID, crea nella sola copia una bozza con due revisioni mediante il servizio applicativo, esporta uno snapshot coerente, esegue il dump e lo ripristina in un secondo database UUID. Confronta conteggi e SHA256 di tutte le colonne delle bozze e delle revisioni; infine elimina esclusivamente i due database creati dalla propria esecuzione. Lo schema sorgente resta invariato.

La prova del 30 ottobre 2026 è riuscita: **1 bozza, 2 revisioni, hash identici, 2 database temporanei rimossi**. Evidenza locale: `%LOCALAPPDATA%\BandiSuite\restore-drill-4e03b95d9f6a4ed8b31e123ba99a1c7b\restore-verification.json`. Nessun processo o container del backup è rimasto attivo. Questa prova riguarda il portale, non certifica ancora un ripristino completo dei tre schemi cloud definitivi.

Il comando seguente richiede il sandbox di sviluppo attivo e la virtualenv Bandi; non va eseguito contro dati remoti:

```powershell
..\Bandi\.venv\Scripts\python.exe deployment/verify_portal_backup_restore.py --sandbox scripts/supabase/.artifacts/portal-20261030 --bandi-repo ../Bandi --output-dir "$env:LOCALAPPDATA\BandiSuite\restore-drill-nuovo"
```

## Pianificazione Windows facoltativa

`backup_cloud_windows.ps1 -Action Install` prepara un'attività giornaliera alle 03:30, inizialmente **disabilitata**. Non sovrascrive attività esistenti, non inserisce password negli argomenti, evita esecuzioni sovrapposte e impone un limite di quattro minuti. Include recupero dell'orario perso (`StartWhenAvailable`) e avvio tre minuti dopo il login; richiede rete disponibile e riprova al massimo due volte a distanza di 15 minuti in caso di errore. Un archivio completo del giorno corrente viene riusato solo dopo aver ricontrollato i due SHA256 e gli stati di successo, evitando duplicati fra trigger giornaliero e login.

```powershell
powershell -NoProfile -File deployment/backup_cloud_windows.ps1 -Action Install -ConfigPath "$env:LOCALAPPDATA\BandiSuite\private\backup.local.json"
# Solo dopo un backup manuale riuscito e la verifica del risultato:
powershell -NoProfile -File deployment/backup_cloud_windows.ps1 -Action Enable
powershell -NoProfile -File deployment/backup_cloud_windows.ps1 -Action Status
```

L'attività usa la sessione interattiva dell'utente: occorrono PC acceso, utente connesso, Docker Desktop e rete disponibili. Non risveglia il PC. Se alle 03:30 era spento, tenta il recupero quando torna disponibile e dopo il login; non può fare copie mentre il computer rimane spento. Il cron HTTP del portale esegue manutenzione e **non è un backup**. Una copia sullo stesso PC non è una copia fuori sede; nessuna garanzia di perdita dati zero viene fornita. Copie cifrate su un supporto separato e prove periodiche di ripristino restano da organizzare.

## Attivazione effettiva del 30 ottobre 2026

Connessione privata: `%LOCALAPPDATA%\BandiSuite\private\release-tfeplskriazdybiampgg\backup.local.json`. Il ruolo dedicato `suite_backup` legge i tre schemi mediante session pooler e TLS `verify-full`, usando la CA pubblica tracciata in `deployment/certs/supabase-root-2021.crt`. Nessuna credenziale owner o runtime viene usata dal task.

Il primo archivio reale è `%LOCALAPPDATA%\BandiSuite\backups\tfeplskriazdybiampgg\backup-20261030T134428Z-f11863751b6a423fa4e5e20fd7f9a85b`: **481.510 byte**, tre schemi, indice leggibile e SHA256 di archivio e indice verificati. `restore_tested=false`: i dati cloud reali non sono stati ripristinati su un altro sistema. Rimane valida la prova di ripristino su dati esclusivamente sintetici descritta sopra.

L'attività `Bandi Suite Cloud Backup` è installata e abilitata. Il suo primo avvio tramite Utilità di pianificazione si è concluso con codice `0`, riconoscendo e verificando la copia odierna. Prossima esecuzione giornaliera: 31 ottobre 2026 alle 03:30 locali. Nessun archivio precedente è stato cancellato.

L'attività è stata sospesa temporaneamente durante la verifica del nome dell'organizzazione Supabase e riabilitata dopo la conferma del medesimo progetto. Il backup e la configurazione sono rimasti conservati; non sono stati eseguiti ripristini, pulizie o spostamenti di database durante questa verifica. Le ACL dell'archivio reale sono state controllate: accesso soltanto all'utente Windows corrente e SYSTEM.

Il recupero degli avvii persi e i retry usano le impostazioni documentate da [Microsoft per Utilità di pianificazione](https://learn.microsoft.com/en-us/powershell/module/scheduledtasks/new-scheduledtasksettingsset).

Test locali senza rete: `python -m unittest discover -s deployment -p test_backup_cloud_schemas.py`.
