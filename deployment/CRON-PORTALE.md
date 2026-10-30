# Controllo periodico del gateway Bandi

`install_portal_cron.py` prepara un solo job cron-job.org dedicato a
`GET /api/collaboration/v1/maintenance/heartbeat`. Il controllo verifica il
datastore online; non accende il PC e non avvia ingestion o generazioni.

La configurazione usa intervalli di 30 minuti (`0` e `30`), fuso `Europe/Rome`,
timeout 30 secondi, header `X-Cron-Key`, risposta non archiviata e redirect non
considerati successi. Non vengono pianificate chiamate ad altre API.

## Preparazione senza chiamate di rete

Impostare `COLLAB_CRON_KEY` nell'ambiente oppure fornire un file privato con
`--cron-key-file`; in assenza di entrambi il programma usa un prompt nascosto.
Non passare la chiave come argomento e non inviarla in chat.

```powershell
python deployment/install_portal_cron.py --gateway-url https://HOST_DEL_GATEWAY
```

Il comando scrive `deployment/.private/portal-maintenance.json`, escluso da
Git, contenente la sola configurazione del job. Il file include la chiave cron:
proteggerlo con i permessi dell'utente proprietario, anche su Windows. L'API key
cron-job.org non viene esportata. Per cambiare il percorso usare `--export`.

## Installazione esplicita

Quando il gateway è pubblicato e il suo endpoint è stato verificato, fornire
l'API key tramite `CRONJOB_API_KEY` o il prompt nascosto:

```powershell
python deployment/install_portal_cron.py --gateway-url https://HOST_DEL_GATEWAY --apply
```

Il programma legge l'elenco completo dei job. Crea il job solo se titolo e URL
sono assenti; modifica soltanto un job che corrisponde a entrambi. Titoli o URL
duplicati, corrispondenze parziali, elenchi incompleti e dati cambiati durante
la verifica interrompono l'operazione. Una seconda esecuzione con gli stessi
valori non crea duplicati; una nuova chiave cron aggiorna il job corrispondente.
La configurazione scritta viene riletta e verificata. Non sono disponibili
operazioni DELETE, modifica di job estranei o retry automatici di scritture
con esito incerto.

L'API client usa soltanto `https://api.cron-job.org`, con TLS verificato,
timeout e limiti di risposta. Non segue redirect e non eredita proxy ambientali.
I messaggi mostrano azione e ID, senza chiavi, header o risposte complete.

## Collaudo

```powershell
python -m unittest discover -s deployment -p test_install_portal_cron.py
```

I test sono offline e non contattano account cron-job.org o gateway reali.
L'API e il significato di `someFailed`, GET/PUT/PATCH e dei campi del job sono
stati verificati nella [documentazione ufficiale](https://docs.cron-job.org/rest-api.html).

## Configurazione effettiva pronta

Il 30 ottobre 2026 è stato preparato il JSON per il gateway reale nella cartella privata `%LOCALAPPDATA%\BandiSuite\private\release-tfeplskriazdybiampgg\portal-maintenance.json`. Include la chiave cron già assegnata, mai stampata. Il gateway è stato verificato; manca ancora l'API key dell'account cron-job.org. Nessun job è stato creato o modificato sull'account cron. Per installarlo si usa `--apply` con la configurazione privata secondo le istruzioni sopra.

Il file `cron-key.local` nella stessa cartella privata contiene la chiave del gateway. Il comando completo seguente chiede **soltanto l'API key cron-job.org con prompt nascosto**, se non è già presente nella variabile `CRONJOB_API_KEY`. La chiave non va incollata in chat o passata sulla riga di comando:

```powershell
python deployment/install_portal_cron.py --gateway-url https://adf-bandi-portale-api-y5sbh5kxpa-oc.a.run.app --cron-key-file "$env:LOCALAPPDATA\BandiSuite\private\release-tfeplskriazdybiampgg\cron-key.local" --export "$env:LOCALAPPDATA\BandiSuite\private\release-tfeplskriazdybiampgg\portal-maintenance.json" --apply
```

La sospensione temporanea dei task PC durante la verifica del nome Supabase non ha modificato questo export: il progetto è stato confermato con lo stesso ID e il gateway resta quello indicato. Lo stato finale del cron esterno è **configurazione pronta, installazione in attesa dell'API key dell'account**. Il gateway può restare operativo anche prima dell'attivazione di cron-job.org.
