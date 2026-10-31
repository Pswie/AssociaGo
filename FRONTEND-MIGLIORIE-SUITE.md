# Revisione frontend e Bandi completo online

31 ottobre 2026. Piano operativo e registro degli interventi richiesti dopo
la conferma dell'utente che Gestionale e Progetti si aprono correttamente.
Il resoconto generale rimane [PUBBLICAZIONE-SUITE.md](PUBBLICAZIONE-SUITE.md).

L'ultimo rilascio aggiunge la copia generale dei metadati in Supabase Free:
36 tabelle, 3.136.705 righe, circa 203 MB compressi. Fonti mostra gli archivi
bandi, progetti e RUNTS, conservando le cinque fonti live e i permessi Clerk.
Le 28 letture originali previste usano la copia quando il PC è offline;
nelle schermate modificabili preferiscono il PC disponibile, evitando di
nascondere una modifica appena salvata. Sono passati 743 test frontend e
422 test backend, oltre alle letture reali del server Google. Copertura e
limiti sono in [ARCHIVIO-CLOUD.md](../Bandi/integration/ARCHIVIO-CLOUD.md).

## Obiettivo e confini

- Bandi: ritrovare online schermate, dati e operazioni dell'applicazione locale.
  Il proprietario ha confermato il controllo delle operazioni dal proprio
  account; gli altri utenti conservano consultazione autorizzata e proprie bozze.
- Archivio, modelli e lavori Bandi rimangono sul PC. Il sito inoltra richieste
  dichiarate al nodo già autenticato, senza pubblicare porte del computer.
- Correggere i frontend dei tre applicativi: navigazione, salvataggi, date,
  errori, ricerca, moduli incompleti, accessibilità e uso da telefono.
- Applicare nome e logo ufficiali, conservando attribuzioni e licenze dei motori
  AssociaGo e LibrePM e l'accesso Clerk già verificato.

Le caselle aperte indicano lavoro in corso, non funzioni già pubblicate.

## Identità verificata

Denominazione: **Angeli del Futuro APS**. Fonte dei dati già confermati:
`terzo-settore/sito/src/data/site.ts`. I dati assenti, come codice fiscale,
telefono e iscrizione RUNTS, non vengono inventati.

Logo: `terzo-settore/sito/public/brand/logo-ufficiale.png`, con la relativa
nota di provenienza. Deriva dai materiali PowerPoint forniti dall'utente;
viene copiato senza ridisegni o generazione di un altro marchio.
SHA256: `24bfb608601d5a3127dc9ee59b09917de11e1857c1b42c9d9560d1c3a662fa5d`.

| Area | Nome per l'utente | Motore conservato |
| --- | --- | --- |
| Soci, volontari e rendiconto | Gestionale | AssociaGo |
| Pianificazione e registro comune | Progetti | LibrePM |
| Archivio, candidature e generazione | Bandi | ETS Intelligence/Bandi |

Il nome del motore rimane nelle informazioni sul software e nell'offerta del
sorgente; non deve far confondere Progetti con l'applicazione Bandi.

## Direzione delle interfacce

La persona di riferimento è chi lavora nell'associazione: deve scegliere un
progetto, trovare le informazioni, registrare attività e continuare il lavoro
negli altri siti, anche da telefono. La priorità è il compito corrente.

Vocabolario: soci, volontari, turni, progetti, candidature, bozze e rendiconto.
Colori: blu e azzurro delle ali, corallo del marchio, superfici chiare e testo
scuro. Gli stati di errore, successo e indisponibilità restano distinguibili.
Il progetto selezionato e i collegamenti alle altre aree rendono riconoscibile
la suite, senza aggiungere slogan o spiegazioni tecniche nei flussi di lavoro.

Si riusano componenti e stili esistenti. Controlli raggruppati per compito,
gerarchia tipografica leggibile, spaziatura su base 4px, pulsanti raggiungibili
da tastiera e obiettivi tattili adeguati sul telefono. Logo proporzionato su
superficie leggibile, senza deformazione. Nessuna parete di statistiche vuote
o falsamente positive: caricamento, assenza di dati ed errore sono stati diversi.

## AssociaGo: difetti individuati e interventi

- [x] Audit delle schermate e dei percorsi di modifica.
- [x] Correggere modifica evento che poteva creare un duplicato.
- [x] Conservare data e ora locali nei moduli eventi e assemblee.
- [x] Completare il frontend Presenze con identificativi Member e Membership
  distinti; nessun abbinamento automatico fra anagrafiche. Backend e migrazione
  vengono pubblicati insieme alla nuova interfaccia, dopo la verifica finale.
- [x] Gestire errori e caricamenti senza presentarli come elenchi vuoti.
- [x] Correggere ricerca delle pagine consentite, titoli modali e manuale.
- [x] Applicare nome e logo nelle aree di lavoro e nelle informazioni.
- [x] Verificare salvataggi, tastiera, finestre di dialogo e schermi piccoli.
- [x] Pubblicare solo dopo i controlli e verificare la versione servita.

Gestionale è pubblicato nel deployment `dpl_GdK8pbU1diyRTWUaC6Btb1XF5KVS`,
insieme alla revisione server `adf-gestionale-collaudo-as-ui-20261030-c`.
Applicata una sola nuova migrazione PostgreSQL (V3); confermate in sola lettura
la cronologia e le colonne previste. Test server: 270 passati, nessuno saltato;
frontend: 18 test, lint, build web/desktop, controlli mobile e axe superati.
Pagina, JavaScript e logo sul dominio pubblico coincidono con gli artefatti
verificati. Sorgente: commit `00a6c6e516cbe54be8239d10f6386abc02b80b9c`.

