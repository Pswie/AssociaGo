# Frontend pubblicati e accesso della suite

30 ottobre 2026. I quattro frontend sono pubblicati sui domini dell'associazione.
Il dubbio sul progetto Supabase è stato chiarito e la sospensione temporanea è
terminata. Il sito principale mantiene tutte e cinque le aree con pari visibilità.
Sono stati rimossi slogan, statistiche e spiegazioni tecniche dal percorso di accesso.

**L'utente ha confermato l'apertura di Gestionale e Progetti dopo le correzioni.**
Anche il funzionamento dei servizi interni sul sito principale era stato
confermato. Le verifiche automatiche non sono state usate per sostituire
questa prova normale. Bandi pubblica ora le31 pagine originali collegate al PC;
il primo collaudo dell'utente ha segnalato attese di caricamento. Sono ora
pubblicate le correzioni delle code vuote e dei trasferimenti grandi;
l'utente ha confermato il caricamento RUNTS nella propria sessione.
Copertura e limiti sono documentati in `Bandi/integration/ONLINE-LOCALE-PIANO.md`.

Nel tentativo successivo la sessione è stata riconosciuta in Progetti, ma
l'apertura rimaneva in attesa. Sono stati corretti due problemi distinti:
la configurazione pubblicata dei due frontend Java mancava del prefisso `/api`,
e il ciclo di avvio Progetti poteva cancellare un errore e ripartire continuamente.
Le due nuove build sono state pubblicate e la successiva apertura è stata
confermata dall'utente, mantenendo la sessione.

## Indirizzi e progetti

| Area | Indirizzo pubblico | Progetto Vercel |
|---|---|---|
| Ingresso e aree del sito | <https://www.angelidelfuturoaps.com/admin> | terzo-settore |
| Soci, volontari e amministrazione | <https://gestionale.angelidelfuturoaps.com> | adf-gestionale-collaudo |
| Gestione dei progetti | <https://progetti.angelidelfuturoaps.com> | adf-bandi-collaudo |
| Bandi: fonti online; strumenti originali e bozze accessibili dal menu | <https://bandi.angelidelfuturoaps.com/fonti> | adf-bandi-portale |

Team: `team_IO082y04OBhGW2doFuF6qYcy`, Villa Langellotti's projects.
Gli identificatori dei deployment correnti e gli esiti sono riportati in
[frontend-release-state.json](frontend-release-state.json).

I tre nuovi sottodomini sono stati assegnati e verificati nei progetti corretti.
La zona DNS esistente era già sufficiente: non è stato cambiato alcun record DNS.
I preview e gli indirizzi di deployment restano protetti da Vercel Authentication;
i domini pubblici usano l'autenticazione applicativa Clerk.

## Correzioni dell'accesso

Il sito principale presenta l'accesso e, dopo l'autenticazione, le cinque aree:
Bandi, Progetti, Gestionale, Agenda e servizi, Servizi al cittadino. Le tre
applicazioni esterne senza sessione aprono automaticamente l'ingresso centrale.
Bandi non mostra più due pulsanti di accesso o una seconda pagina introduttiva.

Con l'istanza Clerk di sviluppo esistente, i normali link fra origini non
trasferivano il contesto della sessione. Il sito principale usa ora i metodi
ufficiali `buildUrlWithAuth` e `navigate` dell'SDK per i collegamenti autorizzati,
anche per l'apertura in un'altra scheda. Il codice applicativo non legge,
memorizza o copia cookie o token per realizzare questo passaggio.

I tre frontend costruiscono un ritorno assoluto alla propria origine, conservando
il progetto selezionato e rifiutando destinazioni esterne introdotte nei parametri.
I parametri di trasporto vengono esclusi soltanto dalla copia usata come ritorno;
l'URL corrente viene lasciato all'SDK durante la sincronizzazione.

Clerk trasporta alcuni ritorni nel frammento `#/…`, oltre che nella query.
Il sito principale riconosce entrambi i formati: acquisisce anticipatamente solo
i tre parametri di ritorno, poi attende che l'SDK abbia una sessione attiva e
valida l'origine prima di proseguire. Questo copre anche chi era già autenticato
sul sito principale. Parametri ambigui, duplicati o destinazioni non ammesse
mantengono disponibile la scelta delle aree. Tre alias distinti con la stessa
destinazione, come nel flusso reale dell'SDK, sono accettati.

Il claim di ruolo amministrato sul server è stato inoltre applicato e riletto
in Clerk: [CLERK-CLAIMS.md](CLERK-CLAIMS.md). L'istanza resta quella di sviluppo
esistente: non sono stati creati account di produzione o attivati piani a pagamento.

## Indirizzi precedenti e sorgente Git

I seguenti redirect permanenti HTTP308 sono stati verificati mantenendo
esattamente percorso e query, incluso `suiteProject`:

