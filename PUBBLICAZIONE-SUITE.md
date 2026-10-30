# Lavori sulla suite Angeli del Futuro

Aggiornato il 30 ottobre 2026. Questo è il resoconto operativo corrente.
[LAVORI-INTEGRAZIONE.md](LAVORI-INTEGRAZIONE.md) descrive la prima integrazione;
[MIGRAZIONE-SUPABASE.md](MIGRAZIONE-SUPABASE.md) la conversione dei database.

## Stato effettivo

Il nuovo account Supabase è stato collegato tramite OAuth ufficiale. Il progetto
`tfeplskriazdybiampgg`, nell'organizzazione «Angeli del futuro aps»
(`zdhnjznqpzdwuatqobmo`), è attivo sul piano **Free**, regione `eu-west-1`,
PostgreSQL17.11. È stato verificato vuoto prima degli interventi. Sono stati
creati i tre schemi privati e sei ruoli separati, verificando privilegi minimi
ed esclusione dalla Data API. Il precedente progetto Tabaccheria non è stato
modificato. Aiven era vuoto e non serve recuperarlo.

Le migrazioni applicative sono state applicate: due per AssociaGo, quattro per
LibrePM e due per il portale Bandi. Il tuo utente Clerk verificato è collegato
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
correzione è in pubblicazione insieme alla gestione delle attese del profilo;
il SSO completo e il caricamento dei dati non sono ancora considerati verificati.
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
| `bandi_portal` | Bozze, revisioni, coda e progetti comuni | `bandi_portal_app` |

Le migrazioni usano account proprietari distinti. Gli account applicativi non
possono creare tabelle, leggere gli altri schemi o modificare la storia delle
migrazioni. Le revisioni delle bozze sono append-only anche nei permessi SQL.
Gli schemi devono rimanere fuori dalla Data API Supabase.

Il browser usa Clerk e le API delle applicazioni. La connessione PostgreSQL
rimane sui server: il modello Next.js con Supabase Auth e un secondo middleware
di sessione non serve per questa architettura.

L'archivio completo Bandi, PDF, indici vettoriali, Ollama e Agno rimangono sul PC.
La variante opzionale con tutto Bandi su PostgreSQL è collaudata, ma non è il
modello scelto per questa pubblicazione. Il collegamento fra app è versionato:
segnala indisponibilità e conflitti, senza promettere transazioni distribuite
istantanee o sovrascrivere silenziosamente il lavoro.

## Funzioni sviluppate e problemi corretti

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
- Creato il gateway Bandi separato dall'archivio: nessuna API locale di
  ingestione, comando arbitrario o agente AI esposto online.
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
| Proprietario | Tutte le bozze, scelta della bozza principale, autorizzazione delle generazioni |
| PC spento | Bozze e progetti comuni online; catalogo locale indisponibile; generazioni autorizzate in attesa |
| PC acceso con nodo collegato | Consultazione del catalogo e lavorazione delle richieste consentite |

Il PC usa HTTPS in uscita: nessuna porta del router o database locale da
pubblicare. Il nodo è stato prima verificato in sola lettura. Dopo i controlli
su code vuote, writer assenti, modelli e tokenizer con digest fissati, è stata
abilitata anche la capability di generazione: il gateway la riserva al solo
account proprietario verificato. **Nessun nuovo lavoro AI, ingestione o
generazione è stato accodato o eseguito durante la pubblicazione.** Il nodo
continua a controllare la coda mentre il PC è acceso e si avvia all'accesso a
Windows; non deve essere lasciato un terminale aperto. L'arresto/riavvio breve
per verificare il nome del progetto non ha eseguito lavori.

Ultimo controllo operativo: 30 ottobre, 14:39 UTC. Worker PID 59816 e
supervisore PID 42824 attivi; worker in stato `running/polling`, heartbeat
del gateway aggiornato, 681 consultazioni della
coda, zero lavori attivi o completati. Il supervisore e l'attività Windows
continuano indipendentemente da questa chat. I PID cambiano ai riavvii; fanno
fede i file di stato e i log indicati sotto. Il risultato atteso è servire le
consultazioni e lavorare soltanto le generazioni autorizzate dal proprietario.

