> Aggiornamento successivo del 1 novembre 2026: [nuovo resoconto Bandi](BANDI-INTERVENTI-20261101.md). Le misure e i PID nel documento seguente appartengono al collaudo precedente; il registro Google ora misura circa 402,3 MB e i livelli da 52 MB sono stati recuperati.

# Verifica della suite Angeli del Futuro

Controllo del 1 novembre 2026. Questo documento distingue le prove sui servizi
reali dai test con dati sintetici.

## Che cosa significano i 52 MB su Google

Le sei vecchie build sono state eliminate dal registro. Rimangono
**52.196.016 byte di livelli non più utilizzati**, in attesa della pulizia
automatica interna di Google. Non sono dati dell'associazione o file mancanti.
Google documenta che i livelli non più referenziati vengono eliminati
giornalmente: [gestione delle immagini](https://docs.cloud.google.com/artifact-registry/docs/docker/manage-images#deleting_images).

Il nuovo aggiornamento correttivo ha aggiunto circa 187 KB al registro.
La misura effettiva dopo questo aggiornamento è **454.063.160 byte**; non
sottraggo anticipatamente i 52 MB ancora presenti. La versione precedente
del gateway Bandi è ora conservata come ritorno alla versione precedente.
Nessun file locale, originale, backup o dato Supabase è stato eliminato.

## Problemi trovati e interventi

1. **Nodo Bandi sul PC fermo.** Il task esisteva, ma l'ultimo avvio era
   fallito e il server non riceveva più la presenza del PC da circa 22 ore.
   Prima del ripristino ho controllato processi, lock, code, ricevute e
   lavorazioni locali: nessun lavoro pendente o generazione in corso.
   Ho conservato stati e log precedenti e ripristinato il nodo supervisionato.
   La causa storica dell'avvio fallito non è stata riprodotta; non la attribuisco
   a un problema diverso senza una prova.
2. **Errore 500 su alcune schede progetto importate.** I riferimenti alle
   fonti contengono anche elenchi e oggetti JSON; la risposta API accettava
   solo valori semplici. Ho corretto la risposta senza cambiare i dati o
   allargare i permessi. Gli inserimenti manuali mantengono i controlli precedenti.
3. **Frontend incompatibile con gli stessi riferimenti.** Corretto il lettore
   dei dati e la visualizzazione di elenchi e oggetti, con limiti di dimensione
   e profondità e rappresentazione come testo. Aggiunte regressioni dedicate.
4. **Test della pagina Novità non aggiornato.** La pagina era già correttamente
   disponibile online; aggiornato il controllo che la classificava erroneamente
   come pagina esclusivamente locale.
5. **Riferimento immagine non aggiornato nel registro di pubblicazione.**
   Il servizio usava l'immagine corretta; riallineati i metadati alla versione
   effettivamente pubblicata.
6. **Icona del sito Bandi mancante sul percorso standard.** Il sito usa il logo
   ufficiale già disponibile; aggiunto il collegamento corretto e il reindirizzamento
   del percorso dell'icona, senza creare altre immagini.

Le 26 preparazioni locali già fallite sono rimaste intatte: non sono state
riavviate per far passare un test.

## Prove completate

| Area | Prova | Esito |
| --- | --- | --- |
| Database e sincronizzazione | 210 test: Bandi 102, Gestionale 65, Progetti 20, nodo locale 23 | Superati, nessuno saltato |
| Nuova correzione API | 17 test del dominio, inclusi dettaglio/elenco con riferimenti strutturati e rifiuto negli inserimenti manuali | Superati |
| Frontend Bandi | Suite completa: 820 test in 94 file; TypeScript ed ESLint | Superati |
| Accessi dei due frontend | Gestionale 27 e Progetti 31 test di autenticazione, callback e permessi API | Superati |
| Collegamenti reali tra le tre API | 18 richieste GET: salute, accessi anonimi, chiavi peer, organizzazione errata e accesso al database | Superati |
| Dati reali sul PC | Tutte le 28 consultazioni del catalogo, in transazioni di sola lettura | Superate dopo la correzione |
| Nuova immagine Bandi su Google | 35 controlli reali: manifest, cataloghi, permessi, 28 consultazioni e ricerca quotidiana | Superati |
| Gateway Bandi pubblicato | Salute 200; API riservate 401 senza login; documentazione interna non esposta | Superati |
| Archivio Cloudflare | Ricostruzione completa e hash verificati dal nuovo job Google; ulteriori controlli su manifest, blocco finale, autenticazione e percorsi protetti | Superati |
| Ricerca quotidiana | Quattro fonti con ultimo controllo riuscito, 87 riferimenti conservati | Verificata online |
| Manutenzione cron-job.org | Sei esecuzioni pianificate recenti, ogni 30 minuti, fino alle 04:00 UTC: tutte HTTP 200 nei log Google | Verificata senza chiamare manualmente il cron |
| Backup notturno | Archivio dei tre schemi del 1 novembre: 492.454 byte, hash archivio e indice | Corretti |
| API Docker del PC | Nuova immagine, scheda reale con riferimenti strutturati 200, salute 200 e richieste anonime 401 | Superati; database e impostazioni invariati |
| AI locale | Ollama raggiungibile dall'API Docker; modelli configurati per generazione ed embedding già installati | Verificato senza generare testi o scaricare modelli |

I test della sincronizzazione coprono identità comune del progetto, versioni,
idempotenza, recupero degli errori, claim concorrenti, bozze private,
revisioni immutabili e separazione delle organizzazioni. Le scritture di prova
sono state eseguite in database locali separati, non nei siti pubblici.

Il test peer reale usa una chiave server valida e un UUID inesistente: la
risposta 404 prevista prova il raggiungimento del servizio e del relativo
datastore. Non equivale a creare e modificare un progetto nella sessione
Clerk dell'utente.

La vecchia immagine dell'API locale e i suoi log sono conservati. La nuova
immagine aggiunge alla base esatta un solo file corretto; verificati contenuto,
livelli Docker e risposta nella vera immagine in esecuzione. Anche il tag
usato dal normale avvio Docker è stato aggiornato, così un riavvio ordinario
non ripristina la versione con l'errore. Sono rimasti invariati container
PostgreSQL, migrazione `0033_suite_projects`, collegamento a `G:/Terzi settore`,
porte, ambiente, comando e DNS. Nessun worker è stato ricreato.

## Come sono collegati i siti

- [Area riservata principale](https://www.angelidelfuturoaps.com/admin): ingresso
  centrale con Clerk e collegamenti ai servizi.
- [Gestionale](https://gestionale.angelidelfuturoaps.com): dati associativi e
  amministrativi collegati al progetto comune.
- [Progetti](https://progetti.angelidelfuturoaps.com): identità e selezione del
  progetto condiviso, con UUID comune e collegamenti alle altre applicazioni.
- [Bandi](https://bandi.angelidelfuturoaps.com): cataloghi e bozze online;
  operazioni locali e AI tramite il nodo sul PC, riservate al titolare.

Il catalogo generale su Cloudflare si consulta anche a PC spento. Le funzioni
che lavorano su originali e AI richiedono PC, Docker e nodo attivi. I nuovi
riferimenti quotidiani online provengono da quattro indici ufficiali;
non dichiaro una copertura automatica di tutti i siti esistenti. Il percorso
locale più ampio controlla 37 risorse e resta distinto.

La sincronizzazione usa le azioni esplicite di condivisione e aggiornamento,
versioni e tentativi idempotenti. Non prometto una sincronizzazione automatica
ogni pochi minuti a servizi Google spenti: il pianificatore Java da solo non
garantisce esecuzione quando Cloud Run scala a zero.

## Limiti delle verifiche

- Opera continua a rispondere «richiede una nuova autenticazione»: il passaggio
  tra tutte le applicazioni nella sessione Clerk reale non è stato nuovamente
  collaudato. Il browser anonimo e i test automatici di callback sono verifiche
  separate; non ho creato token per impersonare l'utente.
  L'istanza Clerk è ancora quella di sviluppo già usata dalla suite; non ho
  creato un'altra istanza o spostato gli utenti durante questi controlli.
- Non ho inviato candidature, email o lanciato generazioni AI per collaudare i
  pulsanti. Queste operazioni sono coperte da prove isolate, non da una nuova
  esecuzione reale con effetti esterni.
- Un file reale superiore a 4,5 MiB attraverso Vercel e Clerk rimane da provare
  nella sessione autenticata; i trasferimenti a blocchi hanno test dedicati.
- La pipeline quotidiana locale completa non è stata rilanciata: la correzione
  del log vuoto è verificata automaticamente; il controllo leggero reale delle
  37 risorse e la ricerca cloud sono già avvenuti. Non riavvio writer storici.
- L'avvio a freddo del Gestionale ha richiesto circa 48 secondi, poi HTTP 200.
  Le richieste successive sono più rapide: non confondo l'attesa iniziale con
  un controllo di login completo.
- Il frontend ha controlli TypeScript ed ESLint completi riusciti. Per il
  backend ho eseguito le suite pertinenti e i controlli statici dei file
  modificati: non dichiaro risolto il debito statico generale già documentato
  in `Bandi/integration/CI-DEBITO-TECNICO.md`.
- Il browser anonimo ha coperto dieci casi: sito principale, Gestionale e
  Progetti a 375/768/1440 pixel, Bandi a 1440 pixel. Nessun overflow nei casi
  provati; il controllo mobile di Bandi nella sessione reale rimane distinto.

Un primo test sintetico del nodo aveva un percorso temporaneo di 261
caratteri, oltre il limite Windows: conservata la prova, gli stessi 23 test
sono riusciti in una nuova cartella corta, senza modificare il prodotto.

## Pubblicazione finale

- Backend Google Bandi: `adf-bandi-portale-api-cf-20261101-c`, traffico 100%,
  immagine `sha256:455e63ece0f6080452a90d866f5e53425d661c4dd793739398a36a6b69f4176c`.
- Nodo PC aggiornato e attivo: processo `60204`, supervisore `59052`; stato e
  avanzamento verificati anche da un controllo indipendente.
- API Docker locale: immagine
  `sha256:6701d9f3ee864281c5f699f1b78a4695a45b34f56626a2fe5ac449949f3cade2`,
  sana, con la scheda reale corretta verificata.
- Codice Bandi pubblicato: `f4df5c945a7fd7b905077a2c600175d3c806a983`, nove file
  revisionati, seguito da `ead581a5ea68c1b3b6eb263e36a4551938b6caa7` per due
  file dell'icona; entrambi i riferimenti remoti sono verificati. Nessun nuovo
  run GitHub Actions risulta avviato al controllo del primo commit; i risultati
  sopra sono le prove eseguite, non un esito CI remoto.
- Frontend Bandi pubblicato:
  `dpl_H6qXRi1SCE4Hw6a62CYC7BALmiaj`, Node 22, su
  [bandi.angelidelfuturoaps.com](https://bandi.angelidelfuturoaps.com).
  Quindici controlli pubblici passati con corpi e hash identici alla candidata;
  reindirizzamento dell'icona 307, PNG ufficiale 200 e collegamenti HTML corretti.
  L'alias è stato riletto anche dal connettore Vercel. La precedente versione
  `dpl_A7fNLX4ygPifSKS4izrbYFryeQZR` è conservata.

La prima candidata dell'icona si era fermata al controllo di un corpo vuoto:
non è stata promossa. La correzione finale intercetta il percorso prima della
gestione speciale delle icone di Next.js. Sono conservate anche le prove
dei tentativi precedenti.

### Nodo lasciato in esecuzione

Il task `Bandi Portal Local Node` resta intenzionalmente attivo. Al controllo
finale è nello stadio `polling`, con processo `60204` e supervisore `59052`.
Il suo funzionamento non dipende dalla chat.

La cartella di stato è
`C:\Users\lange\AppData\Local\BandiSuite\private\release-tfeplskriazdybiampgg\node-state`:
`state.json` registra il lavoro, `supervisor.json` la supervisione,
`process.log` e `worker.log` i log. Il criterio di funzionamento è avanzamento
dei contatori di polling, presenza recente sul server e assenza di errori.
Uno stato terminale `failed`, una presenza scaduta o oltre 90 secondi senza
avanzamento richiedono verifica; il supervisore non riavvia automaticamente
writer incerti. I processi temporanei del collaudo sono terminati.

Le prove tecniche sono conservate nelle cartelle ignorate da Git sotto
`AssociaGo/deployment/.artifacts`: `suite-qa-20261101-ae62e3ca186e4dbbadf42ea386ce4930`,
`fresh-suite-audit-20261101-0759f723b6ec4a17b4988bd5e020605e`,
`fresh-suite-cloud-readonly-20261101-a1eeeb98bc5e4b129aa8f19af14d0bad`,
`domain-api-candidate-20261101-0fc568754cde41bdb69a928724364cc2` e
`domain-api-promotion-20261101-5b06145c030c4a8e9113227151d489d4`.
La pubblicazione frontend e il confronto pubblico indipendente sono in
`provenance-ui-production-20261101-3413c210d2904d7a8b7bad2e8d2e8bf8`
e `fresh-suite-cloud-readonly-20261101-a1eeeb98bc5e4b129aa8f19af14d0bad`.
Il ripristino locale e i controlli successivi sono in
`local-api-refresh-20261101-3d961389e9bd44bf9f000cbace10a247`.
