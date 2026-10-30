# Collaudo LibrePM e gateway Bandi

`scripts/supabase/run_gateway_smoke.py` avvia il JAR reale di LibrePM e il
gateway FastAPI/Uvicorn reale su loopback, con PostgreSQL17 e ruoli separati.
Richiede Python nell'ambiente Bandi, Java21, Docker già attivo e un sandbox
locale già supervisionato da `scripts/supabase/local_database.py`.

Il comando verifica l'identità del container tramite ID e label e rifiuta
connessioni che non indicano `127.0.0.1`. Crea un solo database dal nome UUID
`codex_suite_smoke_*`, con i soli schemi `librepm` e `bandi_portal`.
Non modifica gli schemi del database originale, non installa pgvector e non
avvia container, acquisizioni, worker PC o modelli AI.

Da questa repository, indicando file privati con credenziali sintetiche:

```powershell
& ../Bandi/.venv/Scripts/python.exe scripts/supabase/run_gateway_smoke.py `
  --pg-state <sandbox>/state.json `
  --pg-connection <sandbox>/connection.json `
  --pg-roles <sandbox>/roles.json `
  --librepm-jar <jar-collaudato>/LibrePM-final.jar `
  --bandi-repo ../Bandi `
  --output deployment/.artifacts/gateway-native-final.json
```

I valori delle credenziali non vengono passati negli argomenti o registrati
nell'evidenza. I processi ricevono configurazione sintetica e il JAR viene
copiato prima dell'avvio; il suo SHA256 è registrato nello stato.

Il limite è 240 secondi. Il watchdog arresta soltanto i processi avviati dal
collaudo; il blocco finale rimuove soltanto il database UUID appena creato,
senza fermare il container condiviso. Un esito completo richiede
`status=succeeded`, `stage=cleaned`, `database_removed=true` nel file
`gateway-native-final.state.json`. Le risposte canoniche sono nel file JSON
richiesto; le diagnostiche di un errore vengono redatte prima del salvataggio.

La prova comprende importazione idempotente, sincronizzazione HTTP reale,
persistenza dello snapshot in LibrePM, approvazione esplicita dei metadati,
stabilità del collegamento al fascicolo, edit privati non pubblicati, due
progetti con titolo uguale e UUID distinti e assenza di lavori PC.

La fedeltà dell'autenticazione è dichiarata: LibrePM verifica RS256 attraverso
un issuer OIDC/JWKS sintetico locale; il gateway verifica un JWT RS256 con
issuer HTTPS sintetico e chiave pubblica iniettata dal collaudo. Questo prova
il percorso backend e i controlli crittografici, senza provare una sessione
Clerk reale, il browser, Vercel, TLS Cloud Run o un database Supabase remoto.