## LibrePM: difetti individuati e interventi

- [x] Audit dei moduli, navigazione e dati di riepilogo.
- [x] Correggere conteggi che leggevano `status` invece di `statusName`.
- [x] Distinguere statistiche non disponibili da valori positivi o zero.
- [x] Aggiungere errori e nuovi tentativi agli elenchi dei progetti.
- [x] Proteggere creazioni ripetute e rendere i moduli accessibili.
- [x] Conservare il progetto attivo quando si apre una scheda.
- [x] Raggruppare la navigazione mantenendo tutte le funzioni raggiungibili.
- [x] Applicare marchio e nomi corretti, anche nella presentazione iniziale.
- [x] Verificare lista, creazione, selezione e menu su desktop e telefono.
- [x] Pubblicare senza modificare il bootstrap di accesso appena corretto.

Progetti è pubblicato nel deployment `dpl_ADTbavfbV19DuAD6o5Aqah3KCP7S`.
Pagina, JavaScript e logo serviti dal dominio pubblico coincidono con gli
artefatti verificati. Il sorgente della stessa revisione è nel commit
`78615261b43474f494e69564f4fff63c5aac1827`, ramo `cloud/adf-bandi` di LibrePM.
Controllati lint, build web/desktop, regressioni dei conteggi, selezione del
progetto, protezione dai doppi clic e cinque larghezze da 320 a 1280px.

## Bandi: estensione completa

Inventario iniziale: 31 pagine, 15 voci di navigazione, 82 GET e 70 mutazioni
dichiarate, comprese le rotte di autenticazione, integrazione e portale.
La mappa dettagliata è in
[ONLINE-LOCALE-PIANO.md](../Bandi/integration/ONLINE-LOCALE-PIANO.md).

- [x] Inventario di schermate e confine fra applicazione locale e portale.
- [x] Conferma del proprietario: operazioni dal proprio account, altri limitati.
- [x] Definire il catalogo finito delle operazioni e la copertura per pagina.
- [x] Collegare le letture complete ai servizi del PC, con filtri e paginazione.
- [x] Collegare le modifiche con validazione, versioni, idempotenza e ricevute.
- [x] Rendere disponibili documenti e file selezionati su richiesta, con limiti
  complessivi, verifica dei digest e assenza di percorsi arbitrari.
- [x] Riutilizzare le schermate native, mantenendo l'operatività locale.
- [x] Conservare bozze online e permessi dei collaboratori.
- [x] Conservare progetto comune, UUID e collegamenti agli altri applicativi.
- [x] Gestire PC spento, richieste interrotte, errori e risultati transitori.
- [x] Verificare che il solo proprietario possa avviare le operazioni riservate.
- [x] Preparare gateway, frontend e aggiornamento supervisionato del nodo.
- [x] Pubblicare il sito originale e verificare l'accesso reale dell'utente.
- [x] Verificare il caricamento RUNTS con l'utente dopo le correzioni.
- [ ] Provare un file reale oltre4,5MiB; le fixture di trasferimento sono passate.

Il sito originale è pubblicato alla radice del dominio Bandi. Le prove coprono
232 test PostgreSQL/ASGI senza salti,663 test web, build e fixture browser su
desktop e telefono. Il database PC è stato aggiornato con la sola0033 dopo
un backup completo da5,19GB, verificando i conteggi delle75 tabelle originali.
Il primo collaudo reale mostra il menu completo ma segnala attese RUNTS e altre
pagine. Corrette le attese delle code vuote, la ricomposizione degli elenchi
grandi e le chiamate di rete senza scadenza. Frontend e nodo aggiornati;
l'utente ha confermato «Ora vedo l'elenco» sulla pagina RUNTS. La verifica del
file reale grande resta distinta dalle fixture e non viene dichiarata conclusa.

La nuova pagina [Fonti ufficiali](https://bandi.angelidelfuturoaps.com/fonti) legge
direttamente cinque cataloghi pubblici, con accesso Clerk e cache volatile.
È consultabile senza PC acceso; gli strumenti originali sono raggiungibili
dal proprietario tramite **Analisi e archivio**. Superati anche 36 casi browser
dedicati, con gli stessi hash runtime della suite finale. Nessun archivio PDF
è stato trasferito e nessuna generazione AI è stata avviata per il collaudo.
Copertura e limiti: [FONTI-ONLINE.md](../Bandi/integration/FONTI-ONLINE.md).

L'accesso alle pagine non viene ottenuto disabilitando i controlli del gateway.
Ogni operazione rimane autorizzata sul server; nessun URL o comando arbitrario
del browser viene eseguito sul PC. Le azioni distruttive conservano le conferme
esplicite e i controlli nativi. Le prove non usano generazioni o cancellazioni
sui dati reali dell'utente.

## Verifica e pubblicazione

Le modifiche restano separate dalle parti di autenticazione funzionanti, salvo
le sole correzioni di presentazione. Per ciascun applicativo: controlli del
codice, build, regressioni funzionali appropriate e prova visiva/interattiva.
Le fixture sono separate dai dati reali. Il collaudo verifica l'effetto finale
dei comandi, non soltanto che il pulsante abbia un gestore.

Per la pubblicazione si conservano hostname e accesso Clerk. Si controllano
anche i file JavaScript effettivamente serviti e il prefisso API dei gestionali.
Un HTML che risponde200 non dimostra che il caricamento dei dati funzioni.

Le operazioni Bandi lunghe seguono `Bandi/AGENTS.md`: avviso sulla durata,
supervisore indipendente dalla chat, PID e heartbeat verificabili, risultati
terminali e ricevute. Nessun writer esistente viene duplicato o ripreso senza
controllare i relativi blocchi e checkpoint.
