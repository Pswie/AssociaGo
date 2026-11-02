# Bandi, catalogo e coda AI — 2 novembre 2026

## Risultato

Il portale Bandi pubblicato è https://bandi.angelidelfuturoaps.com/bandi. Mostra **cinque schede per pagina**, con navigazione sopra e sotto l’elenco. **Tutte le schede** azzera i filtri: l’archivio resta interamente consultabile. Il filtro iniziale è Aperti; selezioni individuali, schede dubbie e scadute hanno stati distinti.

Sono pubblicate **940 schede**: 919 record locali validi e 21 riferimenti online preesistenti. Una verifica indipendente ha percorso tutte le **188 pagine**, senza duplicati né elementi attesi mancanti. Il controllo completo ha trasferito circa 1,28 MB; il sito richiede soltanto la pagina visualizzata. Una lettura reale degli aperti dopo l’ultimo rilascio ha richiesto 0,594 secondi e 15,4 KB: è una misura osservata, non una promessa per ogni connessione.

La coda condivisa del PC è attiva. Bandi e i worker Zyb Instagram e Google Drive usano lo stesso ingresso autenticato; viene ammessa una sola elaborazione AI alla volta, dopo controllo delle risorse. I due nuovi worker Zyb sono attivi e sani. Originali, risultati storici, volumi e container precedenti restano conservati.

## Analisi dei bandi: problemi e correzioni

La scritta «Analisi non riuscita» corrispondeva a fallimenti reali. Non è stata sostituita con un esito positivo fittizio.

| Caso | Causa verificata | Esito dell’intervento |
| --- | --- | --- |
| Fondazione CON IL SUD, beni confiscati | Incoerenza fra ruoli degli enti e percorsi di partecipazione | Nuovo fascicolo, riparazione limitata con fonti, sei chiamate native concluse e anteprime dei due profili pubblicate |
| Campania, neurosviluppo | Fascicolo oltre il vecchio contesto; successivamente timeout e risposta del revisore troppo lunga | Contesto misurato da 65.536 token; coda comune; formato del revisore v2.15 con campi limitati |
| Fabbrica — Spazi Civici per la Gioventù | Fascicolo oltre il vecchio limite di contesto | Contesto completo misurato e richiesta in coda; nessun tentativo precedente sovrascritto |
| Servizio civile, operatori volontari | Acquisizione incompleta del PDF e natura individuale della selezione | Verifica visiva del documento principale e classificazione come selezione individuale, senza matrice di finanziamento degli enti |

L’analisi CON IL SUD è **statutaria preliminare**, con fonti e punti ancora da verificare. Non certifica l’ammissibilità completa dei due enti. Il risultato utile resta consultabile anche quando un nuovo tentativo fallisce o il metodo cambia.

Il nuovo tentativo Campania attraverso la coda ha completato la lettura del fascicolo: **42.708 token effettivi**, 4.512 token di risposta, circa 23 minuti e mezzo. Il successivo revisore ha raggiunto il limite di 4.096 token e ha interrotto una descrizione senza chiuderla. L’inferenza HTTP era conclusa, ma il documento strutturato era incompleto: è stato correttamente rifiutato, senza pubblicarne frammenti. In questa prova era presente un solo modello; il tempo era dominato dalla generazione, circa tre token al secondo, non dall’acquisizione o dalla pagina web.

La v2.15 limita descrizione e modifica richiesta a 512 caratteri ciascuna e la motivazione a 768. Conserva corpus, riserve di token, numero di rilievi e vincoli di giudizio. **145 test** verificano anche che i vecchi formati restino leggibili e che le risposte nuove troppo lunghe vengano rifiutate, senza tagli. Una prova reale separata ha completato il formato nuovo in 50,5 secondi, senza trasformare la risposta. Nessun campo ha raggiunto esattamente il limite: questa prova dimostra una risposta conforme reale, non garantisce il successo di ogni fascicolo lungo.

