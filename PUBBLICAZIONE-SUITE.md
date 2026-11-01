# Lavori sulla suite Angeli del Futuro

Aggiornato il 1 novembre 2026. Questo è il resoconto operativo corrente.
[LAVORI-INTEGRAZIONE.md](LAVORI-INTEGRAZIONE.md) descrive la prima integrazione;
[MIGRAZIONE-SUPABASE.md](MIGRAZIONE-SUPABASE.md) la conversione dei database.

Il successivo [collaudo completo del 1 novembre](deployment/VERIFICA-COMPLETA-20261101.md)
ha trovato e corretto il nodo PC fermo e l'errore sulle schede progetto con
riferimenti alle fonti strutturati. Il gateway pubblicato è ora
`adf-bandi-portale-api-cf-20261101-c`; la precedente revisione è conservata.
Le prove nuove comprendono 210 test di database/sincronizzazione/nodo,
17 test di dominio, 820 test frontend Bandi, 58 test degli altri frontend,
18 GET peer reali, 28 consultazioni PC e 35 verifiche dall'immagine Google.
Il collaudo del login nella sessione reale resta distinto: Opera continua
a richiedere una nuova autenticazione. I dettagli e i limiti sono nel rapporto.

Il controllo successivo su spazio e rischio di spesa è in
[COSTI-E-ARCHIVIAZIONE.md](COSTI-E-ARCHIVIAZIONE.md): circa 211 MB degli
attuali 231 MB sono la copia rigenerabile del catalogo. Lo spostamento su
Cloudflare è ora pubblicato e verificato integralmente; il gateway aggiornato
è in produzione dopo 35 letture reali riuscite da Google. Il vincolo resta nessun passaggio automatico a piani a pagamento.

È stata pubblicata anche la pulizia della pagina Fonti: rimossi gli avvisi
tecnici ripetitivi, mantenuti date, filtri e messaggi utili. Deployment Bandi
`dpl_7mKGGr32kPV1p2s1cLtoJCT9bGn4`, con 15 test del componente, TypeScript,
lint e dieci controlli pubblici superati. La ricerca quotidiana dei bandi è
un requisito distinto: il controllo ha trovato un problema nell'avvio locale,
non una ricerca cloud già attiva; esiti e copertura sono nella guida dedicata
[RICERCA-QUOTIDIANA.md](../Bandi/integration/RICERCA-QUOTIDIANA.md).

Il 1 novembre è stata riparata anche l'API locale: immagine riallineata alla
migrazione `0033_suite_projects` già applicata, salute HTTP200 e database,
container PostgreSQL e volumi invariati. Il flusso quotidiano ha superato
99 verifiche mirate dopo l'isolamento del dispatch dei bandi; non è stata
dichiarata una scansione remota mai avviata. Il collegamento Cloudflare è ora
autenticato. L'utente ha precisato di volere una cartella «Angeli del Futuro»,
non un nuovo account: è stata creata la cartella organizzativa
`C:\Users\lange\Desktop\Github\Angeli del Futuro`, con i riferimenti ai
repository e ai resoconti; l'account Cloudflare esistente è stato riutilizzato.
Nessun nuovo account è stato creato. Da questa indicazione dell'utente in poi
i file locali, gli originali e la copia Supabase restano conservati. La
successiva richiesta esplicita «le vecchie immagini vanno cancellate» ha
autorizzato la sola pulizia dei Docker Google obsoleti, conclusa dopo
controllo globale dei riferimenti; non autorizza la pulizia Supabase.

