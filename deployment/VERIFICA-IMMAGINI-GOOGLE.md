# Verifica dei Docker di Gestionale e Progetti

Il [collaudo successivo della suite](VERIFICA-COMPLETA-20261101.md) conferma
nuovamente i due servizi. Il Gestionale ha risposto 200 dopo 48,176 secondi
di avvio a freddo. La correzione successiva di Bandi è pubblicata nella
revisione `cf-20261101-c`, immagine `455e63ece0f6080452a90d866f5e53425d661c4dd793739398a36a6b69f4176c`;
la precedente `cf-20261101-b` è conservata per ripristino. La nuova misura
del registro è 454.063.160 byte, dopo l'aggiunta di 187.017 byte.
I conteggi della pulizia più sotto descrivono l'operazione già completata,
prima di questo aggiornamento correttivo.

Controllo del **1 novembre 2026**, aggiornato dopo la pulizia delle 04:15,
fuso Europe/Rome. Entrambi i servizi sono operativi. Il timeout osservato sul
Gestionale dipendeva dall'avvio a freddo: la richiesta è poi terminata con
HTTP 200. Su successiva istruzione esplicita sono state eliminate le vecchie
immagini Google inutilizzate; immagini attive, dati e copie locali restano
conservati.

## Immagini effettivamente pubblicate

| Area | Servizio Google | Revisione attiva |
| --- | --- | --- |
| Gestionale, AssociaGo | `adf-gestionale-collaudo` | `adf-gestionale-collaudo-as-ui-20261030-c` |
| Progetti, LibrePM | `adf-bandi-collaudo` | `adf-bandi-collaudo-pg-20261030-a` |

Il nome storico `adf-bandi-collaudo` identifica **Progetti/LibrePM**.
Il sito Bandi usa invece `adf-bandi-portale-api`.

Le immagini sono nello stesso repository Artifact Registry
`europe-west8-docker.pkg.dev/flawless-star-503422-q3/cloud-run-source-deploy`.

- Gestionale: `adf-gestionale-collaudo@sha256:1b5e8a2d6163cc5876e5b18b459919459443de9eb3874495677ad6155fc51f41`.
- Progetti: `adf-bandi-collaudo@sha256:18981e863362d3a15a7a278e29577a1e1c05b2f646a73d96de878f0ec8447b3c`.

I digest del registro coincidono con quelli delle immagini presenti in
Docker sul PC. I JAR conservati nei contesti di build coincidono, byte per
byte, con i JAR ancora presenti nelle due repository:

| Applicazione | JAR | SHA-256 |
| --- | ---: | --- |
| AssociaGo | 119.841.622 byte | `ff19e33293679ea8531819241a75529cbe9679ac4547e306ded45eb5a9c3f37b` |
| LibrePM | 92.818.569 byte | `3b0f5c2d3fbe1f739f033e310e885d9e28a3448e1802594ffcf794a232faba8d` |

Il build finale estrae i JAR in livelli: il confronto riguarda gli artefatti
usati per costruire queste immagini, non un ipotetico `app.jar` ancora
presente nel container finale. Entrambe usano Java 21.0.13+9, architettura
amd64 e utente `suiteapp`, senza eseguire l'applicazione come root.

## Dimensioni e spazio

| Immagine | Livelli compressi sul registro | Dimensione locale non compressa |
| --- | ---: | ---: |
| Gestionale | 224.638.667 byte, circa 224,64 MB | 689.520.493 byte, circa 689,52 MB |
| Progetti | 200.299.101 byte, circa 200,30 MB | 638.712.574 byte, circa 638,71 MB |

Sono stati letti i manifest OCI remoti, senza scaricare nuovamente i livelli.
Ogni immagine ha 13 livelli; 10 sono condivisi. I livelli compressi distinti
delle due immagini occupano **309.340.359 byte**, circa 309,34 MB. Sommare
le due dimensioni locali sovrastimerebbe lo spazio condiviso.