| Indirizzo precedente | Destinazione |
|---|---|
| adf-gestionale-collaudo.vercel.app | gestionale.angelidelfuturoaps.com |
| adf-bandi-collaudo.vercel.app | progetti.angelidelfuturoaps.com |
| adf-bandi-portale.vercel.app | bandi.angelidelfuturoaps.com |
| terzo-settore.vercel.app | www.angelidelfuturoaps.com |

I vecchi indirizzi non vengono aggiunti indiscriminatamente alle origini ammesse
delle API. Gli indirizzi immutabili dei singoli preview restano destinati al QA,
non all'uso quotidiano.

È stato scollegato **solo AssociaGo** dal vecchio Git `Pswie/terzo-settore`,
che conteneva una copia precedente del gestionale. La conferma ordinaria
interattiva della CLI è stata completata senza flag di bypass; la rilettura
conferma `link: null` e produzione invariata durante la disconnessione.
Il collegamento Git del sito principale resta attivo. Non sono stati eseguiti
modifiche al Git del sito principale. Successivamente è stata pubblicata la
correzione CI di Bandi e il nuovo frontend LibrePM nel proprio ramo
`cloud/adf-bandi`, commit `78615261b43474f494e69564f4fff63c5aac1827`.

## Contenuti pubblicati e configurazione

- AssociaGo e LibrePM: build statiche aggiornate, API Google effettive e origine
  primaria configurate; CSP AssociaGo derivata dalle sole impostazioni pubbliche.
  `VITE_API_BASE_URL` comprende ora `/api`. Le `apiOrigins` nel file condiviso
  restano origini senza percorso: è il preparatore delle build ad aggiungere
  il prefisso richiesto dai due client. Il valore è stato verificato sia nei
  bundle locali sia nel JavaScript servito dai domini pubblici.
- Bandi: soli sorgenti web Next e risorse pubbliche selezionate, modalità gateway;
  esclusi backend locale, documenti, modelli, `.env` e biblioteca PDF del PC.
  Il gateway effettivo è `https://adf-bandi-portale-api-y5sbh5kxpa-oc.a.run.app`.
  `BANDI_NATIVE_ENABLED=1` abilita le schermate originali per il proprietario;
  i collaboratori restano nel portale delle bozze. Le richieste vanno al nodo
  PC autenticato, senza esporre porte locali.
- Sito principale: sorgenti Astro aggiornati e nuova navigazione. I valori
  segreti server esistenti non sono stati decriptati, copiati o modificati.

Persistite nei progetti Vercel anche le sole impostazioni pubbliche di Bandi
e del sito principale, per produzione e preview: istanza Clerk, origini,
modalità nativa, `NEXT_PUBLIC_OFFICIAL_SOURCES=1` e collegamento Bandi a `/fonti`. I segreti server
Clerk, SQL e dei contatti non sono stati letti o cambiati.

Gli snapshot contengono inventari con SHA256 e vengono verificati prima
dell'upload. I manifest del rilascio corrente sono:

- `deployment/.artifacts/frontend-associago-brand-20261030T162845Z/state.json`
  per AssociaGo;
- `deployment/.artifacts/frontend-librepm-brand-20261030T160627Z/state.json`
  per LibrePM;
- `deployment/.artifacts/official-frontends-20261030T202257Z-05a42234/state.json`
  per Bandi;
- `deployment/.artifacts/official-frontends-20261030T202257Z-05a42234/state.json`
  per l'ultima correzione del sito principale.

Deployment correnti: Bandi `dpl_Eh2bsaS1kQriCLF4QU9XdcmzrFDo` in `fra1`,
principale `dpl_8jK1uKpHjScZUWKhxChj6yWCG6UH`. Entrambi READY e promossi,
con alias riletti e otto controlli HTTP dei domini pubblici passati. I tre HTML
Bandi coincidono con le candidate; l'HTML primario coincide dopo la sola
normalizzazione dell'ID casuale del widget Clerk nelle sue due occorrenze.
L'anonimo non vede il chooser: il suo clic resta una prova della sessione reale.

Gli artefatti intermedi rimangono conservati separatamente. Il candidato
primario intermedio non promosso non ha sostituito il sito pubblico.

## Verifiche eseguite e limite attuale

- Quattro build remote READY e produzione verificata tramite
  `project.targets.production`, oltre agli indirizzi pubblici HTTPS.
- Browser reale anonimo: ciascuna delle tre applicazioni apre automaticamente
  il login centrale, conservando nel ritorno il proprio progetto; slogan assente.
  Nessuna credenziale è stata inserita e nessun utente impersonato.
- Bandi pubblico: pagina HTTP200, API native senza autenticazione HTTP401,
  percorso `/suite/projects/{uuid}` verso il contesto corretto. Le funzioni
  locali sono consentite solo dopo il controllo del proprietario sul gateway.