In [Bandi → Fonti](https://bandi.angelidelfuturoaps.com/fonti) è ora pubblicata
anche una copia generale dei metadati: enti, progetti, bandi, graduatorie,
relazioni e dati economici. Comprende **36 tabelle e 3.136.705 righe** in
**202.583.336 byte compressi**. Il progetto Supabase Free esistente occupa
complessivamente circa **231 MB**. Gli originali, i PDF, i testi integrali e
le lavorazioni locali restano sul PC. Le cinque fonti ufficiali live rimangono
disponibili. Copertura, misure, permessi, limiti e comandi di aggiornamento
sono in [ARCHIVIO-CLOUD.md](../Bandi/integration/ARCHIVIO-CLOUD.md).

## Aggiornamento del 1 novembre: archivio e novità

- [x] Cartella organizzativa «Angeli del Futuro», senza account cloud nuovi.
- [x] Copia privata Cloudflare: 13 blocchi, hash completi verificati da remoto.
- [x] Worker obbligatorio; accessi anonimi e chiavi errate rifiutati.
- [x] Vecchio publisher PostgreSQL disabilitato, configurazione precedente conservata.
- [x] API locale recuperata senza cambiare schema o volumi del database.
- [x] Vecchio evento fermo prima dell'apply conservato e superato con ricevuta,
  senza dichiararlo artificiosamente completato.
- [x] Nuovo indice D1 e job cron-job.org `8766630`, ogni 5 minuti, con limiti interni di 15 minuti e 24 ore per fonte.
- [x] Nuova pagina `/novita`, con lo stesso Clerk e accesso dei collaboratori.
- [x] Completare 35 letture reali da Google e promuovere il gateway al 100%.
- [x] Pubblicare il frontend Node 22: 208 regressioni e 13 confronti HTTP esatti.
- [x] Verificare il primo giro reale: quattro fonti riuscite, 87 riferimenti, nessuna chiamata manuale.

Le quattro fonti online sono CON IL SUD, Fondo per la Repubblica Digitale,
Politiche Giovanili e PN Inclusione. Il controllo conserva riferimenti e
metadati, senza scaricare PDF o avviare AI. «Rilevato il» non significa
«pubblicato il»; stato aperto e scadenze vanno verificati sulla fonte.
Il primo giro reale ha raccolto 50 schede CON IL SUD, 25 PN Inclusione,
4 Politiche Giovanili e 8 Repubblica Digitale. Esiti tutti riusciti. I picchi
CPU 14/15/8/14 ms sono stati accettati dal runtime; non sono un margine
garantito rispetto ai 10 ms nominali Free. Nessun piano Paid attivato.

La copia Supabase di circa 211 MB resta conservata secondo il divieto di
cancellazione. Il database occupa ancora circa 231 MB: lo spazio non è
stato recuperato. Soci, volontari, progetti propri, contabilità e bozze
restano nei tre schemi privati.

Le due immagini Docker di Gestionale e Progetti, richieste successivamente,
sono state controllate in sola lettura: digest e JAR corrispondono agli
originali pubblicati, entrambi i servizi sono sani. Il primo timeout del
Gestionale dipendeva dall'avvio a freddo: 43,288 secondi di startup e prima
risposta 47,097; a caldo 0,574 secondi. Progetti 0,428 secondi. Nessun
riavvio o cambio di configurazione per questo controllo. Le dimensioni
compresse sono circa 224,64 e 200,30 MB; i dieci layer comuni non vanno
contati due volte. [Verifica delle immagini Google](deployment/VERIFICA-IMMAGINI-GOOGLE.md).

Il gateway corrente legge l'archivio da Cloudflare, senza riusare le tabelle
cache PostgreSQL. È stato corretto anche un errore asincrono intercettato
nelle prove ASGI; la nuova immagine è stata collaudata dal runtime Google.
Il bundle runtime Bandi è ora versione 2, con le credenziali applicative
precedenti conservate e due chiavi di lettura distinte aggiunte.
[Come aggiornare il rilascio corrente](deployment/AGGIORNARE-BANDI-CLOUDFLARE.md).

Sono state eliminate **sei build Docker obsolete**, corrispondenti a
20 versioni OCI, e quattro revisioni senza traffico. Restano le tre build
in uso, con i nove manifest necessari, il job di collaudo e i segreti
precedenti. Tutti i servizi rispondono 200 dopo la pulizia. Dei 20 elementi,
14 sono stati rimossi con comandi SDK e sei attestazioni correlate dal
provider; nessuna cancellazione di file locali, database o dati Cloudflare.

Il registro misura ora **453.876.143 byte in 67 file**. La rimozione dei
manifest ha liberato 27.864 byte; circa 52,20 MB di livelli non più usati
attendono la raccolta automatica del provider. Non sono presentati come
spazio già recuperato. Le regole generiche di cancellazione automatica
restano in simulazione; le immagini sono state eliminate con una lista
esplicita verificata, proteggendo quelle attive.

I sorgenti Bandi di questo rilascio sono pubblicati e verificati su GitHub:
commit `2705d3d6f3ad36862c0baf03a2ec0f9798bd7ac6`, 59 file nel ramo
`agent/phase-4-structured-extraction` della repository privata
`Pswie/ets-intelligence-graph`. La CI include i test del runtime Cloudflare;
la ricerca quotidiana resta su cron-job.org, non su GitHub.

## Stato effettivo

Il nuovo account Supabase è stato collegato tramite OAuth ufficiale. Il progetto
`tfeplskriazdybiampgg`, nell'organizzazione «Angeli del futuro aps»
(`zdhnjznqpzdwuatqobmo`), è attivo sul piano **Free**, regione `eu-west-1`,
PostgreSQL17.11. È stato verificato vuoto prima degli interventi. Sono stati
creati i tre schemi privati e sei ruoli separati, verificando privilegi minimi
ed esclusione dalla Data API. Il precedente progetto Tabaccheria non è stato
modificato. Aiven era vuoto e non serve recuperarlo.

Le migrazioni applicative sono state applicate: tre per AssociaGo, quattro per
LibrePM e cinque per il portale Bandi. Il tuo utente Clerk verificato è collegato
ai gestionali; non sono stati inventati soci, progetti o movimenti contabili.
Il collegamento usa il Session pooler verificato
`aws-0-eu-west-1.pooler.supabase.com:5432`, con controllo completo TLS e CA
Supabase verificata. Non serve acquistare l'opzione IPv4.

Il gateway Bandi è già attivo su Google: salute 200, dati anonimi 401,
manutenzione autenticata con query reale200 e API dell'archivio locale404.
Le nuove revisioni Java sono state promosse al100% dopo i controlli su
salute, accesso e scambi fra server. I quattro frontend sono pubblicati sui
domini indicati sotto. Sono pubblicate anche la pagina di accesso essenziale
con tutte e cinque le aree e la correzione del trasferimento della sessione
fra i siti. L'utente ha confermato l'accesso all'area interna Servizi al cittadino;
il successivo collaudo ha mostrato un blocco nel caricamento del profilo
Progetti e un errore del Gestionale. È stato individuato un prefisso `/api`
mancante nella configurazione di pubblicazione dei due client Java. La
correzione è ora pubblicata insieme alla gestione delle attese del profilo;
è stato verificato anche il prefisso nel JavaScript realmente servito dai due
domini pubblici. Il successivo collaudo dell'utente ha confermato che
**Gestionale e Progetti si aprono entrambi**, mantenendo l'accesso. Per Bandi
è stata verificata anche una richiesta autenticata riuscita al profilo del
portale; sono ora pubblicate anche le schermate originali e il collegamento
alle operazioni native. Il primo collaudo dell'utente ha segnalato caricamenti
troppo lunghi nelle pagine Bandi. Sono state individuate l'attesa delle code
vuote e le troppe richieste per ricomporre gli elenchi grandi; le correzioni
sono pubblicate. Dopo la ricarica l'utente ha confermato **«Ora vedo l'elenco»**
sulla pagina RUNTS. Questa conferma riguarda quel caricamento e l'accesso,
senza dichiarare collaudata ogni singola operazione delle31 pagine.
Il primo backup
reale dei tre schemi è riuscito; la verifica dell'archivio non equivale a una
prova di ripristino dell'intero database reale.

Il nome visualizzato del progetto era erroneamente `info@cmt-ig.org` già nella
prima lettura. L'utente ha confermato il link allo stesso progetto e lo ha
rinominato dal pannello, perché il MCP ufficiale non espone il cambio nome.
La successiva lettura ufficiale conferma **Angeli del futuro**, nello stesso
progetto e organizzazione. Non sono stati creati altri database o spostati dati.

Vincolo dell'utente: **Supabase solo Free, nessun piano a pagamento**. Il
progetto è già creato e non ne serve un altro.

## Architettura scelta

Tre siti con la stessa istanza Clerk, collegati da un UUID comune del progetto.
LibrePM è il registro autorevole; AssociaGo gestisce persone, turni e rendiconto;
Bandi bozze e candidature. Titoli uguali non determinano collegamenti automatici.
Un progetto Supabase dedicato ospita tre schemi privati:

| Schema | Contenuto | Account applicativo |
| --- | --- | --- |
| `associago` | Soci, volontari, progetti e contabilità | `associago_app` |
| `librepm` | Progetti operativi e registro comune | `librepm_app` |
| `bandi_portal` | Bozze, revisioni, coda, progetti comuni e copia privata dei metadati | `bandi_portal_app` |

Le migrazioni usano account proprietari distinti. Gli account applicativi non
possono creare tabelle, leggere gli altri schemi o modificare la storia delle
migrazioni. Le revisioni delle bozze sono append-only anche nei permessi SQL.
Gli schemi devono rimanere fuori dalla Data API Supabase.

Il browser usa Clerk e le API delle applicazioni. La connessione PostgreSQL
rimane sui server: il modello Next.js con Supabase Auth e un secondo middleware
di sessione non serve per questa architettura.

L'archivio completo Bandi, PDF, indici vettoriali, Ollama e Agno rimangono sul PC.
Una proiezione compatta e immutabile dei metadati è ora consultabile dal cloud.
Il ruolo applicativo conserva la sola lettura delle tre tabelle della copia
precedente; il gateway attuale usa invece la chiave privata Cloudflare. Il
vecchio publisher PostgreSQL sul PC è disabilitato. Le tabelle conservate
rimangono private e non sono esposte dalla Data API.
La variante opzionale con tutto Bandi su PostgreSQL è collaudata, ma non è il
modello scelto per questa pubblicazione. Il collegamento fra app è versionato:
segnala indisponibilità e conflitti, senza promettere transazioni distribuite
istantanee o sovrascrivere silenziosamente il lavoro.

## Funzioni sviluppate e problemi corretti

La revisione successiva del frontend è descritta nel registro
[FRONTEND-MIGLIORIE-SUITE.md](FRONTEND-MIGLIORIE-SUITE.md). Gestionale e Progetti
sono già aggiornati online: nome e logo ufficiale, navigazione e ricerca più
chiare, errori recuperabili e uso da telefono. Nel Gestionale sono corretti
modifica eventi senza duplicati e date locali; le Presenze distinguono le
vecchie anagrafiche dalle nuove iscrizioni, senza abbinamenti per nome.
Le Attività applicano ora il contesto dell'associazione anche sul server e
proteggono dati economici, partecipanti e istruttori. Chiusa la possibilità di
crearsi un'iscrizione in un'altra associazione o riassegnare un'identità Clerk
tramite un POST anagrafico. Non si dichiara completato il supporto generale a
più associazioni indipendenti per gli altri moduli storici.

Prima della migrazione V3 è stato creato un nuovo backup dei tre schemi,
verificato con indice pg_restore e digest. Per questa revisione sono passati
270 test server senza salti e 18 test frontend, oltre a lint, build e controlli
mobile. I dettagli delle Presenze e dei permessi sono in
[PRESENZE-E-SICUREZZA-ATTIVITA.md](PRESENZE-E-SICUREZZA-ATTIVITA.md).

- Nell'audit iniziale di AssociaGo corretti comunicazioni e destinatari,
  prestiti e restituzioni, rimborsi e doppi pagamenti, import CSV soci,
  modifica soci/attività, ricerca volontari e allegati autenticati.
- Corretti percorsi UI incompleti, errori di caricamento, layout mobile,
  navigazione per ruolo, sessioni desktop, backup SQLite e packaging Electron.
  I vecchi pulsanti che simulavano importazioni/export non eseguiti ora
  segnalano il limite reale. Dettagli: [AUDIT-TODO.md](AUDIT-TODO.md).
- Completato Progetti di AssociaGo: creazione/modifica, selezione, assegnazioni,
  turni, ore svolte e collegamento dei movimenti contabili già esistenti.
- Aggiunti controlli sull'associazione e sui permessi, anche alle vecchie API
  dei turni. Riepiloghi contabili esclusi ai ruoli senza accesso.
- Implementati registro comune, collegamenti persistenti, revisioni sorgenti,
  importazioni idempotenti e gestione dei conflitti in LibrePM.
- Corrette cancellazioni logiche di entità versionate, importi, allegati binari,
  descrizioni lunghe e identificativi testuali nel passaggio a PostgreSQL.
- Convertiti e verificati i database Java, conservando le varianti precedenti.
- Creato il gateway Bandi separato dall'archivio: i servizi AI e il corpus
  rimangono sul PC. Le operazioni del proprietario passano attraverso un
  catalogo finito, autenticato; non è accettato un comando o URL arbitrario.
- Implementati accesso Clerk verificato dal backend, bozze proprie per i
  collaboratori, vista di tutte le bozze per il proprietario, revisioni,
  rilevamento delle modifiche simultanee e cancellazione logica.
- Implementati nodo PC in uscita, coda limitata, lease e ricevute persistenti.
  Una generazione e una lettura breve possono procedere insieme, con un solo
  writer. Un'interruzione richiede retry esplicito e controllo del lavoro esistente.
- Aggiunto il mirror cloud del progetto comune e la scelta della bozza
  principale riservata al proprietario. Solo i metadati approvati sono condivisi;
  una modifica privata successiva non cambia il riepilogo in LibrePM.
- Conservato l'UUID fra i tre siti. Un riferimento inesistente mostra l'errore
  e non seleziona un altro progetto.
- Aggiornate dipendenze web vulnerabili di Bandi e ingressi del sito principale
  per distinguere Gestionale, Gestione progetti e Bandi e bozze.

## Comportamento Bandi

| Persona/situazione | Funzioni |
| --- | --- |
| Collaboratore autorizzato | Metadati condivisi, catalogo consentito quando il PC è acceso, proprie bozze online |
| Proprietario | Sito originale completo: 31 pagine, 15 voci originali più bozze, dati e operazioni del PC, incluse generazioni |
| PC spento | Bozze e progetti comuni online; schermate e operazioni locali segnalano l'indisponibilità |
| PC acceso con nodo collegato | Il proprietario consulta e usa il sito originale; i collaboratori mantengono il portale limitato |

La voce **Bandi** nell'Area riservata apre ora
<https://bandi.angelidelfuturoaps.com>. Si riusano i componenti e i servizi
originali, con Clerk e un trasporto verso il PC; non è stata costruita una
seconda applicazione con un archivio diverso. Le bozze sono sempre raggiungibili
anche da <https://bandi.angelidelfuturoaps.com/collabora>.

Confrontati in sola lettura il servizio locale Docker e il nodo Windows:
stesso database, schema, credenziale e archivio fisico. Allineata anche la
configurazione originale di modelli e Agno. La nuova integrazione dei progetti
si attiva soltanto con le azioni del proprietario **Condividi**, **Collega** e
**Allinea le tre aree**. Condivide metadati approvati e UUID, senza copiare
documenti o testi privati. La riconciliazione esplicita recupera un collegamento
già effettuato se si interrompe la conferma al gateway; non rigenera documenti.

Le richieste sono cifrate a riposo e temporanee; letture, comandi e interrogazioni
AI hanno corsie distinte. File e immagini richiesti passano a blocchi di 512KiB,
con digest e limite complessivo di 50MiB, prima di raggiungere le Functions
Vercel. Il sito controlla attese, annullamenti e richieste già eseguite.
Le API native e di amministrazione non vengono pubblicate come un proxy libero.

Nel primo riscontro reale sono state analizzate54 letture, tutte concluse sul
PC con HTTP200: l'attesa era soprattutto nella coda, fino a45,81 secondi.
Il nodo ora evita di interrogare le corsie vuote a ogni lettura. Il frontend
ricompone gli elenchi grandi con quattro blocchi da512KiB insieme: il caso da
3,75MiB richiede non più di30 chiamate, invece di circa465. Un limite effettivo
di180 secondi comprende rete e corpo della risposta, con errore visibile se
il collegamento si blocca. Sono passati470 test web; il nuovo sito pubblico
corrisponde alla candidata verificata. L'utente ha confermato il caricamento
RUNTS dopo l'aggiornamento; un trasferimento reale oltre4,5MiB resta da provare.

Prima di aggiungere la sola tabella locale `suite_projects` è stato creato
un backup PostgreSQL completo da **5.187.311.859 byte**, conservato esclusivamente
sul PC e protetto per utente/SYSTEM. Verificati SHA256 e indice pg_restore;
applicata la sola migrazione `0033_suite_projects`. I conteggi delle **75 tabelle
e 3.375.773 righe preesistenti sono invariati**. Dettagli:
[DATABASE-LOCALE-AGGIORNATO.md](../Bandi/integration/DATABASE-LOCALE-AGGIORNATO.md).
La verifica dell'indice non viene presentata come un ripristino completo.


Il PC usa HTTPS in uscita: nessuna porta del router o database locale da
pubblicare. Il nodo è stato prima verificato in sola lettura. Dopo i controlli
su code vuote, writer assenti, modelli e tokenizer con digest fissati, è stata
abilitata anche la capability di generazione: il gateway la riserva al solo
account proprietario verificato. **Nessun nuovo lavoro AI, ingestione o
generazione è stato accodato o eseguito durante la pubblicazione.** Il nodo
continua a controllare la coda mentre il PC è acceso e si avvia all'accesso a
Windows; non deve essere lasciato un terminale aperto. L'arresto/riavvio breve
per verificare il nome del progetto non ha eseguito lavori.

Ultimo aggiornamento operativo: 30 ottobre, 19:41 ora italiana. Nuovo worker
PID 66228 e supervisore PID 12676; stato `running/polling`, heartbeat del
gateway recente, cinque capacità attive incluse generazione e accesso nativo,
nessun errore o lavoro attivo nel controllo. Il vecchio nodo è stato arrestato
regolarmente con code e lease vuote, preservando identità, chiavi e ricevute.
Una precedente richiesta di sola ricerca era scaduta; è rimasta terminale,
senza ripetizioni automatiche. Il supervisore e l'attività Windows
continuano indipendentemente da questa chat. I PID cambiano ai riavvii; fanno
fede i file di stato e i log indicati sotto. Il risultato atteso è servire le
consultazioni e lavorare soltanto le generazioni autorizzate dal proprietario.

Guide: [contratto](../Bandi/integration/COLLABORAZIONE-CONTRATTO.md),
[gateway](../Bandi/docs/COLLABORAZIONE-GATEWAY.md),
[nodo PC](../Bandi/integration/NODO-PC.md),
[interfaccia](../Bandi/apps/web/COLLABORAZIONE.md).

## Controllo completo successivo delle pagine Bandi

Il controllo pagina, componente, client e proxy ha individuato e corretto
otto difetti: attesa di tutti i pannelli Radar prima di mostrare i bandi,
catalogo accessorio che bloccava il Laboratorio, polling sovrapposti delle
candidature, riuso del modulo dopo il cambio del bando, accesso bozze senza
scadenza, caricamento bozze dipendente dallo stato del PC, risposta grande
troncata dopo gli upload e un menu troppo largo su telefono.

La revisione finale supera **517 test web su85 file**, TypeScript e lint.
Il collaudo visivo usa i componenti originali e copre **31 modelli di pagina,
124 combinazioni desktop/telefono e dati vuoti/errori**, più ruoli, PC offline,
navigazione e tastiera. L'overflow riprodotto è corretto e verificato anche
a320,390 e1440px. Dopo i test è cambiato soltanto il CSS del campo interessato;
il nuovo collaudo completo e la build remota includono quel CSS. Conservati
anche gli esiti falliti intermedi, senza presentarli come prove riuscite.

Le API sono simulate nella fixture visiva; non si dichiara completato un
CRUD reale su ogni record o una visita autenticata di tutte le pagine.
Il collegamento Opera richiede ancora autenticazione e il controllo Windows
ha restituito un errore del collegamento nativo. Rimane valida la conferma
dell'utente sul caricamento RUNTS. Nessuna generazione o modifica dell'archivio
reale è stata eseguita per il collaudo. Supervisori e server temporanei sono
stati fermati e la loro chiusura è stata verificata indipendentemente.

Matrice, priorità, riproduzioni e limiti:
[CHECK-COMPLETO-BANDI.md](../Bandi/integration/CHECK-COMPLETO-BANDI.md).

## Archivio generale: aggiornamento del 31 ottobre

La consultazione non dipende più soltanto dalle API delle fonti. Una copia
DuckDB con compressione XZ conserva le relazioni e i tipi dei 36 gruppi di
metadati, senza record grezzi, vettori o testo integrale dei documenti. Il
database originale misurato era 12,7 GB; la proiezione nativa è 493.891.584 byte
e la copia compressa è 202.583.336 byte. Sono verificati tutti i conteggi,
la precisione degli importi e i digest dell'intero file decompresso.

Il tuo account vede **151.462 profili RUNTS** dell'ultima pubblicazione
presente, del 6 agosto 2026; sono conservati anche 151.173 profili della
pubblicazione precedente. I CSV originali mantengono il vincolo riservato:
la vista proprietario è scelta dal server, non da un parametro del browser.
I collaboratori continuano a vedere soltanto le proiezioni ammesse e possono
usare la ricerca RUNTS ufficiale. L'archivio consultabile contiene anche
**265.692 progetti e 957 bandi**, dopo i filtri di pubblicabilità.

Le schermate originali riusano 28 letture esistenti. Quando il PC è disponibile
e supporta il trasporto nativo, le schermate modificabili leggono i dati
aggiornati dal PC: una modifica non viene nascosta dalla copia precedente.
Quando è offline usano la copia cloud per quelle letture. La pagina Fonti
rimane una vista datata della copia e delle fonti live. AI, scritture e altre
operazioni native continuano a usare il PC; non tutte le 31 schermate sono
diventate autonome.

La copia usa 194 blocchi nello stesso progetto Supabase Free: nessun nuovo
account, bucket o piano. Il caricatore limita ogni copia a 205 MB e controlla
il database complessivo a 460 MB, mantenendo l'attiva durante la preparazione
della successiva. La misura successiva alla pubblicazione è **230.823.603
byte**; due generazioni dello stesso contenuto sono stimate a circa 441 MB,
non verificate con un secondo caricamento. La quota mensile di egress resta
condivisa: 5 GB equivalgono a circa 24 caricamenti completi a freddo prima
degli altri consumi. Il trasferimento PostgreSQL binario evita la duplicazione
in esadecimale. I backup conservano le strutture della cache, escludendone
i dati rigenerabili e mantenendo i dati applicativi.

Il rilascio è verificato con **422 test backend e 141 sottocasi**, **743 test
frontend**, TypeScript e lint. La stessa immagine Linux pubblicata ha superato
34 controlli reali dal server Google, incluse tutte le 28 letture, senza
usare la coda del PC. Il controllo nella costruzione dell'immagine verifica
JSON e timestamp, prevenendo la dipendenza `pytz` mancante individuata sul
container minimale. Il frontend pubblico corrisponde alla candidata verificata;
le API senza autenticazione restituiscono 401. Il controllo successivo dei tre
servizi ha superato 27 verifiche di configurazione e 18 verifiche HTTP.

Sono pubblicati `dpl_AwATKBUPkkYvAG31sfery4xmfHrQ` e la revisione gateway
`adf-bandi-portale-api-catalog-20261031-c`; il precedente gateway delle fonti
rimane disponibile per il rollback. I sorgenti Bandi corrispondenti sono
pubblicati nel commit `ee128728b8f1de10b973939cdc9ba641388b23a6` del ramo
`agent/phase-4-structured-extraction`. La navigazione delle 31 schermate nella
sessione reale del proprietario resta non verificata perché Opera richiede
riautorizzazione; nessun account è stato impersonato.

## Consultazione dalle fonti: fase del 30 ottobre

Il requisito aggiornato evita di trasferire tutto l'archivio. Sono state
verificate risposte reali delle API OpenCoesione, CON IL SUD, Repubblica
Digitale e Funding & Tenders; il codice RUNTS esistente riconosce ancora i
moduli ufficiali e può consultarli senza AI. La soluzione scelta è leggere
schede dalle fonti con adattatori server limitati e una piccola cache di
metadati. I documenti originali si recuperano quando il proprietario avvia
una lavorazione sul PC; bozze e lavoro associativo rimangono online.

Le fonti basate solo su file e i filtri senza API equivalente richiedono un
adattamento: non si promette una sostituzione identica di ogni funzione mediante
un semplice URL. Non sono state avviate scansioni dell'archivio, migrazioni
massive o attivazioni Cloudflare/Azure. La lettura è ora pubblicata su `/fonti`,
con tre sezioni per bandi, progetti ed enti. L'Area riservata è configurata per
aprire questo ingresso. Il proprietario può raggiungere anche gli strumenti
originali tramite **Analisi e archivio**; quei dati e le lavorazioni AI
continuano a richiedere il PC acceso. Le bozze rimangono online.

Ogni richiesta legge una pagina, senza scorrere automaticamente il catalogo.
La cache è soltanto in memoria: massimo 64 voci, 8 MiB e cinque minuti.
I collaboratori autorizzati possono consultare le fonti, senza acquisire
accesso alle operazioni native riservate al proprietario. Non sono stati
aggiunti un archivio PDF cloud o un nuovo comando «Scarica scheda»:
**Apri fonte** apre la scheda ufficiale dove disponibile. Per RUNTS apre il
portale generico; per OpenCoesione può aprire il record JSON completo.

Verifiche finali: **232 test PostgreSQL/ASGI e cinque subtest**, zero salti;
**663 test web in 89 file**, TypeScript e lint; 36 casi browser Fonti, oltre
ai 124 casi delle schermate native della fase precedente. Un job temporaneo
senza segreti ha letto tutte e cinque le fonti dal server Google, in circa
0,2–3,8 secondi ciascuna, ed è stato poi eliminato. Non sono state avviate
generazioni AI o lavorazioni sull'archivio reale.

Gateway in esercizio: `adf-bandi-portale-api-sources-20261030-a`, 100% traffico,
con la precedente `native-20261030-c` conservata per il rollback. Bandi Vercel
`dpl_Eh2bsaS1kQriCLF4QU9XdcmzrFDo`; sito principale
`dpl_8jK1uKpHjScZUWKhxChj6yWCG6UH`. Otto controlli dei domini pubblici
confermano pagine 200 e rifiuto anonimo 401 delle API; gli alias corrispondono
alle candidate promosse. I tre HTML Bandi coincidono byte per byte con le
candidate. Per `/admin` differisce soltanto l'ID casuale del widget Clerk,
normalizzato nelle sue due occorrenze. Il chooser autenticato e i clic sui
record non sono stati ispezionati nella sessione reale: Opera continua a
richiedere una nuova autenticazione. Le prove con fixture non vengono
presentate come una nuova conferma dell'utente.

Prove delle fonti, lettori riutilizzabili e piano concreto:
[ARCHIVIO-ONLINE-FATTIBILITA.md](../Bandi/integration/ARCHIVIO-ONLINE-FATTIBILITA.md).

I sorgenti di questa modalità e i test sono pubblicati nella repository privata
`Pswie/ets-intelligence-graph`, commit
`269190371b20bc4baa21c09e686149d874b176f0`, successivo al controllo completo
`309e90eb1935e844d6d2e6097d5d57a85d9bf8ea`. Nessun token o password è stato
aggiunto a Git. L'audit finale dei tre server supera 27 controlli di
configurazione e 18 richieste HTTP; la ricevuta separata della pulizia conferma
cinque revisioni rimanenti e nessun job temporaneo.


## Clerk e sessione comune

Installato e autenticato il [server MCP ufficiale Clerk](https://clerk.com/docs/guides/ai/mcp/clerk-mcp-server).
L'istanza usata dai siti contiene un solo utente, Pasquale, come indicato
dall'utente. Il proprietario configurato è
`user_3GyG1NastOd4H8LiZChA5g61g9O`. Nome, email e metadati modificabili non
assegnano quel permesso. Nessuna chiave segreta Clerk è stata recuperata.

È stata applicata e riletta la sola regola firmata
`session.claims.role = {{user.public_metadata.role}}`, preservando gli altri
parametri. Dettagli in [CLERK-CLAIMS.md](deployment/CLERK-CLAIMS.md).

L'istanza è di sviluppo. I normali link fra origini diverse non trasferivano il
contesto del browser: la navigazione e il ritorno ora usano gli helper ufficiali
Clerk, con destinazioni limitate ai nostri siti e UUID conservato. La pagina
centrale mantiene tutte e cinque le aree e un modulo di accesso essenziale.
**L'utente ha confermato l'apertura di Gestionale e Progetti con il login comune.**
Il MCP non espone la registrazione dei satelliti di sviluppo; questo non prova
che ne manchi la configurazione. Non sono state create istanze di produzione
o attivati piani a pagamento. La stessa chiave pubblica da sola non prova il
funzionamento SSO.
I collaboratori futuri vanno autorizzati tramite identificativo o organizzazione Clerk.

## Hosting e Docker

| Sito | Indirizzo pubblicato | Hosting |
| --- | --- | --- |
| Principale | [www.angelidelfuturoaps.com](https://www.angelidelfuturoaps.com) | Vercel esistente |
| AssociaGo | [gestionale.angelidelfuturoaps.com](https://gestionale.angelidelfuturoaps.com) | Vercel + Cloud Run `adf-gestionale-collaudo` |
| LibrePM | [progetti.angelidelfuturoaps.com](https://progetti.angelidelfuturoaps.com) | Vercel + Cloud Run `adf-bandi-collaudo` |
| Bandi | [bandi.angelidelfuturoaps.com](https://bandi.angelidelfuturoaps.com) | Frontend originale Vercel + gateway Cloud Run + metadati Supabase + nodo PC |

Il servizio Google chiamato `adf-bandi-collaudo` ospita LibrePM, non Bandi Python/Next.js.
Il dominio principale usa già DNS Vercel. Le immagini aggiornate sono nel
registro `europe-west8-docker.pkg.dev/flawless-star-503422-q3/cloud-run-source-deploy`.
I contesti Docker contengono soltanto JAR verificate o moduli gateway ammessi,
senza dati, configurazioni private o modelli del PC.
[build_runtime.py](deployment/build_runtime.py) registra heartbeat, PID,
digest e risultato terminale delle costruzioni.

Nella prima pulizia sono state rimosse **18 revisioni Google obsolete** senza
traffico o tag; dopo l'aggiornamento sono state eliminate anche una candidata
AssociaGo scartata, la precedente revisione del portale limitato Bandi e due
revisioni native sostituite: 22 revisioni rimosse fino al 30 ottobre. Con
questo rilascio sono state eliminate altre tre revisioni gateway superate:
**25 revisioni rimosse complessivamente**.
L'inventario globale successivo
conta **3 servizi, 0 job e 5 revisioni**: tre in servizio e le precedenti revisioni
AssociaGo/gateway conservate per il ripristino. Solo le nuove ricevono traffico.
Nei tre pacchetti Artifact Registry erano stati rimossi o verificati assenti
13 digest obsoleti; le nuove immagini sono controllate separatamente, compresi
index e manifest figli, prima di qualsiasi rimozione puntuale. Eliminato anche
l'index della candidata AssociaGo scartata dopo aver verificato che non fosse
usato da servizi o job in alcuna regione. Conservati i manifest figli e i layer
condivisi; nessuna cancellazione generale del registro.

La pulizia ha ricontrollato servizi e job in tutte le regioni del progetto,
riferimenti, tag e immagini prima delle cancellazioni puntuali. Due manifest
approvati sono risultati già assenti durante la procedura: il controllo si è
fermato, è stato riconciliato l'inventario in sola lettura e verificato il set
finale esatto. Le ricevute intermedie sono conservate insieme a
`deployment/.artifacts/cleanup-final-verified-20261030.json`. Non è stato
eseguito un prune generale del registro. I container locali e i volumi con i
dati Bandi sono conservati.

La pulizia del 31 ottobre ha inoltre rimosso soltanto cinque immagini superate
e i relativi 15 digest OCI, rileggendo riferimenti, traffico e tag prima e dopo
ogni operazione. Restano cinque revisioni della suite e zero job temporanei;
le tre API rispondono 200. Il registro riporta 502.710.003 byte, circa 0,468
GiB: la quota gratuita è aggregata sull'account e questa misura non costituisce
una garanzia di costo mensile zero. Non è stato eseguito alcun prune generale
né eliminato un volume o un originale sul PC.

È stata disconnessa la sola vecchia sorgente Git automatica del frontend
AssociaGo, che puntava alla copia nel monorepo precedente. La rilettura ha
confermato la disconnessione e la produzione invariata; il collegamento Git
del sito principale è conservato. I vecchi indirizzi canonici Vercel
reindirizzano ai nuovi domini mantenendo percorso e parametri del progetto.

## Cron e backup

`GET /api/collaboration/v1/maintenance/heartbeat` usa `X-Cron-Key`, distinto
dalle credenziali PC. Esegue una vera query del database, restituisce errori
senza segreti e non avvia generazioni.
[install_portal_cron.py](deployment/install_portal_cron.py) esporta il JSON per
cron-job.org e, con accesso API disponibile, crea/aggiorna esclusivamente il job
corrispondente: ogni 30 minuti, Europe/Rome, nessuna chiave nell'URL o risposta
archiviata. **Creato e verificato su cron-job.org il job8758073**, abilitato.
Il gateway risponde HTTP200 alla chiamata autenticata di controllo.
Verificata anche la prima esecuzione pianificata delle19:30 ora italiana:
HTTP200 in601ms, esito positivo nella cronologia di cron-job.org.
L'API key dell'account non viene salvata nella repository. Il launcher locale
[attiva-cron-portale.ps1](deployment/attiva-cron-portale.ps1) rimane disponibile
per modifiche future. Nessun cron GitHub o Google Scheduler è stato aggiunto.
Guida: [CRON-PORTALE.md](deployment/CRON-PORTALE.md).

Il cron non è un backup e non garantisce l'assenza di pause del piano Free.
È operativo [backup_cloud_schemas.py](deployment/backup_cloud_schemas.py), con
`pg_dump` PostgreSQL17, archivio custom dei tre schemi, manifest SHA256 e
verifica mediante `pg_restore --list`. Scrive fuori dalle repository, in
directory protette per l'utente, senza cancellare vecchie copie o esporre
credenziali nei comandi. La prova di ripristino reale ha usato una bozza e due
revisioni sintetiche: conteggi e hash coincidono dopo il ripristino. I due
database temporanei sono stati rimossi e la sorgente è rimasta intatta.
L'ultimo backup reale dei tre schemi, dopo le nuove migrazioni, è riuscito
alle 18:17 UTC: archivio custom di **488.846 byte**, SHA256
`729aa1126e4efe4b2059dd5e66b70f242b3459871bf564eea3427fcb28530991`
e indice pg_restore verificati. Usa il ruolo separato
`suite_backup`, TLS verify-full e la CA Supabase. Il task Windows è installato
e abilitato: alle 03:30, con recupero dell'orario perso e accesso Windows
ritardato, evitando sovrapposizioni e copie doppie nella stessa giornata.
Richiede PC acceso, utente connesso, Docker e rete disponibili; questa copia
non è fuori sede. Il ripristino dell'intero archivio reale non è stato eseguito.
Guida e comandi sono in [BACKUP-CLOUD.md](deployment/BACKUP-CLOUD.md).
Nessun piano a pagamento è stato attivato.
Le attività Windows si chiamano `Bandi Portal Local Node` e
`Bandi Suite Cloud Backup`. Stato e log del nodo sono nella cartella privata
`%LOCALAPPDATA%\BandiSuite\private\release-tfeplskriazdybiampgg\node-state`.
Riferimenti:
[pause Supabase](https://supabase.com/docs/guides/platform/free-project-pausing),
[backup Supabase](https://supabase.com/docs/guides/platform/backups).

## Verifiche e lavoro restante

- AssociaGo: ultima regressione270 test, PostgreSQL17/MySQL8.4/SQLite reali.
- LibrePM: 107/107 test, inclusi PostgreSQL/MySQL e integrazione; ulteriori
  25 verifiche sui ruoli Clerk passate. Un ruolo organizzativo esplicito non può
  riacquistare privilegi da metadati precedenti.
- Prima integrazione dei tre backend PostgreSQL: 19 verifiche native passate.
- Nuovo gateway con JAR LibrePM reale: 13 verifiche HTTP/PostgreSQL passate,
  inclusi scelta della bozza principale, riepilogo ricevuto da LibrePM,
  retry senza duplicati e due progetti con stesso titolo ma UUID diversi.
  Token OIDC/JWKS sintetici: questa prova non verifica il login Clerk reale.
- Gateway e adattatore: 47 test PostgreSQL e 27 regressioni locali passati;
  Ruff e mypy passati.
- Nodo: 23 test locali e due prove con gateway PostgreSQL passati.
- Bandi web: ultima suite completa663 test, lint, typecheck e build passati;
  ulteriori18 prove mirate del gate sono sovrapposte e non si sommano al totale.
  Flussi browser con dati sintetici verificati. Build web AssociaGo/LibrePM e
  check/build Astro passati.
- Backend pubblicati: 27 verifiche di configurazione e18 richieste HTTP
  passate sui tre indirizzi stabili;16 controlli offline dei preparatori e
  dell'audit di rilascio passati.
- Cron: dieci test offline passati; installazione reale su cron-job.org riletta
  e verificata, endpoint autenticato HTTP200.
- Backup: cinque test offline e prova di ripristino PostgreSQL17 reale passati;
  ulteriori sette test sul recupero delle esecuzioni Windows passati.
- Immagine gateway: salute200, anonimo401, API locali/documentazione404,
  manutenzione autenticata con query PostgreSQL riuscita; PUT/GET peer reali
  riusciti, altra organizzazione403 e progetti anonimi401.

Il vecchio controllo delle migrazioni complete su SQLite è stato sostituito
dalla verifica già disponibile su PostgreSQL reale, senza modificare migrazioni
storiche. Il nuovo job CI esegue esplicitamente upgrade fino a head, confronto
dei modelli, pgvector e controlli del portale con ruoli distinti: riprodotto in
locale con **232 test passati e zero saltati** nell'ultima regressione, inclusi
i 128 test nativi precedenti e 104 test delle fonti,
dopo gli allineamenti dei tipi. Black, Ruff e mypy passano sugli11 nuovi file
del trasporto e dei test. La CI generale conserva debito preesistente:
Ruff1.993 segnalazioni, Black131 file storici da formattare, mypy5.641 errori.
I269 nuovi errori di tipo introdotti durante lo sviluppo sono stati risolti;
i diagnostici mypy rimasti coincidono esattamente con la baseline. Non sono
stati esclusi controlli per dichiarare verde la CI. Dettagli:
[CI-DEBITO-TECNICO.md](../Bandi/integration/CI-DEBITO-TECNICO.md).
Le verifiche locali non equivalgono a un'esecuzione remota GitHub Actions.

La notifica GitHub «No jobs were run» del run `41075611405` dipendeva da un
contesto `runner.temp` usato nell'ambiente del job, dove non è disponibile.
La correzione sposta la risoluzione nel passo di preparazione e passa il
percorso ai passi successivi tramite `GITHUB_ENV`. Verificati il workflow
con actionlint ufficiale e la preparazione del file dei ruoli; correzione
pubblicata nel commit `c96605174d36dde4683564ace9cadac1930400ad` di
`Pswie/ets-intelligence-graph` e sincronizzata nella copia locale.
Il vecchio run resta un errore storico. **Non è stato aggiunto alcun cron
GitHub: la manutenzione richiesta resta esclusivamente su cron-job.org.**

**Audit Bandi completo e di produzione: zero vulnerabilità
note nell'esecuzione effettuata.** L'errore npm del workspace è stato risolto
preparando la riparazione del lockfile con npm11 in ambiente isolato: manifest
invariati, nessun override o major applicativo. Installazioni pulite verificate
con npm10 e npm11 e nuova regressione/build passati. Dettagli in
[NPM-AUDIT.md](../Bandi/apps/web/NPM-AUDIT.md).

- [x] Analisi, conversioni, implementazioni e collaudi locali.
- [x] MCP Clerk e identificazione del proprietario.
- [x] Costruzione e caricamento delle immagini backend su Google.
- [x] Preparare backup e verificare il ripristino reale di bozze e revisioni.
- [x] Collegare il nuovo account e predisporre schemi/ruoli sul progetto Free autorizzato.
- [x] Applicare migrazioni delle tre app e inizializzare le identità verificate.
- [x] Configurare segreti, identità, associazione e organizzazione.
- [x] Pubblicare revisioni, frontend e domini.
- [x] Verificare il login reale: l'utente conferma Gestionale e Progetti aperti.
- [x] Attivare nodo PC e backup contro le destinazioni verificate.
- [x] Collegare cron-job.org con l'accesso API dell'utente e installare il job.
- [x] Verificare con l'utente il caricamento RUNTS dopo le correzioni.
- [ ] Verificare un file oltre4,5MiB attraverso la sessione reale Vercel/Clerk.
- [x] Ripulire revisioni e immagini Google obsolete dopo il collaudo online.

Gli advisor Supabase dopo il rilascio non hanno restituito segnalazioni di
sicurezza. Le indicazioni informative sugli indici non sono state usate per
eliminare indici in un database appena inizializzato.

Le correzioni successive al primo collaudo SSO aggiungono 18 prove sui
callback AssociaGo/LibrePM, 9 sul portale Bandi e 13 sul sito principale,
con build delle quattro app riuscite. Il loro esito non sostituisce il test
con l'account dell'utente.

Una nuova prova di contratto esegue i veri client JavaScript dei due gestionali
con le variabili generate dallo script di pubblicazione: verifica le richieste
a `/api/v1/me`, `/api/v1/associations` e `/api/me` e l'intestazione Bearer.
Questo controllo intercetta il prefisso mancante che il solo esame della CSP
e della disponibilità HTML non aveva rilevato. Sono passati anche i cinque
controlli esistenti degli snapshot frontend.

È stato corretto anche il ciclo di riconoscimento di LibrePM: l'oggetto delle
organizzazioni cambiava a ogni render e riavviava il tentativo, cancellando
l'errore precedente. Ora l'effetto dipende dagli identificativi effettivi,
conserva l'errore fino a un nuovo tentativo esplicito e annulla le richieste
obsolete. Il recupero del token ha un limite di 15 secondi, il profilo di 75
e l'intera procedura di 90; un errore del token non avvia una richiesta anonima.
Passati 23 test API/helper/SSO e una prova del componente React in StrictMode
con 12 verifiche, inclusi errore persistente, retry e cambio utente.

Il controllo successivo in sola lettura conferma che esiste un solo utente
Clerk, con il subject previsto e ruolo pubblico `presidente`. AssociaGo ha
l'accesso tecnico `ADMIN/ACTIVE` all'associazione corretta; LibrePM lo stesso
subject nel profilo attivo. Non sono stati cambiati ruoli o dati per aggirare
gli errori di caricamento.

Il ritorno centrale gestisce sia i parametri normali sia il frammento usato
da Clerk. Cattura solo le tre destinazioni di ritorno prima che l'SDK ripulisca
l'indirizzo, poi elimina quei dati temporanei. Rifiuta destinazioni estranee,
valori discordanti e duplicati; non legge o trasferisce manualmente cookie o
token di sessione. È coperto anche il ritorno con sessione primaria già attiva.

Credenziali e ricevute operative rimangono in directory escluse da Git.
I sandbox temporanei usati nei collaudi sono stati fermati e rimossi con i
propri volumi. Durante la pubblicazione possono essere avviate ulteriori
verifiche controllate; non sono stati avviati worker AI sui dati dell'utente.

## Uso quotidiano e sviluppi separati

1. Entra da [Area riservata](https://www.angelidelfuturoaps.com/admin) e scegli
   Gestionale, Progetti o Bandi. L'obiettivo è una sola sessione Clerk.
   Le aree Agenda e servizi e Servizi al cittadino sono conservate.
2. In LibrePM apri il progetto, entra in **Integrazioni**, scegli **Condividi
   questo progetto** e **Allinea applicazioni**. Gli stessi pulsanti aprono il
   progetto preciso negli altri siti; puoi anche partire da AssociaGo.
3. In AssociaGo gestisci persone, turni e movimenti effettivi. In Bandi salva
   bozze e collega il progetto comune. La bozza principale si pubblica negli
   altri siti con una scelta esplicita del proprietario.
4. Il proprietario può richiedere una generazione su una bozza completa e un
   bando reale. Il PC elabora la coda; a PC spento le bozze restano online.

Non sono stati aggiunti dati dimostrativi nei gestionali pubblicati. I progetti
reali vanno creati dall'utente. I futuri collaboratori devono essere autorizzati
per identificativo o organizzazione Clerk; la sola registrazione non assegna
il controllo dell'associazione.

Restano sviluppi distinti dalla suite attivata: import partecipanti di eventi
con contesto esplicito, import/export completo e ripristino desktop guidato,
e supporto completo a più associazioni indipendenti nella stessa installazione.
Le vecchie anagrafiche non sono state fuse automaticamente. Le cartelle pensate
per funzionalità future non equivalgono a un prodotto completo di fundraising o
billing. Non sono stati inventati flussi amministrativi o regole mancanti.

I sorgenti aggiornati sono nelle copie locali delle repository. Su GitHub sono
pubblicati anche la correzione del workflow CI e il frontend LibrePM del commit
`78615261b43474f494e69564f4fff63c5aac1827` nel ramo `cloud/adf-bandi`,
corrispondente alla nuova interfaccia Progetti in esercizio. Il Gestionale
aggiornato è nel ramo `master` di `Pswie/AssociaGo`, commit
`00a6c6e516cbe54be8239d10f6386abc02b80b9c`. Le pagine di accesso e le
informazioni sul software collegano i rispettivi sorgenti aggiornati. Per i
rilasci successivi seguire [FRONTEND-VERCEL.md](deployment/FRONTEND-VERCEL.md)
e [DEPLOY-CANDIDATES.md](deployment/DEPLOY-CANDIDATES.md).

Il sito originale Bandi, gateway e regressioni sono pubblicati nella repository
privata `Pswie/ets-intelligence-graph`, ramo `agent/phase-4-structured-extraction`,
commit iniziale `8c1e30200ba4ed6516904103d14cf665e7e9cab9`; le correzioni dei
caricamenti sono nel commit `2b345772773ab334b6ac9ee8b3d2eaaddbaf5404` e descritte in
[CARICAMENTI-ONLINE.md](../Bandi/integration/CARICAMENTI-ONLINE.md).

## Residui temporanei

La pulizia di tre directory `node_modules` usate esclusivamente nelle prove
isolate è stata rifiutata dal controllo automatico dell'ambiente con
`blocked by policy`, senza ulteriori motivazioni. Non sono stati tentati
aggiramenti. Lo staging è conservato in
`%TEMP%\bandi-npm-hardening-439sit1q`; le dipendenze delle app reali sono integre.