La pubblicazione distingue risultati completati e abbozzi incompleti. Un errore nuovo conserva l’ultimo risultato davvero completato; non eredita il vecchio riepilogo incompleto come se fosse una verifica valida. Sono stati corretti sia il produttore locale sia l’unione delle analisi nel Worker online.

Le schede riportano le fonti e la freschezza della verifica. Un avviso non verificato da oltre 72 ore può passare fra quelli da controllare. La quantità di bandi nel filtro Aperti può quindi cambiare anche senza cancellare schede: l’archivio completo resta disponibile.

## Coda comune e gestione delle risorse

Endpoint sul PC: `http://127.0.0.1:11435`. Nei container: `http://host.docker.internal:11435`. Ollama rimane l’upstream locale sulla porta 11434.

- FIFO, una sola inferenza fisica; fino a otto lavori complessivi.
- Richiesta massima 8 MiB, payload complessivi in memoria fino a 32 MiB.
- Almeno 8 GiB di RAM fisica disponibile e 16 GiB di margine di commit, con margini percentuali sui PC più grandi. Sono condizioni di ammissione, non una garanzia che qualunque modello entri in memoria.
- Modelli locali già installati e operazioni autorizzate; niente download o inferenze cloud.
- Token in file privato, in sola lettura nei container. Niente token nel codice, negli argomenti dei processi o nel database della coda.
- SQLite registra stati, identità, hash e contatori. Non conserva prompt, documenti o risposte.
- Un modello già residente e non avviato dal broker blocca l’ammissione; il broker non lo scarica arbitrariamente.
- Dopo la conclusione confermata, viene rilasciato soltanto il modello del proprio lavoro, verificando che non resti caricato prima del successivo.
- Un esito incerto blocca nuove partenze; non viene ripetuta alla cieca un’inferenza interrotta.

Un lavoro in attesa non viene avviato dopo 30 minuti di attesa; la consegna della cancellazione può attendere il drenaggio del lavoro già attivo. L’inferenza ha un limite separato di 30 minuti e un intervallo per il rilascio del modello. I timeout dei client comprendono l’attesa e l’esecuzione: 3.900 secondi per Bandi e 3.700 per Zyb.

Se il client si scollega mentre aspetta, il lavoro viene cancellato prima di partire. Se si scollega durante l’elaborazione, lo slot resta occupato finché l’upstream non è concluso. Non parte un secondo modello mentre il primo sta ancora lavorando.

La coda è installata come task Windows **AngeliDelFuturo-LocalAIQueue**, con avvio all’accesso, esecuzione nascosta e supervisore indipendente. Tutti i produttori noti sono instradati qui. Un altro programma avviato manualmente direttamente su Ollama potrebbe aggirarla: non è stato imposto un blocco globale del sistema operativo ad applicazioni estranee.

## Zyb e assegnazioni Drive

I due worker nuovi, `zyb-ai-queue-instagram-1` e `zyb-ai-queue-google-drive-1`, sono running/healthy. L’immagine è `zyb-worker:globalqueue-20261102-2ff9441385`; riusa il volume esterno originale `zyb_coordination`, i modelli e i percorsi preesistenti. Whisper CPU rimane separato.

Prima di prendere un lavoro, i worker verificano che la coda sia pronta. Durante l’attesa rinnovano l’assegnazione. Il backend verifica proprietario, file, worker, identificativo e scadenza: un’assegnazione scaduta, conclusa o trasferita non può essere riattivata dal vecchio worker. La perdita dell’assegnazione interrompe il client e lascia drenare l’eventuale elaborazione già attiva.

Il codice è pubblicato fino al commit Zyb `d62130945e95b864b2a59d135bfd689aeff8b5ea`. Un altro intervento autorizzato ha poi pubblicato `af88d19dd2bdb9e7c88e38a38b1c02aa2225fda5`, attualmente attivo su Render. La discendenza e l’identità del codice Drive sono state verificate; quel rilascio non è stato sovrascritto.

