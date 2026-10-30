# Contratto e collaudo della suite progetti

Il contratto comune è [suite-v1.openapi.json](suite-v1.openapi.json), OpenAPI 3.1 con schemi JSON Schema 2020-12. [SUITE-V1-CONTRACT.md](SUITE-V1-CONTRACT.md) descrive i vincoli di dominio e le decisioni condivise. `x-suite-systems` indica quale applicazione implementa ogni operazione: i percorsi comuni non implicano che tutti i server espongano tutte le funzioni.

Il registro dei progetti vive in LibrePM; AssociaGo conserva contabilità e volontariato, Bandi conserva i fascicoli. Gli identificativi nativi rimangono distinti. Un collegamento collega record esistenti; un'importazione ripetuta non duplica il progetto e non sovrascrive il piano con la candidatura.

## Verifica senza avviare applicazioni

Da AssociaGo:

```powershell
python -m pip install -r integration/requirements.txt
python scripts/integration/check_contract.py
python scripts/integration/check_contract.py --check-snapshots
```

Il controllo non apre connessioni di rete. Verifica le 14 operazioni HTTP, i requisiti congiunti dei tre header peer, i payload di esempio condivisi e casi negativi su UUID, versioni, denaro, dati personali, campi inattesi e sicurezza degli URL. Le fixture sono dati sintetici; non contengono credenziali utilizzabili.

Le copie distribuite in `LibrePM/integration` e `Bandi/integration` sono snapshot della stessa release. Il controllo confronta i due file canonici byte per byte tramite SHA256 e manifest; con `--check-snapshots` richiede la presenza di entrambe le copie. Senza opzione verifica quelle presenti, per permettere di usare AssociaGo anche senza clonare le altre repository.

`fixtures/api-security-cases.json` è una matrice di aspettative da verificare sui provider. La sua presenza non prova da sola che gli scenari HTTP siano stati eseguiti: il collaudo nativo e i test delle singole applicazioni forniscono quella prova.

## Validare una risposta Java o Python

```powershell
python scripts/integration/check_contract.py --schema PeerSnapshot --payload risposta.json
python scripts/integration/check_contract.py --response-log risposte.json
```

Il secondo comando accetta un array JSON di risposte serializzate, per esempio:

```json
[
  {
    "system": "bandi",
    "method": "GET",
    "path": "/api/integrations/v1/peer/projects/{suiteProjectId}",
    "status": 503,
    "payload": {
      "code": "suite_disabled",
      "message": "Integrazione progetti non attiva su questo sito."
    }
  }
]
```

`path` usa il percorso del contratto con il parametro tra parentesi. Non registrare header, chiavi, cookie o token. Il validatore rifiuta questi campi nel file delle risposte. Le risposte GET peer di LibrePM sono contesti; le equivalenti risposte AssociaGo/Bandi sono snapshot.

## Collaudo dei tre backend reali insieme

Prima costruire i bootJar aggiornati delle due applicazioni Java. Bandi deve avere già il proprio ambiente `.venv` con le dipendenze dell'applicazione. Il comando seguente non installa pacchetti e non usa i dati degli ambienti normali:

```powershell
python scripts/integration/run_native_smoke.py `
  --librepm-jar ../LibrePM/build/libs/LibrePM-0.1.1.jar `
  --associago-jar build/libs/AssociaGo-0.1.0.jar `
  --bandi-repo ../Bandi
```

Il processo dura al massimo quattro minuti. Avvia due Spring Boot e un FastAPI reali, tutti su porte temporanee di `127.0.0.1`, con database SQLite e cartelle temporanee contenenti soltanto fixture. Le migrazioni desktop Java vengono applicate a database vuoti. Per Bandi lo schema SQLite è creato dai modelli SQLAlchemy attraverso la stessa sostituzione di dipendenza usata nei test del progetto. Il processo esclude variabili d'ambiente applicative ereditate e gira fuori dalle repository, per evitare la lettura di file `.env`.

Il collaudo esegue l'accesso locale Bandi, condivide un fascicolo nel registro LibrePM, ripete la richiesta per verificarne l'idempotenza, sincronizza il progetto verso AssociaGo e Bandi e rilegge i riepiloghi attraverso le API reali. Verifica che il preventivo non generi movimenti contabili. Controlla inoltre autenticazione peer, organizzazione, profilo Bandi, versioni precedenti o discordanti, valute/precisione non rappresentabili in contabilità e il comportamento degli altri servizi durante l'arresto di un peer. Ogni risposta è validata sullo stesso contratto.

Verifica anche la condivisione successiva di un mirror AssociaGo già esistente, il rifiuto di un ricollegamento senza modifiche remote, la conservazione del piano a una nuova revisione del seed e il rifiuto di importi non rappresentabili senza perdita nel preventivo operativo. I conteggi dei record vengono controllati sui database temporanei e sul catalogo remoto, per rilevare duplicati e progetti orfani.

Al termine, anche in caso di errore, tutti i processi figli vengono terminati e le cartelle temporanee eliminate. Le risposte sintetiche e prive di credenziali vengono salvate in `.artifacts/native-responses.json`, ignorato da Git. Il watchdog arresta i processi dopo 240 secondi; non riavvia servizi.

Il collaudo desktop non verifica le migrazioni MySQL/PostgreSQL, le credenziali Clerk reali, il database Aiven o il deploy Google. Questi controlli restano distinti e devono essere dichiarati separatamente nel resoconto finale.