- Quattro redirect precedenti HTTP308 con percorso e query conservati.
- Bandi: **663 test nella suite completa finale**, lint,
  TypeScript e build gateway/nativa superati. Ulteriori18 test mirati del gate
  comprendono casi già contati e non vanno sommati al totale.
- AS e LibrePM: 9 regressioni del ritorno per ciascuna applicazione e build web
  riuscite; 16 regressioni API/policy frontend AssociaGo superate.
- Avvio LibrePM: 23 test mirati e 12 asserzioni sul vero componente React in
  StrictMode. L'errore iniziale resta visibile con “Riprova” nella stessa sessione,
  le risposte di una precedente identità vengono ignorate e le operazioni sono
  limitate nel tempo. Queste verifiche includono casi dei gruppi precedenti e
  non costituiscono un totale da sommare indiscriminatamente.
- Contratto delle API frontend: un test esegue i veri client JavaScript con
  l'ambiente generato e controlla `/api/v1/me`, `/api/v1/associations` e `/api/me`.
  Il prefisso è verificato anche nei bundle pubblici finali dei due siti.
- Sito principale: 13 regressioni, inclusa la cattura anticipata prima del cleanup
  SDK; controllo Astro senza errori o warning e build riuscita.
- Cinque verifiche locali dell'integrità degli snapshot e dei blocchi di upload.

L'apertura di Gestionale e Progetti è confermata dal normale account dell'utente.
Questa conferma riguarda l'accesso; non dichiara collaudata ogni singola azione
operativa. Bandi è stato pubblicato con663 test web e232
test PostgreSQL/ASGI superati; l'utente ha confermato il caricamento RUNTS.
Un file oltre4,5MiB nella sessione reale Vercel/Clerk richiede ancora verifica.

## Rigenerare un rilascio

Da AssociaGo, usando le origini effettive:

```powershell
python deployment/prepare_frontends.py `
  --api-associago https://adf-gestionale-collaudo-y5sbh5kxpa-oc.a.run.app `
  --api-librepm https://adf-bandi-collaudo-y5sbh5kxpa-oc.a.run.app `
  --bandi-project-id prj_oSIG5ZS4MPNB1CyUqb89asUo50hO `
  --gateway-url https://adf-bandi-portale-api-y5sbh5kxpa-oc.a.run.app `
  --bandi-native `
  --official-sources
```

Il comando crea un nuovo `state.json` senza sovrascrivere i precedenti.
`--official-sources` richiede esplicitamente `--bandi-native` e il gateway:
abilita soltanto la nuova consultazione, senza cambiare i permessi delle API
native. Il gateway richiede `bandiOfficialSourcesEnabled: true` nel piano
privato, che genera `COLLAB_OFFICIAL_SOURCES_ENABLED=true`. Il bundle runtime
rimane quello verificato, fissato alla versione 1. Gli adattatori non usano SQL,
Ollama o il nodo PC. Le 53 verifiche offline dei deploy helper sono passate.

Il sito principale di questo rilascio proviene dallo snapshot già revisionato
del precedente rilascio; cambia soltanto `PUBLIC_BANDI_URL`. Le modifiche
preesistenti nella sua repository di lavoro non sono state pubblicate.
`deploy_frontend_preview.py --state PERCORSO/state.json --app NOME` verifica
lo snapshot; `--inspect-with-cli` esegue solo l'ispezione CLI e `--deploy-preview`
crea un preview protetto. Un nuovo progetto vuoto può essere classificato da
Vercel come produzione al primo upload: controllare sempre anche target e alias.

Per questo rilascio, i candidati di produzione sono stati creati dagli stessi
snapshot con `--target production --skip-domain --no-wait`, poi controllati
prima della promozione. `--skip-domain` evita lo spostamento del traffico durante
la build. Il solo endpoint di promozione del preview rispondeva HTTP422; creare
un candidato produzione ha risolto il vincolo senza usare il vecchio Git.
Lo script operativo e le ricevute di questa esecuzione sono conservati sotto
`deployment/.artifacts/stage_reviewed_frontends.py` e nei rispettivi manifest.
Una scrittura dall'esito incerto richiede la rilettura dello stato prima di
ripetere l'upload. Le promozioni usano ID di progetto e deployment espliciti.

Riferimenti ufficiali: [Clerk buildUrlWithAuth](https://clerk.com/docs/js-frontend/reference/objects/clerk#build-url-with-auth),
[modelli di funzionamento Clerk](https://clerk.com/docs/guides/how-clerk-works/overview),
[Vercel deploy](https://vercel.com/docs/cli/deploy),
[promozioni Vercel](https://vercel.com/docs/cli/promote),
[disconnessione Git](https://vercel.com/docs/cli/git).