I 24 file di lavoro personali non sono stati scritti o inclusi nei nostri commit; due file TVStremio sono cambiati per l’altro intervento e sono conservati. Le prove autenticate aggiornano i normali metadati di ultimo utilizzo e rate limit, senza modificare credenziali, permessi o documenti.

## Dati, sincronizzazione e costi

Il catalogo leggero su Cloudflare D1 occupa circa **5 MB**. Non sono stati caricati altri originali pesanti su Supabase e non sono stati attivati piani a pagamento. Le vecchie copie, compresa quella storica su Supabase, restano conservate. Il loro spazio non viene dichiarato liberato.

La ricerca delle fonti e la manutenzione su **cron-job.org** restano distinte dall’AI locale. Nessun cron GitHub è stato aggiunto. Con il PC spento rimane disponibile il catalogo già pubblicato; le elaborazioni AI riprendono quando il PC e il servizio locale sono disponibili. I link alle fonti permettono di consultare i documenti sul sito che li pubblica; l’AI usa gli originali locali quando necessari.

Sono corretti il confronto fra URL ufficiali e tecnici, l’isolamento dei record malformati, le ricevute di revisione e la pubblicazione a piccoli blocchi con checkpoint. Si inviano solo dati cambiati, fino a cinque record per POST. Un singolo record non valido non ferma tutto il catalogo; uno stato incerto di scrittura continua invece a bloccare la ripartenza.

Il supervisore del coordinatore aveva inoltre segnalato un falso stallo dopo una pubblicazione realmente conclusa. Ora considera progressi effettivi e identità del processo, con limiti massimi comunque presenti; le ricevute iniziali errate restano conservate insieme alla riconciliazione. Una prova reale del coordinatore aggiornato ha completato cinque ACK, senza scritture SQL o nuove inferenze.

Il consumo futuro dipende da utilizzo e quote dei fornitori: la paginazione e l’invio delle sole modifiche riducono richieste e traffico, ma non costituiscono una garanzia illimitata di gratuità.

## Verifiche eseguite

| Ambito | Evidenza |
| --- | --- |
| Frontend pubblicato | 916 test; TypeScript ed ESLint; 375/768/1440 pixel senza overflow; controllo accessibilità automatico senza rilievi; asset confrontati con il deploy |
| Catalogo completo | 188 pagine, 940 identità uniche, nessun duplicato o record atteso mancante |
| Broker | 32 test deterministici; autenticazione, FIFO, cancellazione, incertezza, riavvio e supervisione |
| Prova FIFO reale | Due richieste native/OpenAI in coda; intervalli SQL senza sovrapposizione; nessun modello fra i due lavori e alla fine; hash di risposta verificati |
| Embedding reale | Modello bge-m3, 1.024 dimensioni, risposta valida e rilascio verificato |
| Trasporto/configurazione Bandi | 38 test; token non serializzato nelle impostazioni e nessun percorso di retry alternativo |
| Verifica combinata mirata | 437 test superati |
| Consumer Bandi, suite ampia | 296 superati; sei fallimenti documentali preesistenti riprodotti anche sul codice originale, non nascosti modificando i verdetti |
| Coordinatore e supervisione | 58 test unici e una prova reale completata |
| Formato AI v2.15 | 145 test e prova nativa reale conforme |
| Worker Cloudflare | 30 test reali workerd/Miniflare/D1; JS pubblicato corrispondente al bundle, binding conservati |
| Zyb | 130 test superati; un test flock Linux saltato su Windows; controlli autenticati del backend attivo e dei due worker Docker |

La sessione reale del proprietario **Clerk e autorizzazione di rete locale del browser non è stata riprovata in questo turno**. Opera richiede una nuova autenticazione al connettore; nel tentativo di controllo Windows la finestra Opera non era più disponibile. I test automatici e delle API non vengono presentati come prova del login reale del proprietario.

