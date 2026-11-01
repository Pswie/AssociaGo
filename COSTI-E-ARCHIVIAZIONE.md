# Spazio, costi e aggiornamento dei bandi

Nel successivo [collaudo completo](deployment/VERIFICA-COMPLETA-20261101.md)
è stata pubblicata una correzione del gateway Bandi. Il registro Google
misura ora **454.063.160 byte**, inclusi i livelli non ancora raccolti da
Google. I **52.196.016 byte** indicati come recupero in attesa sono livelli
non più utilizzati delle build già eliminate: Google documenta la raccolta
giornaliera dei livelli senza riferimenti nella
[guida alla rimozione delle immagini](https://docs.cloud.google.com/artifact-registry/docs/docker/manage-images#deleting_images).
Non sono file dell'associazione; non sottraggo questi byte prima del recupero
effettivo. La revisione precedente del nuovo gateway è conservata per il
ritorno alla versione precedente; nessun piano o impostazione di spesa è cambiato.

Aggiornamento del **1 novembre 2026**: l'utente ha autorizzato il collegamento
ufficiale Cloudflare e precisato che vuole una **cartella Angeli del Futuro**,
non un nuovo account. La cartella organizzativa è stata creata in
`C:\Users\lange\Desktop\Github\Angeli del Futuro`; l'account Cloudflare
collegato viene riutilizzato. Nessun nuovo account è stato creato.
Ha vietato di cancellare i file e ha poi autorizzato esplicitamente la
rimozione dei soli Docker Google obsoleti. Il trasferimento dati procede per
copia: gli originali locali e la copia attuale Supabase restano conservati.
La liberazione dello spazio occupato dalla copia Supabase è sospesa; non si
può presentare come già ottenuta conservando gli stessi payload nel database.

La copia è ora **pubblicata e verificata su Cloudflare Workers Free**,
nell'account esistente: 13 blocchi, 202.583.336 byte compressi. Sono stati
riletti tutti i blocchi dal servizio e verificati hash del compresso e dei
493.891.584 byte ricostruiti. File interni e API rifiutano accessi anonimi.
L'utente ha confermato Workers Free dal pannello; la conferma non deriva
da un accesso API alla fatturazione. Wrangler è autorizzato.

Il gateway è collegato e promosso al 100%, dopo 35 letture reali da Google.
Gli originali e la copia Supabase rimangono conservati: **nessuno spazio
Supabase è stato liberato**. Nessun piano a pagamento è stato attivato.
Lo stato operativo è in [CLOUDFLARE.md](../Angeli%20del%20Futuro/CLOUDFLARE.md).

## Quanto spazio usiamo davvero

Il collegamento ufficiale conferma l'organizzazione `Angeli del futuro aps`
sul piano **Free** e il progetto `tfeplskriazdybiampgg` attivo e sano.
La misura SQL delle 10:38 italiane conferma:

| Contenuto | Byte | MB decimali circa |
| --- | ---: | ---: |
| Database completo attuale | 230.823.603 | 230,8 |
| Copia del catalogo nelle tre tabelle `catalog_*` | 210.649.088 | 210,6 |
| Tutto il resto del database | 20.174.515 | 20,2 |
| File presenti nei bucket Supabase Storage | 0 | 0 |

La copia autonoma del catalogo misura 202.583.336 byte compressi; indici,
righe e strutture PostgreSQL spiegano la maggiore occupazione fisica.
Le 36 tabelle con 3.136.705 righe sono **dentro il file DuckDB**: su Supabase
il file è conservato in 194 blocchi, con due piccole tabelle di controllo.

**Circa il 91% dell'occupazione attuale è quindi una copia rigenerabile.**
Spostarla consentirebbe di avvicinare il database residuo agli attuali 20 MB,
salvo crescita nel frattempo e spazio effettivamente recuperato da PostgreSQL.
Non è una misura di una migrazione già completata.

Le tabelle consumano spazio: dati, indici, immagini e allegati binari inclusi.
Il database non è esaurito: oggi occupa circa il 46% dei 500 MB gratuiti.
Conservare una seconda copia completa durante un aggiornamento porterebbe
però la stima a circa 441 MB; la crescita del catalogo ridurrebbe il margine.

Sono presenti anche colonne binarie operative in `associago.attachments`,
`associago.associations`, `associago.institutional_signatures` e
`librepm.asset_blobs`. La verifica aggregata attuale trova **zero byte** di
allegati, loghi o firme in queste colonne. In futuro i file caricati qui
consumerebbero la stessa quota del database: bisogna considerare anche loro.

## Cosa significa restare gratis

Supabase Free include attualmente 500 MB di database, 1 GB di file e quote
separate di traffico. Database e Storage sono servizi distinti: spostare un
file dal database a un bucket libera la quota database, ma non elimina il
traffico Supabase. Il piano Free applica restrizioni al superamento delle
quote; non è un'autorizzazione ad acquistare Pro.
[Prezzi](https://supabase.com/pricing),
[superamento delle quote](https://supabase.com/docs/guides/platform/billing-faq#what-will-happen-when-i-exceed-the-free-plan-quota).

La copia completa richiede circa 203 MB ogni volta che un nuovo processo
server deve caricarla. Con 5 GB di traffico non memorizzato nella cache sono
circa 24 caricamenti completi teorici al mese, **prima** di contare gli altri
usi. Non sono 24 visite: il processo riutilizza il catalogo finché rimane
disponibile. Un riavvio può causare un nuovo caricamento.
[Traffico Supabase](https://supabase.com/docs/guides/platform/manage-your-usage/egress).

Un servizio gratuito può fermarsi alla quota. Per questo progetto la regola
è: **limiti espliciti e nessun passaggio automatico a un piano a pagamento**.
Non è possibile promettere disponibilità illimitata, spazio illimitato o
condizioni commerciali immutabili per sempre.

## Separazione scelta

| Tipo di dato | Destinazione | Motivo |
| --- | --- | --- |
| Soci, volontari, progetti propri, contabilità, bozze, revisioni e collegamenti | PostgreSQL Supabase Free | Dati modificabili, relazioni e transazioni già funzionanti |
| Copia consultabile del catalogo generale | Cloudflare Workers **Free**, Static Assets protetti | Copia immutabile e sostituibile, separata dal database operativo |
| Schede e documenti pubblici già disponibili presso la fonte | Collegamento verificato alla fonte, lettura su richiesta | Evitare duplicazioni inutili; mantenere cache solo dove serve |
| Novità rilevate sulle fonti e stato dei controlli | D1 Free separato, tetto applicativo di 5.000 schede | Aggiornamento quotidiano senza ricreare 203 MB |
| Originali, testi integrali, modelli e lavorazioni AI | PC | Mantiene l'elaborazione locale già richiesta |
| Nuovi allegati privati degli utenti | Storage privato separato, con quota propria | Evita che pochi PDF riempiano il database; richiede un intervento applicativo dedicato |

Non serve migrare tutte le tabelle dei gestionali per liberare la maggior
parte dello spazio. È possibile usare database esterni anche per nuovi dati,
ma separare dati legati da transazioni richiede una ragione concreta e
gestione della coerenza. Il catalogo rigenerabile è invece già separabile.

### Perché Cloudflare Workers Free per la copia

Gli Static Assets non hanno un costo aggiuntivo di archiviazione. Il limite
documentato è 25 MiB per file e 20.000 file per versione sul piano gratuito:
il catalogo va quindi suddiviso in blocchi più piccoli. Le letture devono
passare sempre dal Worker con `run_worker_first: true`, autenticazione tra
server e nessun percorso pubblico alternativo. Oltre la quota gratuita le
richieste protette ricevono 429, senza esposizione diretta dei file.
[Costi e comportamento al limite](https://developers.cloudflare.com/workers/static-assets/billing-and-limitations/),
[limiti](https://developers.cloudflare.com/workers/platform/limits/),
[protezione degli asset](https://developers.cloudflare.com/workers/static-assets/binding/).

Gli accessi protetti consumano le 100.000 richieste giornaliere Workers Free,
condivise dall'account. Il Worker trasferisce solo i blocchi: il server
esistente continuerebbe a verificare hash e interrogare DuckDB. Il Worker
dispone di 128 MB di RAM e 10 ms di CPU, insufficienti per eseguire tutto
il backend attuale.
[Prezzi Workers](https://developers.cloudflare.com/workers/platform/pricing/).

La pubblicazione reale ha verificato il passaggio obbligatorio dal Worker,
la chiave tra server, il rifiuto degli accessi anonimi e l'integrità completa.
Gli asset fanno parte di una pubblicazione: non sono un disco per gli upload
degli utenti. Non sono stati attivati R2 o Workers Paid.

D1 Free ospita il piccolo indice aggiornabile delle novità: offre
5 GB complessivi, massimo 500 MB per database e quote giornaliere di lettura
e scrittura. Anche le scansioni contano: servono indici e interrogazioni
limitate. Non richiede di convertirvi adesso tutti i gestionali.
[Prezzi D1](https://developers.cloudflare.com/d1/platform/pricing/),
[limiti D1](https://developers.cloudflare.com/d1/platform/limits/).

R2 ha una quota iniziale gratuita di 10 GB-mese, ma le eccedenze di spazio
e operazioni sono fatturabili: non è la prima scelta con il vincolo di
nessuna spesa automatica. Azure o altri servizi a consumo richiederebbero
la stessa verifica, senza presumere che un credito iniziale equivalga a
gratuità permanente. [Prezzi R2](https://developers.cloudflare.com/r2/pricing/).

Supabase Storage è un'alternativa più semplice per separare subito la quota
file da quella database, restando nello stesso account. Richiede blocchi
sotto il limite Free di 50 MB per file e accesso privato; rimangono il limite
di 1 GB e il traffico condiviso, quindi non risolve l'intero problema.
[Limiti file](https://supabase.com/docs/guides/storage/uploads/file-limits).

## Costi dell'hosting esistente

La lettura Google conferma la fatturazione **abilitata** sul progetto attuale.
È presente `adf-collaudo-avviso-1-euro`, un budget mensile di avviso da 1 EUR,
oltre al budget `PROVA`. Non risultano spend cap in questa lista. **Un avviso
non è un blocco della spesa.** Questo controllo non quantifica la fattura già
maturata. Le quote Cloud Run e del registro immagini sono condivise sul conto.
[Prezzi Cloud Run](https://cloud.google.com/run/pricing),
[budget di avviso](https://docs.cloud.google.com/billing/docs/how-to/budgets).

Google documenta ora anche spend cap per Cloud Run, ma precisa che
l'applicazione non è istantanea e le eccedenze dovute al ritardo vengono
fatturate. Non coprono automaticamente altri servizi, come il registro
immagini. Non costituiscono quindi una garanzia assoluta di spesa zero.
[Spend cap Google](https://docs.cloud.google.com/billing/docs/how-to/budgets-spend-caps).

L'API Vercel delle spese ha restituito `costs_not_found` per l'intervallo
esaminato: **non è una prova di fattura zero**. Non ho modificato piani,
metodi di pagamento o servizi attivi. Spostare il solo catalogo libera spazio
Supabase, ma non rende automaticamente gratuito tutto l'hosting della suite.

La verifica del 1 novembre conta **453.904.007 byte in 87 file unici** nel
registro Google, con i layer condivisi contati una volta. Il confronto
prudenziale con 500 MB lascia circa 46 MB; la franchigia è condivisa sul
conto, quindi non è una garanzia per altri progetti. Sono conservate sette
revisioni della suite, cinque versioni di segreti abilitate e un job di
collaudo completato senza pianificazione. [Misure e due immagini](deployment/VERIFICA-IMMAGINI-GOOGLE.md).
Questa è una misura per file unici; non va interpretata come una nuova
eliminazione confrontandola con le precedenti somme per immagine.

La successiva richiesta di eliminare le vecchie immagini Docker è stata
eseguita: sei build obsolete e quattro revisioni senza traffico rimosse,
tre build attive conservate. Dopo la pulizia risultano **453.876.143 byte
in 67 file**, tre revisioni attive, cinque versioni di segreti e il job di
collaudo conservati. Il dato di 87 file sopra è l'inventario precedente.
I 27.864 byte dei manifest sono già rimossi; altri 52.196.016 byte di blob
esclusivi attendono il garbage collector Google. La liberazione potenziale
non va confusa con lo spazio fisico già recuperato.

## Ricerca quotidiana dei bandi: requisito da preservare

Consultare una copia del catalogo e cercare nuovi bandi ogni giorno sono
operazioni diverse. La copia completa può aggiornarsi meno spesso; la
ricerca delle novità deve avere uno stato proprio e aggiornare un piccolo
indice senza ricreare ogni giorno 203 MB di archivio.

Il job cron-job.org `8758073` resta dedicato alla manutenzione ogni 30 minuti.
È stato aggiunto **`8766630`** per il nuovo controllo delle fonti: POST
privato ogni 5 minuti, con almeno 15 minuti tra elaborazioni e 24 ore tra due
controlli della stessa fonte. Chiavi di lettura e del cron distinte.
Nessun cron GitHub aggiunto.

La prima copertura online comprende quattro indici: Fondazione CON IL SUD,
Fondo per la Repubblica Digitale, Dipartimento Politiche Giovanili e PN
Inclusione. Il primo controllo cloud ha raccolto 50 schede CON IL SUD in
circa 70 KB di D1. Il primo giro reale completo ha raccolto 87 riferimenti
sulle quattro fonti, tutti con esito riuscito. I picchi CPU 14/15/8/14 ms
sono stati accettati grazie all'elasticità per picchi infrequenti: non
costituiscono un margine garantito sui 10 ms nominali Free. Le schede indicano quando sono state
rilevate; non inventano pubblicazione, scadenza o stato ancora aperto.

Il registro sul PC conserva una copertura più ampia: 31 fonti e 37 risorse,
non 75 crawler. Il task reale del 1 novembre ha controllato le 37 risorse
prima di arrestarsi per un errore di logging, ora corretto. Errori, URL
rimossi e blocchi restano visibili. Il nuovo servizio online non sostituisce
automaticamente tutti gli adapter locali. [Copertura ed esiti](../Bandi/integration/RICERCA-QUOTIDIANA.md).

Il vecchio caricamento PostgreSQL è stato disabilitato impostando soltanto
`publishEnabled=false`, con copia integrale della configurazione precedente.
Non deve essere riattivato per aggiornare Cloudflare: usare un nuovo
pacchetto privato e Wrangler. Nessuno snapshot o blocco Supabase cancellato.

## Ordine del trasferimento

1. Verificare Workers Free nell'account Cloudflare già collegato e usare
   risorse nuove con prefisso `angeli-del-futuro`, senza creare account,
   attivare R2, Workers Paid o altri prodotti a consumo.
2. Preparare l'adattatore esterno e provare accessi negati, blocchi mancanti,
   file alterati, scadenze e quote, preservando i permessi Clerk.
3. Caricare la copia già verificata, controllare hash completi e letture
   previste, poi cambiare il lettore e il publisher del PC e verificare il
   sito. Disabilitare le vecchie configurazioni di caricamento su Supabase:
   un aggiornamento successivo non deve riempire di nuovo le tabelle cache.
   Allineare anche backup, ripristino e configurazione operativa corrente.
4. Conservare la copia originale sul PC, la copia Supabase e ogni possibilità
   di ripristino. **Non rimuovere i payload esistenti:** il divieto successivo
   dell'utente prevale sul precedente piano di pulizia. Una futura liberazione
   dello spazio richiederà una nuova indicazione esplicita; conservare la
   vecchia copia lascia invariata quella parte dell'occupazione Supabase.
5. Attivare e verificare la ricerca quotidiana separata sui siti dichiarati,
   con esecuzione effettiva e controllo delle novità persistite.
6. Adeguare gli upload futuri per salvare allegati privati fuori dal database
   operativo, con limiti e controlli prima del caricamento.

Finché il nuovo percorso non è verificato, la copia funzionante su Supabase
rimane al suo posto. Gli originali del PC e le tabelle operative non sono
inclusi nella pulizia.
