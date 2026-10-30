# Suite progetti v1 — contratto di implementazione

Tre siti separati. Registro autorevole nel backend LibrePM. Archivi operativi isolati: possono risiedere in database distinti oppure in tre schemi privati di uno stesso PostgreSQL/Supabase, con ruoli e migrazioni separati. Nessuna replica delle scritture contabili e nessuna scrittura diretta nelle tabelle delle altre applicazioni. Questo file coordina le implementazioni; `suite-v1.openapi.json` ne definisce i payload verificabili.

## Identificativi e autorizzazione

- `suiteProjectId`: UUID del progetto operativo LibrePM. Gli ID degli altri sistemi restano stringhe opache nelle corrispondenze.
- `organizationId`: UUID configurato uguale nelle tre app; distinto da associationId, profilo aps/cooperativa, soggetto Clerk.
- API utente: autenticazione già prevista da ciascuna app. LibrePM deve controllare il progetto dell'utente, non il solo ruolo. AssociaGo limita all'associazione configurata e ai ruoli di progetto; Bandi mantiene il proprio operatore autenticato.
- API peer sotto `/api/integrations/v1/peer/**`: segreti solo server, confronto costante, tre header `X-Suite-Peer`, `X-Suite-Key`, `X-Suite-Organization`. LibrePM accetta associago con `SUITE_ASSOCIAGO_KEY`, bandi con `SUITE_BANDI_KEY`; gli altri accettano solo librepm con la rispettiva chiave. Non riutilizzare sessioni o credenziali database.
- Nessun token negli URL. URL remoti solo da configurazione, HTTPS salvo loopback HTTP, niente credenziali/query/frammenti/redirect arbitrari, timeout finiti.
- `SUITE_ENABLED=false` per impostazione predefinita. Se attiva: organization UUID valido, chiavi almeno 32 caratteri, mapping locale esplicito. Configurazione incompleta restituisce 503; credenziali errate 401/403. Non generare segreti versionati.

## Configurazione comune

`SUITE_ENABLED`, `SUITE_ORGANIZATION_ID`, `SUITE_LIBREPM_API_URL` (origine backend senza /api), `SUITE_ASSOCIAGO_API_URL`, `SUITE_BANDI_API_URL`, `SUITE_LIBREPM_PUBLIC_URL`, `SUITE_ASSOCIAGO_PUBLIC_URL`, `SUITE_BANDI_PUBLIC_URL`, `SUITE_ASSOCIAGO_KEY`, `SUITE_BANDI_KEY`.

LibrePM: `SUITE_IMPORT_OWNER_ID` per import avviati dall'operatore locale Bandi/desktop senza un soggetto Clerk collegato; deve identificare un utente reale. Import con soggetto Clerk già verificato dall'app sorgente può risolvere un utente esistente; non prendere un owner ID dal browser.

AssociaGo: `SUITE_ASSOCIATION_ID` associazione locale a cui devono appartenere tutti i record integrati.

Bandi: `SUITE_BANDI_PROFILE=aps|cooperativa`, profilo ammesso nell'installazione collegata.

## API LibrePM

Utente:

- `GET /api/integrations/v1/projects`: contesti registrati visibili all'utente.
- `POST /api/integrations/v1/projects`: `{librepmProjectId}` registra un progetto posseduto/autorizzato, ripetibile.
- `GET /api/integrations/v1/projects/{suiteProjectId}`: contesto autorizzato.
- `POST /api/integrations/v1/projects/{suiteProjectId}/sync`: sincronizza proiezioni e legge riepiloghi; risultati per singolo peer, errori persistiti.
- `PUT /api/integrations/v1/selection`: `{suiteProjectId}` preferenza persistente utente/organizzazione.
- `GET /api/integrations/v1/selection`: `{suiteProjectId: UUID|null}`.

Peer:

- `GET /api/integrations/v1/peer/projects`: catalogo dell'organizzazione configurata.
- `GET /api/integrations/v1/peer/projects/{suiteProjectId}`: contesto.
- `POST /api/integrations/v1/peer/projects`: ProjectSeed, importa una volta per `(organizationId, source.system, source.entity, source.localId)` e restituisce il contesto. Il peer autenticato deve coincidere con source.system. Mai riabbinare per titolo.
- `POST /api/integrations/v1/peer/projects/{suiteProjectId}/sync`: retry protetto per progetti dell'organizzazione.
- `POST /api/integrations/v1/peer/projects/{suiteProjectId}/instances`: `{system,entity,localId,revision}` registra il collegamento del peer autenticato a un progetto esistente e restituisce ProjectContext. `system` deve corrispondere a `X-Suite-Peer`; non importa un nuovo progetto e non riassegna silenziosamente un collegamento esistente.