## Rilasci e ripristino

- Frontend Vercel: `dpl_CM5dfKUcxVeHHhni8nYnabztvWCs`.
- Worker Cloudflare: `26b7ec06-7a75-4cf2-99a9-738a3e25cfa8`, 100% del traffico. Configurazione e binding conservati; versione precedente ancora disponibile.
- La source map viene riserializzata da Cloudflare: hash grezzo diverso, ma sorgenti, contenuti, nomi e mapping uguali. Un solo upload; nessun secondo tentativo nascosto.
- Backend Zyb Render: commit `af88d19...`, piano Free invariato.
- Gestionale, Progetti, schemi Supabase, backup e autenticazione non sono stati modificati da questo passaggio finale.

Container e immagini precedenti sono mantenuti spenti per il ripristino. **Non avviare insieme i vecchi worker e quelli nuovi**: i vecchi non usano la coda. Per cambiare una release usare le procedure documentate e attendere che i writer siano conclusi; non fare reset o retry indiscriminati.

## Stato operativo finale

Fotografia operativa del 2 novembre, ore 05:07 italiane. Gli stati dei lavori lunghi continuano ad aggiornarsi nei file indicati.

| Servizio | Stato verificato |
| --- | --- |
| Broker comune | Attivo, PID 11288, supervisore 24844, launch 240ad253b3204d8caffa48361ab81c75 |
| API Bandi locale | Container `4a15a6f8a390...`, immagine `c04832dd70e6...`, healthy; 847 file corrispondenti al sorgente, database e mount invariati |
| Lettore locale | PID 10044, supervisore 35440, launch 980f81b7b51f4efc80efa670578a3319; due health nuovi verificati, solo loopback |
| Nodo del portale | PID 57916, supervisore 62792, launch 6cd7f11d8e0446acad699531b5d4261b; due poll e successo gateway dopo l’avvio |
| Coordinatore | Timer originale riabilitato; primo ciclo concluso alle 05:05:15, cinque ACK, supervisore concluso correttamente |
| Zyb Instagram e Drive | Entrambi running/healthy e collegati alla stessa coda |
| Fabbrica | Nuova esecuzione v2.15 in corso, fase lettore; esito non ancora disponibile |
| Campania | Nuova richiesta 13531e4f-0da3-452c-ad9a-b5174c0ac58c in attesa dopo Fabbrica |

La ripresa ha conservato tutte le dieci richieste precedenti. Fabbrica ha mantenuto la richiesta già esistente; Campania ne ha ricevuta una nuova dopo revisione specifica del vecchio fallimento. Nessun comando globale di ripetizione o riavvio dei vecchi fascicoli è stato usato.

I task **Bandi - Preparazione progetti** ed **ETS Intelligence Graph - After Manual Docker** sono nuovamente abilitati con la cadenza originale. Due vecchi task di analisi/verifica erano già disabilitati e restano tali, per non avviare una seconda pipeline. I backup non sono stati modificati.

L’API usa ora un comando `exec` per trasferire i segnali al server. Il vecchio container con shell è uscito con 143, dopo verifica di assenza di writer, senza OOM o arresto forzato: questo non viene presentato come prova di drenaggio ASGI. Il vecchio container, la relativa immagine e i precedenti Zyb restano conservati. Tutti gli helper temporanei del passaggio API e della ricarica reader/Node hanno terminato.

### Analisi ancora in corso

**Lasciare il PC acceso.** La stima iniziale per i due fascicoli è da una a tre ore complessive e può variare; la prova Campania aveva richiesto circa 23 minuti per una sola lettura. La pagina del catalogo non deve restare aperta.

