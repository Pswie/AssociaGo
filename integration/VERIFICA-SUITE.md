# Verifica indipendente della suite

Il 29 ottobre 2026 sono state eseguite 46 verifiche HTTP con i tre backend reali avviati insieme su loopback: LibrePM Spring Boot, AssociaGo Spring Boot e Bandi FastAPI. Ogni risposta è stata confrontata con il medesimo contratto OpenAPI. Sono stati usati esclusivamente database e archivi temporanei con dati sintetici; nessun accesso a dati utente o servizi cloud.

Esito finale: **46 verifiche native passate**, **57 fixture di contratto/sicurezza passate**, **13 risposte serializzate dei test LibrePM conformi** e **2 snapshot distribuiti identici** alla fonte AssociaGo, verificati tramite SHA256.

Il percorso verificato comprende accesso locale Bandi, condivisione di un fascicolo nel registro LibrePM, ripetizione idempotente della condivisione, sincronizzazione verso entrambi i peer e rilettura dei consuntivi e del fascicolo. Il preventivo non ha generato movimenti contabili. Sono stati rifiutati credenziali peer errate, organizzazioni e profili estranei, UUID non validi, versioni precedenti, contenuti discordanti alla stessa versione, overflow delle versioni, valute non supportate e frazioni contabili inferiori al centesimo. Durante l'arresto di Bandi, AssociaGo è rimasto sincronizzato e il registro ha conservato il relativo riepilogo.

I controlli di recupero verificano che condividere nuovamente da AssociaGo il progetto già ricevuto dal registro restituisca lo stesso identificativo e lasci un solo progetto nel catalogo. Un tentativo di collegare quel record locale a un secondo progetto comune riceve 409 senza creare un'istanza remota. Una nuova revisione del seed conserva il piano operativo esistente; un preventivo non rappresentabile esattamente nel budget nativo riceve 409 prima di creare il progetto. Il conteggio finale del registro contiene esattamente i due progetti creati intenzionalmente.

Il collaudo ha individuato e portato alla correzione di due difetti che i controlli separati non mostravano:

- Le migrazioni SQLite di AssociaGo non includevano quattro colonne dei turni già usate dal modello Java. La nuova migrazione V44 le aggiunge senza ricreare i dati.
- Il client Java tentava l'upgrade HTTP/2 h2c verso Uvicorn su loopback, causando HTTP 400 prima della validazione del payload. I client peer Java ora usano HTTP/1.1 esplicito.

La revisione del confine tra servizi ha inoltre segnalato importi non rappresentabili senza perdita nel budget nativo Double di LibrePM, titoli di fascicoli nullable, limiti numerici/di testo diversi, forma degli errori di autenticazione e timeout Java limitati agli header. Le implementazioni sono state corrette; un test HTTP dedicato verifica anche l'interruzione di una risposta con corpo bloccato.

Il collaudo desktop non verifica migrazioni MySQL/PostgreSQL, accessi Clerk reali, database Aiven o deploy Google. La matrice `fixtures/api-security-cases.json` contiene aspettative aggiuntive: alcune sono coperte dai test dei singoli provider e non vanno confuse con il numero di chiamate del collaudo nativo.

Per ripetere la verifica usare i comandi di [README.md](README.md). Le risposte sintetiche dell'ultimo giro sono salvate localmente in `.artifacts/native-responses.json`, ignorato da Git; non includono header, chiavi o cookie. Tutti i processi dell'ultimo giro sono stati terminati e i relativi file temporanei rimossi.

Un residuo di un tentativo iniziale rimane in `C:\Users\lange\AppData\Local\Temp\suite-native-smoke-u52mejaj`: nessun processo attivo, soltanto 11 file sintetici noti (due copie dei jar, log/config e due database di prova), circa 212 MB. La policy automatica dell'ambiente ha rifiutato sia la pulizia ricorsiva sia una successiva rimozione esplicita dei soli file noti e delle directory vuote, dopo la verifica del percorso e dei processi. Il messaggio disponibile è `blocked by policy`, senza ulteriori motivazioni. Non sono stati fatti altri tentativi e il residuo non influisce sulle applicazioni o sui dati utente.
