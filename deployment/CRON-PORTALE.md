# Controllo periodico del gateway Bandi

`install_portal_cron.py` prepara un solo job cron-job.org dedicato a
`GET /api/collaboration/v1/maintenance/heartbeat`. Il controllo verifica il
datastore online; non accende il PC e non avvia ingestion o generazioni.

La configurazione usa intervalli di 30 minuti (`0` e `30`), fuso `Europe/Rome`,
timeout 30 secondi, header `X-Cron-Key`, risposta non archiviata e redirect non
considerati successi. Non vengono pianificate chiamate ad altre API.

## Attivazione semplice su questo PC

Il percorso previsto usa esclusivamente **cron-job.org**, senza scheduler
GitHub. La ricerca nel catalogo delle integrazioni non ha restituito un
connettore esatto per questo servizio; il launcher usa il client dell’API
ufficiale già presente nella repository.

Quando sei pronto a collegare il tuo account, apri una normale finestra
PowerShell ed esegui:

```powershell
powershell.exe -NoProfile -File "C:\Users\lange\Desktop\Github\AssociaGo\deployment\attiva-cron-portale.ps1"
```

Il comando funziona anche da una cartella diversa dalla repository. Apre
[cron-job.org → Settings](https://console.cron-job.org/settings) nel browser
normale. Accedi al tuo account, oppure crea un account gratuito se non ne hai
uno; in **Settings / Impostazioni** genera o copia una **API key**. Il servizio
e l’accesso API sono descritti nelle [pagine ufficiali](https://cron-job.org/en/)
e nella [documentazione dell’API](https://docs.cron-job.org/rest-api.html#authentication).

Torna nella finestra PowerShell e incolla la API key nel prompt nascosto,
quindi premi Invio: durante l’inserimento non compaiono caratteri. **La chiave
va inserita soltanto lì, mai in chat, in un comando o in uno screenshot.**
Non occorre inserire nuovamente la chiave cron del gateway.

Il launcher verifica i file privati già pronti e l’origine del gateway nelle
impostazioni pubbliche, poi crea o aggiorna il solo job corrispondente. La
API key resta in memoria: non viene salvata nei file, esportata o passata
negli argomenti del processo. Viene richiesto un inserimento locale anche se
esiste già una variabile `CRONJOB_API_KEY`. Se non è disponibile un prompt
nascosto, l’installer si interrompe prima di usare un input visibile.

Al termine mostra azione e ID e scrive:

```text
%LOCALAPPDATA%\BandiSuite\private\release-tfeplskriazdybiampgg\portal-maintenance.receipt.json
```

La ricevuta contiene soltanto fornitore, data, azione, ID e risultato della
verifica. Non contiene chiavi, header, configurazioni complete o diagnostica
HTTP. `configurationVerified: true` conferma che la configurazione è stata
riletta dall’account; `executionVerified: false` ricorda che il primo
heartbeat va controllato nella cronologia del job sul sito. Una seconda
esecuzione con gli stessi valori conserva il medesimo job e riporta
`unchanged`. Un errore sostituisce l’eventuale ricevuta precedente con un
esito non verificato, senza ripetere automaticamente la scrittura.

Per verificare soltanto la preparazione:

```powershell
powershell.exe -NoProfile -File "C:\Users\lange\Desktop\Github\AssociaGo\deployment\attiva-cron-portale.ps1" -PrepareOnly
```

Questa modalità non apre browser, non chiede chiavi e non contatta API.
Il 30 ottobre 2026 è stata eseguita con successo anche da una cartella
esterna alla repository. Nessun account è stato creato, nessun piano a
pagamento è stato attivato e nessun job remoto è stato installato durante la
preparazione.

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
Il launcher semplice aggiunge `--prompt-api-key` e `--receipt` a questa stessa
installazione; non esiste un secondo meccanismo di pianificazione.

## Collaudo

```powershell
python -m unittest discover -s deployment -p test_install_portal_cron.py
```

I 10 test sono offline e non contattano account cron-job.org o gateway reali.
Comprendono la seconda esecuzione senza duplicati, l’input nascosto obbligatorio,
il rifiuto del ripiego su input visibile e ricevute senza segreti anche in caso
di errore.
L'API e il significato di `someFailed`, GET/PUT/PATCH e dei campi del job sono
stati verificati nella [documentazione ufficiale](https://docs.cron-job.org/rest-api.html).

## Configurazione effettiva installata

Il 30 ottobre 2026 è stato creato e riletto su **cron-job.org** il job
**8758073**, «Bandi portal maintenance», abilitato ogni 30 minuti. Usa il
gateway reale e l'header privato già configurato. L'API key dell'account è
stata usata soltanto per l'installazione, senza salvarla nella repository.
La ricevuta privata conferma `configurationVerified: true`.

Una chiamata di verifica allo stesso endpoint ha restituito HTTP200 alle
18:26 UTC. Anche la prima esecuzione pianificata è stata verificata nella
cronologia ufficiale: **30 ottobre, 19:30 ora italiana, HTTP200, durata601ms**.
La ricevuta privata riporta ora anche `executionVerified: true`.

Il file `cron-key.local` nella stessa cartella privata contiene la chiave del gateway. Il comando completo seguente chiede **soltanto l'API key cron-job.org con prompt nascosto**, se non è già presente nella variabile `CRONJOB_API_KEY`. La chiave non va incollata in chat o passata sulla riga di comando:

```powershell
python deployment/install_portal_cron.py --gateway-url https://adf-bandi-portale-api-y5sbh5kxpa-oc.a.run.app --cron-key-file "$env:LOCALAPPDATA\BandiSuite\private\release-tfeplskriazdybiampgg\cron-key.local" --export "$env:LOCALAPPDATA\BandiSuite\private\release-tfeplskriazdybiampgg\portal-maintenance.json" --apply
```

La configurazione resta nella cartella privata indicata. Il launcher serve
per future verifiche o modifiche: non occorre eseguirlo per attivare il job
già installato. Non sono stati creati scheduler GitHub o Google. Il controllo
periodico verifica il database, ma non sostituisce un backup e non costituisce
una garanzia contro la sospensione del piano gratuito Supabase.
