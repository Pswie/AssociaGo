# AssociaGo — Deploy in cloud (backend condiviso + più macchine)

Questa guida spiega come mettere AssociaGo su **più macchine con un database unico**,
gratis, tenendo le credenziali del database **sul server** e non dentro l'exe.

## Architettura

```
  App desktop (exe)          App desktop (exe)          App desktop (exe)
   [client leggero]           [client leggero]           [client leggero]
          \                          |                          /
           \________________ HTTPS (login) _________________ /
                                     |
                       Backend Spring Boot (cloud)
                        profilo "cloud" — 1 istanza
                                     |
                          MySQL gestito (cloud)
                        un solo database condiviso
```

- Le app desktop **non** avviano più un backend locale: parlano via HTTPS con un unico
  backend in cloud.
- Il backend è l'**unico** che conosce le credenziali del database.
- Il database MySQL è raggiungibile **solo dal backend**, non dalle app.

## Cosa è già pronto nel codice

- **Profilo Spring `cloud`** (`src/main/resources/application.yaml`): datasource MySQL da
  variabili d'ambiente, dialetto MySQL, Flyway, porta da `PORT`, cookie di sessione sicuri.
- **Schema MySQL** (`src/main/resources/db/migration_mysql/V1__baseline_schema.sql`):
  l'intero schema, generato dalle entity e verificato su MySQL 8.4.
- **Dockerfile** (radice del repo): costruisce ed esegue il backend.
- **Client leggero** (`desktop/src/main/index.js`): se è configurato un backend remoto,
  l'app non avvia Java in locale ma usa quell'URL.

---

## Passo 1 — Database MySQL gratuito

Serve un MySQL gestito, gratuito, raggiungibile da internet. Opzione consigliata: **Aiven**.

### Opzione A (consigliata): Aiven for MySQL — piano Free
1. Registrati su https://aiven.io (non serve carta di credito per il piano Free).
2. **Create service → MySQL → piano "Free"**. Scegli una region vicina (es. `google-europe-west1`).
3. Aspetta ~1–2 minuti che lo stato diventi *Running*.
4. Nella pagina **Connection information** annota:
   - **Host** (es. `mysql-xxxx.aivencloud.com`)
   - **Port** (Aiven usa una porta dedicata, **non** 3306, es. `12345`)
   - **User** (es. `avnadmin`)
   - **Password**
   - **Database name** (es. `defaultdb`)
   - SSL: **REQUIRED** (obbligatorio)

Da questi valori costruisci la **JDBC URL** che userai nel Passo 2:
```
jdbc:mysql://<HOST>:<PORT>/<DATABASE>?sslMode=REQUIRED
```
Esempio:
```
jdbc:mysql://mysql-xxxx.aivencloud.com:12345/defaultdb?sslMode=REQUIRED
```

> Le migrazioni Flyway creeranno automaticamente tutte le tabelle al primo avvio del backend.
> Non devi creare nulla a mano nel database.

