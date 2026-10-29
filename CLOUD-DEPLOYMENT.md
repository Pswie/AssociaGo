# Pubblicazione Google Cloud: stato e procedura

Verifica eseguita il 29 ottobre 2026. Questa repository conteneva una versione
precedente a quella effettivamente pubblicata. Il backend del servizio esistente
è stato recuperato dalla sorgente originale di Cloud Build e integrato con merge
a tre vie, mantenendo le correzioni locali dell'audit.

## Provenienza del backend recuperato

| Campo | Valore |
| --- | --- |
| Progetto Google Cloud | `flawless-star-503422-q3` |
| Regione | `europe-west8` |
| Servizio da aggiornare | `adf-gestionale-collaudo` |
| Backend pubblico | `https://adf-gestionale-collaudo-y5sbh5kxpa-oc.a.run.app` |
| Frontend esistente | `https://adf-gestionale-collaudo.vercel.app` |
| Revisione precedente | `adf-gestionale-collaudo-00012-xhf` |
| Cloud Build originale | `cdf0b91f-cec7-4d14-94f0-8c6446cfdca6` |
| Data build originale | 30 luglio 2026 |
| SHA-256 archivio sorgente | `c98e4d1105eafaf9161e75be4dd8d39ce9cc4f0008359a5f059cf8219f704de3` |

Archivio originale:

```text
gs://run-sources-flawless-star-503422-q3-europe-west8/services/adf-gestionale-collaudo/1785446720.93513-3f5ff09f0dc14cde942173281cf3c09f.zip
```

Immagine precedente, da conservare per il ripristino:

```text
europe-west8-docker.pkg.dev/flawless-star-503422-q3/cloud-run-source-deploy/adf-gestionale-collaudo@sha256:3ad334baef2a21edd0b75bddf3ab2e7aed62fad2876a070ffa14b96bcd038a26
```

Il sorgente recuperato conserva autenticazione Clerk, controlli dei ruoli,
allegati nel database, progetti, rimborsi e assicurazioni dei volontari. Conserva
anche gli script Flyway originali: MySQL V1–V12 e SQLite V1–V42. Gli script già
pubblicati non devono essere riscritti: i loro checksum appartengono alla storia
del database. L'archivio Cloud Build esclude `desktop/` e quindi non contiene il
sorgente del frontend Vercel.

## Blocco attuale: connessione al database

Il backend risponde HTTP 503. I log Google Cloud del 29 ottobre 2026 mostrano
`java.net.UnknownHostException` per l'host MySQL Aiven configurato. Anche la
risoluzione DNS dalla macchina locale restituisce nome inesistente. Flyway non
riesce ad aprire la connessione e Spring termina prima di rendere disponibile
la porta HTTP.

Il problema richiede la verifica del servizio nella console Aiven e del suo
endpoint attuale. Il risultato DNS non dimostra che i dati siano stati cancellati.
Sulla macchina non risultano CLI Aiven, configurazione Aiven o token Aiven già
disponibili. L'accesso Google Cloud, invece, funziona.

Prima della pubblicazione occorre:

1. Ripristinare il servizio Aiven esistente o individuare il relativo backup.
2. Verificare host, porta e nome del database. Le credenziali devono rimanere
   nei segreti e nella configurazione del servizio, senza inserirle nel codice.
3. Salvare un backup del database e verificare la storia Flyway esistente.
4. Verificare che il backend torni ad avviarsi e che i dati siano presenti.

Cambiare immagine non risolve un hostname inesistente. Non eseguire `flyway clean`
o `flyway repair` per aggirare il problema di connessione o i checksum. Non
ricreare un database vuoto presentandolo come ripristino dei dati esistenti.

## Rilascio della versione corretta

Il frontend pubblicato proviene da `Pswie/terzo-settore`, directory
`gestionale/desktop`, commit `6fe61d156669bc592143980ed9af3fd2fc73e386`.
È stato recuperato dalla copia locale corrispondente esattamente a quel commit
e integrato in `desktop/` qui, conservando accesso Clerk, tema, viste annuali e
layout mobile. Il progetto Vercel è `adf-gestionale-collaudo`
(`prj_sJ5h8IcJGJztPxvBhqnrs92Gw3tc`); la pubblicazione precedente è
`dpl_EThehX8kDQDRkbftvSJpD9Hr67Uz`.

Le correzioni sono nella copia di lavoro di **AssociaGo**. La repository
`terzo-settore` e i suoi rami remoti non sono stati modificati. Prima del
rilascio web occorre riportare questa sorgente verificata nella directory
`gestionale/desktop` del repository collegato, oppure pubblicare esplicitamente
la build sul medesimo progetto Vercel. Un push a `Pswie/AssociaGo` non aggiorna
automaticamente quel sito. Conservare le variabili Vite e la configurazione
satellite Clerk già presenti sul progetto; nessuna credenziale è stata copiata
in questa repository.

La build web senza variabili reali verifica la compilazione e mostra un errore
di configurazione se aperta: non è un artefatto da pubblicare sul sito così
com'è. La verifica completa di accesso Clerk, dati e invii resta da eseguire
contro il servizio ripristinato.

Usare lo stesso servizio e conservare le sue variabili d'ambiente, i riferimenti
ai segreti, l'account di servizio e l'URL. Il servizio separato
`adf-bandi-collaudo` appartiene a un'altra applicazione.

Dopo aver superato build e test e ripristinato il database, creare una nuova
immagine identificata da un tag univoco. Pubblicarla prima senza traffico:

```powershell
gcloud run deploy adf-gestionale-collaudo --project=flawless-star-503422-q3 --region=europe-west8 --image=NUOVA_IMMAGINE --no-traffic --tag=candidate
```

La revisione candidata usa il database configurato anche senza traffico: il
backup e la verifica degli script Flyway precedono questo comando. Questa
integrazione recupera gli script già distribuiti e non richiede una riparazione
della storia delle migrazioni.

Verificare sulla candidata `/actuator/health`, il rifiuto delle API senza token,
accesso Clerk, ruoli, lettura dei dati e operazioni essenziali dal frontend
esistente. Quindi assegnare il traffico alla revisione verificata:

```powershell
gcloud run services update-traffic adf-gestionale-collaudo --project=flawless-star-503422-q3 --region=europe-west8 --to-revisions=NUOVA_REVISIONE=100
```

Conservare inizialmente revisione e immagine precedenti. Per ripristinare il
traffico, dopo aver verificato la compatibilità dello schema:

```powershell
gcloud run services update-traffic adf-gestionale-collaudo --project=flawless-star-503422-q3 --region=europe-west8 --to-revisions=adf-gestionale-collaudo-00012-xhf=100
```

Rimuovere vecchi artefatti solo dopo la verifica della nuova versione. Aggiornare
il servizio esistente sostituisce la copia in uso mantenendo il suo indirizzo.