Il registro conserva corrispondenze, stato per peer, ultimo successo/errore e provenienza/versione degli import. I retry non duplicano il progetto; versioni precedenti non sovrascrivono revisioni più recenti. L'import iniziale non aggiorna automaticamente piano/candidatura ai successivi invii. Un job periodico configurabile può riallineare i soli progetti registrati e va protetto dalle esecuzioni sovrapposte. Nessun riuso del vecchio SyncService incompleto.

## API AssociaGo e Bandi

Utente (suffisso identico salvo source):

- `GET /api/integrations/v1/projects`: catalogo tramite LibrePM; preserva errori esterni e permessi locali.
- `GET /api/integrations/v1/projects/{suiteProjectId}`: risolve il progetto remoto e locale.
- `POST /api/integrations/v1/projects/share`: `{localId}`; legge il record locale autorizzato, costruisce ProjectSeed sul server, invia a LibrePM, persiste la corrispondenza. AssociaGo localId=Project.id; Bandi localId=ProjectPreparation.id. Nessun import di progetti pubblici come propri.
- `POST /api/integrations/v1/projects/{suiteProjectId}/sync`: retry tramite LibrePM.
- `POST /api/integrations/v1/projects/{suiteProjectId}/link`: `{localId}` collega un progetto/fascicolo locale esistente e autorizzato al progetto comune, registrando anche l'istanza in LibrePM. Non crea un nuovo progetto LibrePM e non riassegna collegamenti esistenti senza errore esplicito.

Peer:

- `PUT /api/integrations/v1/peer/projects/{suiteProjectId}`: ProjectProjection, upsert idempotente e persistente. Verifica organization, sourceVersion, UUID e localId eventualmente fornito; associa solo record appartenenti al contesto locale.
- `GET /api/integrations/v1/peer/projects/{suiteProjectId}`: PeerSnapshot.

AssociaGo crea un Project locale solo se non esiste la corrispondenza, oppure collega `targetLocalId` verificato; restituisce rendiconto reale dalla contabilità e aggregati di presenze. Il budget preventivo iniziale non genera movimenti. Non tocca lo stato operativo locale sulla base dello stato della candidatura. EUR esplicito: rifiutare altre valute, nessuna conversione silenziosa.

Bandi conserva un mirror/collegamento del progetto di suite; collega un fascicolo solo se `targetLocalId` appartiene al profilo configurato. Se non esiste un fascicolo resta un progetto comune visibile con invito a collegarne uno, senza inventare un bando. Nessuna modifica a catalogo pubblico, revisioni e ricevute. Snapshot di candidatura in sola lettura.

## Payload

ProjectSeed:

```json
{"schemaVersion":1,"organizationId":"UUID","source":{"system":"bandi|associago","entity":"preparation|project","localId":"opaque","revision":"opaque"},"title":"Nome","description":"Testo","plannedBudget":{"amount":"123.45","currency":"EUR"},"projectDeadline":null,"actorSubject":null}
```

`plannedBudget`, `description`, `projectDeadline`, `actorSubject` facoltativi/null. `actorSubject` è ricavato dall'autenticazione del server sorgente, mai dal corpo utente. Il registro non interpreta una scadenza bando come fine progetto.

ProjectProjection:

```json
{"schemaVersion":1,"organizationId":"UUID","suiteProjectId":"UUID","sourceVersion":1,"title":"Nome","description":"Testo","plannedBudget":{"amount":"123.45","currency":"EUR"},"projectDeadline":null,"targetLocalId":null,"links":{"librepm":null,"associago":null,"bandi":null}}
```

PeerSnapshot:

```json
{"schemaVersion":1,"organizationId":"UUID","suiteProjectId":"UUID","system":"associago|bandi","localId":"opaque-or-null","sourceVersion":1,"financial":null,"workforce":null,"preparation":null}
```