### Alternative gratuite
- **TiDB Cloud Serverless** (https://tidbcloud.com) — compatibile MySQL, free ~5 GB, senza carta.
  URL simile, sempre con `sslMode=REQUIRED`.
- **Oracle Cloud Always Free** — la più robusta e "gratis per sempre", ma richiede account
  Oracle (con verifica carta) e più configurazione: crei una VM ARM sempre-attiva e ci fai
  girare **sia** MySQL **sia** il backend (database non esposto a internet). Vedi la sezione
  "Setup avanzato" in fondo.

> Verifica sempre i termini correnti del piano gratuito: cambiano nel tempo.

---

## Passo 2 — Deploy del backend

Ospitiamo **una** istanza del backend. Opzione consigliata per semplicità: **Render**.

### Opzione A (consigliata): Render — Web Service da Docker
1. Fai il push di questo repository su GitHub (vedi `git push`).
2. Registrati su https://render.com (piano Free, senza carta).
3. **New → Web Service → Build and deploy from a Git repository** → seleziona il repo AssociaGo.
4. Render rileva il `Dockerfile` nella radice. Imposta:
   - **Runtime**: Docker
   - **Instance type**: Free
5. Nella sezione **Environment** aggiungi le variabili (dal Passo 1):
   | Variabile | Valore |
   |---|---|
   | `SPRING_DATASOURCE_URL` | `jdbc:mysql://<HOST>:<PORT>/<DATABASE>?sslMode=REQUIRED` |
   | `SPRING_DATASOURCE_USERNAME` | l'utente del DB (es. `avnadmin`) |
   | `SPRING_DATASOURCE_PASSWORD` | la password del DB |
   | `AUTH_ENABLED` | `true`  *(vedi Passo 4)* |
   | `CORS_ALLOWED_ORIGINS` | `*` |
6. **Create Web Service**. Render costruisce l'immagine e avvia il backend.
   - `PORT` è iniettata automaticamente da Render — il backend la legge già.
7. Quando è *Live*, ottieni un URL pubblico HTTPS, es.
   `https://associago-backend.onrender.com`. Verifica la salute aprendo:
   ```
   https://associago-backend.onrender.com/actuator/health   →  {"status":"UP"}
   ```

> **Nota "sleep"**: sul piano Free di Render il servizio va in pausa dopo ~15 minuti di
> inattività; la prima richiesta dopo la pausa impiega ~30–60 s a "svegliarlo" (il client
> desktop aspetta fino a 2 minuti, quindi funziona lo stesso, solo più lento al primo avvio).

### Alternative gratuite
- **Koyeb** (https://koyeb.com) — free, senza carta, deploy da Dockerfile, pausa meno aggressiva.
- **Railway / Fly.io** — ottimi ma richiedono carta / crediti a consumo.

---

## Passo 3 — Collegare l'app desktop al backend

Il client legge l'URL del backend, in ordine di priorità, da:
1. variabile d'ambiente `ASSOCIAGO_BACKEND_URL` (comoda per provare);
2. file `backend.json` nella cartella dati utente (`%APPDATA%\associago-desktop\backend.json` su Windows);
3. file `backend.json` incluso nel pacchetto (valore "di fabbrica").

### Per distribuire un exe già collegato al tuo server
Crea `desktop/resources/backend.json` con l'URL del backend:
```json
{ "baseUrl": "https://associago-backend.onrender.com" }
```
Poi aggiungi questo file agli `extraResources` in `desktop/package.json` (così finisce nel
pacchetto), oppure — più semplice — di' agli utenti di creare
`%APPDATA%\associago-desktop\backend.json` con lo stesso contenuto.

Infine ricostruisci l'installer:
```powershell
cd desktop
npm run build:win
```
L'exe risultante non avvia alcun backend locale: si collega direttamente al server.

### Per provarlo subito, senza ricompilare
```powershell
cd desktop
$env:ASSOCIAGO_BACKEND_URL = "https://associago-backend.onrender.com"
npm run dev
```

---

## Passo 4 — Sicurezza (IMPORTANTE)

Con `AUTH_ENABLED=false` il backend è **aperto**: chiunque conosca l'URL può leggere e
scrivere tutti i dati. **Prima di esporlo pubblicamente imposta `AUTH_ENABLED=true`** (Passo 2).

Con l'autenticazione attiva:
- il login avviene con **password master per-associazione** (`POST /api/auth/login` con
  `associationId` + `password`), già presente nell'app (`LoginForm`);
- tutte le `/api/**` richiedono una sessione autenticata;
- i cookie di sessione sono `Secure` + `SameSite=None` (richiedono HTTPS — Render/Aiven lo sono).

> Il flusso di login cross-site (client desktop → backend HTTPS remoto) va **collaudato
> contro il backend reale** dopo il deploy: i cookie `SameSite=None; Secure` si comportano
> correttamente solo su HTTPS, non su `http://localhost`. Vedi note più sotto.

---

## Riepilogo variabili d'ambiente del backend

| Variabile | Obbligatoria | Esempio |
|---|---|---|
| `SPRING_DATASOURCE_URL` | sì | `jdbc:mysql://host:12345/defaultdb?sslMode=REQUIRED` |
| `SPRING_DATASOURCE_USERNAME` | sì | `avnadmin` |
| `SPRING_DATASOURCE_PASSWORD` | sì | `••••••` |
| `PORT` | no (iniettata dall'hosting) | `8080` |
| `AUTH_ENABLED` | consigliata | `true` |
| `CORS_ALLOWED_ORIGINS` | no | `*` |
| `SPRING_PROFILES_ACTIVE` | no (già `cloud` nel Docker) | `cloud` |

---

## Setup avanzato — tutto su una VM Oracle Always Free (gratis per sempre)

Per la soluzione più robusta e senza limiti di "sleep":
1. Crea un account Oracle Cloud (Always Free) e una **VM ARM Ampere** (sempre attiva).
2. Installa Docker sulla VM.
3. Esegui MySQL e il backend con `docker compose`, sulla stessa rete privata: il database
   **non viene esposto** a internet, solo il backend pubblica la porta HTTPS.
4. Metti un reverse proxy (Caddy/Traefik) per il certificato HTTPS automatico.

Questa opzione elimina la pausa dei piani free e tiene il database completamente isolato,
ma richiede più configurazione manuale.