Guide: [contratto](../Bandi/integration/COLLABORAZIONE-CONTRATTO.md),
[gateway](../Bandi/docs/COLLABORAZIONE-GATEWAY.md),
[nodo PC](../Bandi/integration/NODO-PC.md),
[interfaccia](../Bandi/apps/web/COLLABORAZIONE.md).

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
**Il nuovo collaudo reale dell'utente deve ancora confermare il login comune.**
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
| Bandi | [bandi.angelidelfuturoaps.com](https://bandi.angelidelfuturoaps.com/collabora) | Nuovo portale Vercel + gateway Cloud Run `adf-bandi-portale-api` |

Il servizio Google chiamato `adf-bandi-collaudo` ospita LibrePM, non Bandi Python/Next.js.
Il dominio principale usa già DNS Vercel. Le immagini aggiornate sono nel
registro `europe-west8-docker.pkg.dev/flawless-star-503422-q3/cloud-run-source-deploy`.
I contesti Docker contengono soltanto JAR verificate o moduli gateway ammessi,
senza dati, configurazioni private o modelli del PC.
[build_runtime.py](deployment/build_runtime.py) registra heartbeat, PID,
digest e risultato terminale delle costruzioni.

Ho rimosso **18 revisioni Google obsolete** senza traffico o tag. Restano
soltanto le tre revisioni nuove, una per backend, tutte verificate nuovamente
con HTTP200 dopo la pulizia. Nei tre pacchetti Artifact Registry della suite
sono stati rimossi o verificati assenti13 digest obsoleti; restano9 digest
necessari alle tre immagini correnti, inclusi manifest e attestazioni.

La pulizia ha ricontrollato servizi e job in tutte le regioni del progetto,
riferimenti, tag e immagini prima delle cancellazioni puntuali. Due manifest
approvati sono risultati già assenti durante la procedura: il controllo si è
fermato, è stato riconciliato l'inventario in sola lettura e verificato il set
finale esatto. Le ricevute intermedie sono conservate insieme a
`deployment/.artifacts/cleanup-final-verified-20261030.json`. Non è stato
eseguito un prune generale del registro. I container locali e i volumi con i
dati Bandi sono conservati.

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
archiviata. Nessun account cron-job.org è ancora collegato e nessun job remoto
è stato creato. Guida: [CRON-PORTALE.md](deployment/CRON-PORTALE.md).

Il cron non è un backup e non garantisce l'assenza di pause del piano Free.
È operativo [backup_cloud_schemas.py](deployment/backup_cloud_schemas.py), con
`pg_dump` PostgreSQL17, archivio custom dei tre schemi, manifest SHA256 e
verifica mediante `pg_restore --list`. Scrive fuori dalle repository, in
directory protette per l'utente, senza cancellare vecchie copie o esporre
credenziali nei comandi. La prova di ripristino reale ha usato una bozza e due
revisioni sintetiche: conteggi e hash coincidono dopo il ripristino. I due
database temporanei sono stati rimossi e la sorgente è rimasta intatta.
Il primo backup reale dei tre schemi è riuscito: archivio custom di 481.510
byte, SHA256 e indice pg_restore verificati. Usa il ruolo separato
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

- AssociaGo: 232/232 test, PostgreSQL 17/MySQL 8.4/SQLite reali.
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
- Bandi web: 394/394 test, lint, typecheck e build passati; flussi browser con
  dati sintetici verificati. Build web AssociaGo/LibrePM e check/build Astro passati.
- Backend pubblicati: 27 verifiche di configurazione e18 richieste HTTP
  passate sui tre indirizzi stabili;16 controlli offline dei preparatori e
  dell'audit di rilascio passati.
- Cron: sette test offline passati, senza chiamate ad account reali.
- Backup: cinque test offline e prova di ripristino PostgreSQL17 reale passati;
  ulteriori sette test sul recupero delle esecuzioni Windows passati.
- Immagine gateway: salute200, anonimo401, API locali/documentazione404,
  manutenzione autenticata con query PostgreSQL riuscita; PUT/GET peer reali
  riusciti, altra organizzazione403 e progetti anonimi401.

Il vecchio controllo delle migrazioni complete su SQLite è stato sostituito
dalla verifica già disponibile su PostgreSQL reale, senza modificare migrazioni
storiche. Il nuovo job CI esegue esplicitamente upgrade fino a head, confronto
dei modelli, pgvector e controlli del portale con ruoli distinti: riprodotto in
locale con **112 test passati e zero saltati**. Black, Ruff e mypy sono passati.
La verifica locale non equivale a un'esecuzione remota GitHub Actions.

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
- [ ] Completare la verifica del login reale con la sessione dell'utente.
- [x] Attivare nodo PC e backup contro le destinazioni verificate.
- [ ] Collegare cron-job.org con l'accesso API dell'utente e installare il job pronto.
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

I sorgenti aggiornati sono nelle copie locali delle repository. Questa fase
pubblica immagini e snapshot verificati; non ha eseguito push GitHub. Per i
rilasci successivi seguire [FRONTEND-VERCEL.md](deployment/FRONTEND-VERCEL.md)
e [DEPLOY-CANDIDATES.md](deployment/DEPLOY-CANDIDATES.md).

## Residui temporanei

La pulizia di tre directory `node_modules` usate esclusivamente nelle prove
isolate è stata rifiutata dal controllo automatico dell'ambiente con
`blocked by policy`, senza ulteriori motivazioni. Non sono stati tentati
aggiramenti. Lo staging è conservato in
`%TEMP%\bandi-npm-hardening-439sit1q`; le dipendenze delle app reali sono integre.