Quest'ultimo conteggio esclude configurazioni, indici OCI, attestazioni e
altre immagini. La lettura separata di tutte le risorse file del repository
ha rilevato **87 file unici, 453.904.007 byte** alle 03:30 locali, prima della
pulizia. Dopo la rimozione autorizzata risultano **67 file, 453.876.143 byte**.
Sono già recuperati 27.864 byte di manifest; altri **52.196.016 byte** di
blocchi esclusivi sono potenzialmente recuperabili dalla raccolta interna
del provider. Non sono stati dichiarati come spazio già libero.

È una misura di quel repository; non rappresenta l'intero consumo o la
fattura dell'account Google. Rispetto a un riferimento prudenziale di
500.000.000 byte, il margine misurato è circa 46,12 MB. Le altre risorse e
i prossimi build richiedono comunque controllo. I conteggi storici ottenuti
con metodi diversi non vanno usati per dedurre altre cancellazioni.

## Perché il Gestionale aveva superato 45 secondi

I log Google riportano l'avvio di `AssociaGoApplication` in **43,288 secondi**.
La richiesta del 1 novembre alle 03:15:07 locali è stata completata dal
server con **HTTP 200 dopo 47,097 secondi**, oltre il limite di attesa del
client che la stava controllando.

Successivamente, sulle stesse immagini:

- Gestionale: `/actuator/health` → `200`, stato `UP`, **0,574 secondi**.
- Progetti: `/actuator/health` → `200`, stato `UP`, **0,428 secondi**.

L'endpoint restituisce soltanto lo stato sintetico. I controlli delle API
riservate richiedono autenticazione; il precedente controllo di Progetti
aveva verificato anche la risposta 401 a una richiesta anonima.

Nei log esaminati non sono comparsi errori di memoria esaurita,
autenticazione database, DNS o saturazione delle connessioni. Questo descrive
la finestra controllata, senza garantire che simili problemi non possano
verificarsi in futuro.

## Configurazione e costi

Entrambi hanno 1 CPU, 1 GiB RAM, concorrenza 20 e timeout HTTP 300 secondi.
Il massimo è una istanza; non sono prenotate istanze minime. Il riavvio dopo
un periodo senza traffico può quindi richiedere di nuovo tempo. Il boost di
avvio risulta disabilitato nel Gestionale e abilitato in Progetti; durante
questa verifica i parametri di esecuzione dei due servizi sono rimasti invariati.

Le revisioni usano `SPRING_APPLICATION_JSON` da Secret Manager, versione 1
del rispettivo segreto runtime. I bundle privati preparati per queste
revisioni contengono i ruoli applicativi `associago_app` e `librepm_app`;
le credenziali owner delle migrazioni restano separate. Non è stato letto
nuovamente il contenuto dei segreti Google per svolgere questo controllo.

Inventario dei componenti della suite, prima e dopo la pulizia:

| Risorsa | Prima, 03:30 | Dopo, 04:15 |
| --- | ---: | ---: |
| Revisioni Gestionale | 2 | 1 |
| Revisioni Progetti | 1 | 1 |
| Revisioni API Bandi | 4 | 1 |
| Versioni di segreti abilitate nel progetto | 5 | 5 |
| Job di verifica Bandi | 1 | 1, con una sola esecuzione completata |

Il job conservato è `adf-cf-read-verify-20261101`: non ha una pianificazione.
Il consumo Google resta soggetto alle quote e alla fatturazione già attiva;
la presenza di immagini conservate o di istanze minime a zero non costituisce
una garanzia di costo totale nullo. Nessun piano a pagamento aggiuntivo è
stato attivato per questa verifica.

## Pulizia autorizzata delle vecchie immagini

L'ispezione ha trovato regole automatiche già presenti nel repository:
cancellazione delle immagini senza tag dopo 7 giorni, cancellazione dopo
30 giorni e conservazione delle due versioni più recenti. Lasciarle attive
avrebbe permesso a Google di eliminare immagini anche senza interventi
manuali, in contrasto con la richiesta di conservare i file.

