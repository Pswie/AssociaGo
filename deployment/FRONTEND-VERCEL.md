# Frontend pubblicati e accesso della suite

30 ottobre 2026. I quattro frontend sono pubblicati sui domini dell'associazione.
Il dubbio sul progetto Supabase è stato chiarito e la sospensione temporanea è
terminata. Il sito principale mantiene tutte e cinque le aree con pari visibilità.
Sono stati rimossi slogan, statistiche e spiegazioni tecniche dal percorso di accesso.

**La verifica SSO con il normale accesso dell'utente è ancora in attesa.**
Il precedente tentativo chiedeva nuovamente le credenziali nei siti esterni;
le correzioni descritte sotto sono ora online. Il funzionamento dei servizi
interni sul sito principale è stato confermato dall'utente. Le verifiche
automatiche non sostituiscono la prova autenticata dei tre siti esterni.

## Indirizzi e progetti

| Area | Indirizzo pubblico | Progetto Vercel |
|---|---|---|
| Ingresso e aree del sito | <https://www.angelidelfuturoaps.com/admin> | terzo-settore |
| Soci, volontari e amministrazione | <https://gestionale.angelidelfuturoaps.com> | adf-gestionale-collaudo |
| Gestione dei progetti | <https://progetti.angelidelfuturoaps.com> | adf-bandi-collaudo |
| Bandi e bozze | <https://bandi.angelidelfuturoaps.com/collabora> | adf-bandi-portale |

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
commit, push o modifiche ai rami Git.

## Contenuti pubblicati e configurazione

- AssociaGo e LibrePM: build statiche aggiornate, API Google effettive e origine
  primaria configurate; CSP AssociaGo derivata dalle sole impostazioni pubbliche.
- Bandi: soli sorgenti web Next e risorse pubbliche selezionate, modalità gateway;
  esclusi backend locale, documenti, modelli, `.env` e biblioteca PDF del PC.
  Il gateway effettivo è `https://adf-bandi-portale-api-y5sbh5kxpa-oc.a.run.app`.
- Sito principale: sorgenti Astro aggiornati e nuova navigazione. I valori
  segreti server esistenti non sono stati decriptati, copiati o modificati.

Gli snapshot contengono inventari con SHA256 e vengono verificati prima
dell'upload. I due manifest del rilascio corrente sono:

- `deployment/.artifacts/frontends-20261030T142129Z-3822ef6f/state.json`
  per AssociaGo, LibrePM e Bandi;
- `deployment/.artifacts/primary-final-20261030T143251Z-2e080ec1/state.json`
  per l'ultima correzione del sito principale.

Gli artefatti intermedi rimangono conservati separatamente. Il candidato
primario intermedio non promosso non ha sostituito il sito pubblico.

## Verifiche eseguite e limite attuale

- Quattro build remote READY e produzione verificata tramite
  `project.targets.production`, oltre agli indirizzi pubblici HTTPS.
- Browser reale anonimo: ciascuna delle tre applicazioni apre automaticamente
  il login centrale, conservando nel ritorno il proprio progetto; slogan assente.
  Nessuna credenziale è stata inserita e nessun utente impersonato.
- Bandi pubblico: pagina HTTP200, API senza autenticazione HTTP401, funzioni
  locali HTTP404, percorso `/suite/projects/{uuid}` verso il contesto corretto.
- Quattro redirect precedenti HTTP308 con percorso e query conservati.
- Bandi: **394 test nell'ultima suite completa**, lint e build. Dopo il piccolo
  allineamento del provider, 11 test mirati e TypeScript superati; questi 11
  comprendono casi già contati, non vanno sommati ai 394. La nuova build Next
  remota è riuscita.
- AS e LibrePM: 9 regressioni del ritorno per ciascuna applicazione e build web
  riuscite; 16 regressioni API/policy frontend AssociaGo superate.
- Sito principale: 13 regressioni, inclusa la cattura anticipata prima del cleanup
  SDK; controllo Astro senza errori o warning e build riuscita.
- Cinque verifiche locali dell'integrità degli snapshot e dei blocchi di upload.

Resta da confermare con il normale account dell'utente che l'accesso sul sito
principale venga riconosciuto nei tre siti esterni. Non viene dichiarato
superato questo collaudo soltanto sulla base di mock, build o richieste anonime.

## Rigenerare un rilascio

Da AssociaGo, usando le origini effettive:

```powershell
python deployment/prepare_frontends.py `
  --api-associago https://adf-gestionale-collaudo-y5sbh5kxpa-oc.a.run.app `
  --api-librepm https://adf-bandi-collaudo-y5sbh5kxpa-oc.a.run.app `
  --bandi-project-id prj_oSIG5ZS4MPNB1CyUqb89asUo50hO `
  --gateway-url https://adf-bandi-portale-api-y5sbh5kxpa-oc.a.run.app
```

Il comando crea un nuovo `state.json` senza sovrascrivere i precedenti.
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