Fabbrica usa una nuova cartella, [run-20261102-040434-401919](<G:/Terzi settore/state/organization-verification/run-20261102-040434-401919/state.json>), runner **67188**, nascita **1793592275996**, identità **c3ded8d0ab8241d69c58623ed4f72067**, watchdog **69652** (nascita 1793592278686). Lo stato iniziale verificato è `running/reader`, metodo `compact-loop/v2.15`. Log: `runner.log`, `stdout.log`, `stderr.log`; supervisione: `watchdog.json` nella stessa cartella. La richiesta Campania riceverà un proprio nuovo fascicolo quando verrà avviata.

La verifica indipendente del processo conferma instradamento su 11435 e fascicolo completo: 29.612 token di ingresso, 38.828 richiesti incluse le riserve, entro il contesto di 65.536. Le 17 copie del codice della prova corrispondono alle fonti congelate.

Il successo richiede risposta nativa completa, validazione di schema e fonti, esito terminale coerente, proiezione sigillata e conferma della pubblicazione. `running` o `queued` non significano analisi riuscita. Un timeout, risposta troncata, controllo fallito o identità incerta deve lasciare un fallimento esplicito senza pubblicare frammenti e senza ripetere automaticamente il vecchio lavoro. Gli eventuali risultati completati vengono raccolti dal coordinatore ai successivi cicli.

Lo stato della coda è in [requests](<G:/Terzi settore/state/organization-verification/requests>); quello del coordinatore in [state.json](<G:/Terzi settore/state/bandi-catalog-controller-v1/state.json>) e `supervisor.json`. Il broker mantiene stato e log in `%LOCALAPPDATA%/BandiSuite/private/local-ai-queue-v1/state`. I supervisori Windows continuano a operare anche dopo questa conversazione.

## Dove sono le prove

Le ricevute tecniche sono conservate sotto `AssociaGo/deployment/.artifacts` e nelle cartelle private dei servizi. Non contengono nuove copie dei documenti nel database della coda.

| Verifica | Cartella delle ricevute |
| --- | --- |
| Elenco completo | `bandi-all-pages-readonly-20261102-a0b528d06b2246fbbe4ec5c6b9181580` |
| Coda, FIFO e Campania precedente | `nov2-ai-queue-independent-monitor-57c027cccccb4c2d81e882ee8f4c1890` |
| API nuova e conservazione precedente | `nov2-bandi-api-v215-image-5f4e9ecc13ea4ee5b8b843db1ab2b470/consolidated-final-receipt.json` |
| Formato v2.15 e ricarica servizi | `v215-output-bounds-de83f3c5e9984fd6b9248f139f6bafaf` |
| Worker online | `worker-incomplete-analysis-fix-20261102-7f109da0a3d141a1991c156ba21d6110/production-reconciled-receipt.json` |
| Ripresa coordinatore | `nov2-controller-resume-v215-43b6976647994b0e8113e4771225d754` |
| Zyb e backend attivo | `zyb-ai-queue-83f6cc5aa5e9435e9e2f670d2ff70ea0/ZYB-CODA-AI-COMPLETATA-20261102.md` |
| Pubblicazione sorgenti | `bandi-suite-publication-prep-20261102-416c40febba445548ff8515791c36c04` |

Copie dello stesso resoconto sono in `Bandi/integration/BANDI-CODA-AI-20261102.md` e `AssociaGo/deployment/BANDI-CODA-AI-20261102.md`. Le ricevute Git identificano commit remoti e file effettivamente pubblicati; i 77 artefatti Playwright estranei al rilascio restano esclusi e conservati.

Il resoconto precedente resta disponibile in [BANDI-INTERVENTI-20261101.md](<C:/Users/lange/Desktop/Github/Angeli del Futuro/BANDI-INTERVENTI-20261101.md>). Per i precedenti interventi sulle tre applicazioni sono conservati anche [LAVORI-COMPLETATI.md](<C:/Users/lange/Desktop/Github/Angeli del Futuro/LAVORI-COMPLETATI.md>) e [VERIFICA-COMPLETA.md](<C:/Users/lange/Desktop/Github/Angeli del Futuro/VERIFICA-COMPLETA.md>).