È stata prima abilitata **soltanto la simulazione delle regole**
(`cleanupPolicyDryRun: true`), mantenendo identiche tutte le condizioni.
La modifica è reversibile e non elimina immagini. Una verifica indipendente
ha confermato che gli unici campi del repository cambiati sono il flag di
simulazione e la data di aggiornamento. Tutti gli 87 file, con gli stessi
nomi, dimensioni e hash, risultavano conservati prima della pulizia:
453.904.007 byte.

Dopo la nuova richiesta esplicita di cancellare i vecchi Docker Google,
sono stati controllati tutti i servizi Cloud Run, job ed esecuzioni del
progetto, in tutte le regioni, insieme ai riferimenti OCI del repository.
Sono stati rimossi:

- **sei build obsolete**, corrispondenti a 20 versioni OCI, inclusi manifest
  piattaforma e attestazioni correlate;
- quattro revisioni Cloud Run senza traffico: tre vecchie revisioni Bandi
  e la precedente revisione Gestionale;
- i due tag Bandi `pg-candidate` e `rollback`, che puntavano alle revisioni
  obsolete.

Le 20 rimozioni sono riconciliate: 14 comandi SDK completati e sei
attestazioni eliminate automaticamente dall'API insieme ai rispettivi
manifest. Restano **tre immagini in uso**, rappresentate da nove manifest
OCI necessari. Il Docker Bandi protetto è
`adf-bandi-portale-api@sha256:2efd9f2ab6181f9004dff36586bdb21c5e81b06e6d2efad9a6c0c43461b6a6bf`;
i digest di Gestionale e Progetti restano quelli indicati sopra.

Le verifiche successive hanno restituito HTTP 200 per tutti e tre i servizi:
Gestionale `UP` in 0,510 secondi, Progetti `UP` in 0,485 secondi e Bandi `ok`
in 0,595 secondi. Il job di collaudo, i cinque segreti abilitati, Docker
locale, file originali, Supabase e Cloudflare restano invariati. Le
impostazioni delle applicazioni e il traffico di produzione non sono stati
modificati dalla pulizia.

Le nuove immagini continueranno a occupare spazio: prima di ulteriori
pubblicazioni occorre ricontrollare la quota disponibile. La simulazione
impedisce alle regole conservate di cancellare automaticamente i file;
non espande la franchigia gratuita. Lo spazio dei blocchi privi di riferimenti
può aggiornarsi successivamente: non sono state eseguite cancellazioni dirette
dei blob del registro.

## Scansione delle vulnerabilità

Il repository risulta `SCANNING_DISABLED`; le API Container Analysis e
Container Scanning non risultano abilitate nel progetto. Non è quindi
disponibile un rapporto automatico di vulnerabilità da presentare come
verificato. Questo **non significa zero vulnerabilità**. Non sono state
abilitate API o avviate scansioni a pagamento.

## Prove conservate

Le ricevute locali sono in
`deployment/.artifacts/google-two-images-readonly-20261101-5a51bd6838424dd2a22125e8bb67e73a/`:
`receipt.json`, `librepm-correct-health.json`, `compressed-layers.json`,
`registry-jar-details.json`, `final-resource-inventory-verified.json` e
`retention-independent-proof.json`. La ricevuta della singola modifica di
conservazione è `deployment/.artifacts/registry-retention-dryrun-20261101.json`.
La ricevuta finale della pulizia autorizzata è
`deployment/.artifacts/obsolete-suite-continuation-20261101-f2cd6193939e4ad1b97f35972fcda357/receipt.json`.
I documenti completi del provider restano nella cartella privata locale,
fuori dai repository. Le prove pubblicabili contengono soltanto metadati,
stati e conteggi; non includono password, token o dati degli utenti.