Financial: `{currency:"EUR",income:"0.00",expense:"0.00",balance:"0.00"}`. Workforce: `{assignedCount:0,completedHours:"0.00"}`. Preparation: `{localId:"UUID",version:1,title:"...",status:"draft",reviewDecision:"pending",requestedGrant:{amount:"0.00",currency:"EUR"}}`; campi non disponibili null. Nessun dato personale grezzo.

ProjectContext:

```json
{"schemaVersion":1,"organizationId":"UUID","suiteProjectId":"UUID","title":"Nome","description":null,"version":1,"instances":[{"system":"librepm","entity":"project","localId":"UUID"}],"links":{"librepm":null,"associago":null,"bandi":null},"sync":[{"system":"associago","status":"disabled","lastSuccessAt":null,"errorCode":null,"errorMessage":null}],"financial":null,"workforce":null,"preparation":null}
```

Sync status: `unlinked|pending|synced|error|disabled`. Le API utente omettono/redigono i riepiloghi finanziari per i ruoli che non possono consultarli. Errori comuni JSON `{message,code}` con 400 input, 401 identità peer, 403 permesso/organizzazione, 404 assente, 409 conflitto/versione, 503 configurazione/peer irraggiungibile.

## Convenzioni verificabili v1

- Gli elenchi restituiscono direttamente `ProjectContext[]`, senza wrapper.
- Import, registrazione, collegamento e sincronizzazione restituiscono HTTP 200 con `ProjectContext`; PUT peer restituisce HTTP 200 con `PeerSnapshot`, anche al primo upsert.
- `version` del contesto e `sourceVersion` sono interi tra zero e 2147483647. La versione nativa del fascicolo Bandi parte da 1 e ha lo stesso massimo. Anche `assignedCount` usa questo massimo: si evitano overflow nei motori SQL nativi.
- `projectDeadline` usa `YYYY-MM-DD`. I timestamp di sincronizzazione usano RFC 3339 con fuso orario.
- Gli importi e le ore sono stringhe decimali senza esponenti e senza scala fissa; budget, entrate, uscite e ore non sono negativi. Il saldo ammette valori negativi. Il denaro non passa attraverso float JSON.
- Il titolo comune ammette al massimo 200 caratteri, limite del progetto operativo LibrePM. AssociaGo rifiuta con 409 `CURRENCY_UNSUPPORTED` i preventivi non EUR e con 409 `AMOUNT_PRECISION` le frazioni non nulle inferiori al centesimo: nessun arrotondamento implicito, nessuna conversione.
- LibrePM rifiuta con 409 `SUITE_AMOUNT_PRECISION` un importo che il preventivo operativo nativo non può rappresentare senza perdita. Il rifiuto precede la creazione del progetto: nessun importo viene arrotondato per completare l'importazione.
- Nel seed i quattro campi descrizione, budget, scadenza e attore sono facoltativi e nullable. Nelle risposte tutte le proprietà dichiarate sono presenti; i riepiloghi non disponibili o non autorizzati sono null.
- La revisione sorgente è opaca: un nuovo invio della stessa chiave conserva il piano operativo esistente; una revisione diversa registra provenienza senza sovrascriverlo. Non si inventa un ordinamento lessicografico.
- Gli URL di configurazione non contengono query, frammenti o credenziali. I deep link restituiti possono includere `suiteProject`, senza token o segreti.
- I client peer Java usano HTTP/1.1 esplicito, anche su loopback: non tentano upgrade h2c che Uvicorn non supporta. La scadenza della richiesta include tutto il corpo della risposta, non soltanto gli header.
- `suite-v1.openapi.json` è il contratto verificabile; `scripts/integration/check_contract.py` verifica fixture e risposte catturate senza contattare produzione.

## Navigazione e scambi

- LibrePM: `?suiteProject=<UUID>` seleziona il progetto corretto dopo login; nessun fallback silenzioso al primo se un ID richiesto è inesistente/non autorizzato.
- AssociaGo: `?suiteProject=<UUID>` apre la nuova sezione Progetti col contesto risolto.
- Bandi: `/suite/projects/<UUID>` apre il mirror e il collegamento al fascicolo; login locale preserva il percorso di ritorno sicuro.
- La selezione persistita non equivale a replicare tutte le modifiche: ogni sito legge le proiezioni autorevoli e mostra stato/ultimo allineamento.
- Se un peer non è configurato o è giù, le altre app restano utilizzabili; nessun successo fittizio.
