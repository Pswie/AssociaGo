# Checkup prima dell'attivazione

`check_portal_readiness.py` è il controllo unico del PC per nodo, manutenzione cron e backup. Non avvia il nodo, non installa o abilita attività Windows, non genera documenti e non contatta servizi remoti. Il suo eventuale file di ricevuta è l'unica scrittura applicativa del controllo.

```powershell
python deployment/check_portal_readiness.py --bandi-repo ../Bandi --receipt "$env:LOCALAPPDATA\BandiSuite\release-readiness\readiness.json"
```

La ricevuta contiene soltanto presenza dei file, stato Docker/task, esiti booleani e percorsi locali. Non contiene URL di connessione, account database, chiavi, header o contenuto degli archivi. Il controllo non cerca cookie, vault, profili del browser o credenziali Windows. Per cron rileva soltanto i nomi `COLLAB_CRON_KEY` e `CRONJOB_API_KEY` nell'ambiente, o in un file esplicitamente dichiarato con `--cron-env-file`; `--cron-key-file` verifica soltanto che il file indicato esista. Non verifica l'account cron-job.org.

Il controllo usa il file nodo `.env.portal-node.local`, oppure `.env` soltanto per verificare l'archivio quando il primo manca. `--node-env` e `--backup-config` consentono percorsi privati diversi. Le impostazioni vengono caricate con la stessa classe nativa `Settings` del nodo; il percorso `DATA_ROOT` relativo si risolve nella root Bandi. Il probe database è limitato a PostgreSQL su loopback: una transazione esplicitamente in sola lettura esegue `SELECT 1` e `LIMIT 0` sulle tre tabelle di catalogo/fascicoli, senza leggere righe utente, modificare dati o migrare schemi. Ogni connessione e sottoprocesso ha un timeout.

Il codice di uscita è `0` quando il nodo catalogo soddisfa i controlli locali, `2` quando manca una configurazione necessaria e `1` quando il controllo non può concludersi. `catalog_node_ready=true` indica preparazione locale; `activation_verified` resta `false`: un login nel sito, la raggiungibilità del gateway e una prima lettura sintetica devono essere verificati separatamente durante l'attivazione autorizzata. Il campo `generation_disabled` riporta la configurazione effettiva; la generazione si abilita separatamente dopo autorizzazione del proprietario e verifica dei gate nativi, non tramite questo checkup.

Il primo checkup del 30 ottobre 2026 ha confermato: archivio Docker `healthy`, database locale raggiungibile, tabelle leggibili, `DATA_ROOT=G:\Terzi settore`, generazione disabilitata. Successivamente sono stati collaudati e attivati il nodo catalogo e il backup: i dettagli sono nelle guide dedicate. Il percorso delle ricevute è stato impostato esplicitamente nella cartella privata del rilascio, preservando il `DATA_ROOT` dell'archivio.

Per controllare la configurazione effettivamente attivata:

```powershell
$privateRelease = "$env:LOCALAPPDATA\BandiSuite\private\release-tfeplskriazdybiampgg"
python deployment/check_portal_readiness.py --bandi-repo ../Bandi --node-env "$privateRelease\node.local.env" --backup-config "$privateRelease\backup.local.json" --receipt "$env:LOCALAPPDATA\BandiSuite\release-readiness\readiness.json"
```

Per procedere dopo la pubblicazione:

1. Predisporre il file nodo privato con l'URL HTTPS definitivo e la chiave dispositivo forniti dall'operatore, preservando database e `DATA_ROOT` locali; mantenere `COLLAB_NODE_ALLOW_GENERATION=false`.
2. Ripetere questo checkup. Avviare il nodo supervisionato soltanto dopo il controllo delle credenziali e dell'accesso proprietario; verificare la prima lettura di catalogo e l'heartbeat nel sito.
3. Preparare il job manutenzione con [CRON-PORTALE.md](CRON-PORTALE.md), poi applicarlo soltanto con credenziali dell'account cron esplicitamente fornite.
4. Predisporre la connessione operatore e provare il backup manuale secondo [BACKUP-CLOUD.md](BACKUP-CLOUD.md). Le attività automatiche si installano disabilitate e si abilitano dopo il collaudo.

Il checkup non abilita nessuno di questi passaggi. Test offline: `python -m unittest discover -s deployment -p test_check_portal_readiness.py`.
