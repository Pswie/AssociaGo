# Frontend del gestionale — interventi del 30 ottobre 2026

Obiettivo: rendere coerente il gestionale di Angeli del Futuro e completare i percorsi già supportati dal backend, conservando accesso, ruoli e dati esistenti.

- [x] Modifica eventi: il dettaglio passa l'identificativo e il modulo usa PUT sull'evento scelto, anche quando riceve l'oggetto già caricato.
- [x] Data/ora locali di eventi e assemblee conservate senza conversione UTC.
- [x] Errori visibili e Riprova per cruscotto e liste Soci, Attività, Eventi, Inventario, Assemblee e Coupon; blocco degli invii ripetuti e dei moduli non ancora caricati per eventi, assemblee e inventario.
- [x] Interfaccia presenze: sessioni per attività/anno; creazione/modifica/eliminazione delle sessioni; inserimento, modifica di stato/orario/note e rimozione delle registrazioni.
- [x] Selettore dei partecipanti presenze circoscritto all’attività: `membership:ID` e `member:ID` distinti, anche quando i numeri coincidono. Tessera e stato storico visibili, senza accoppiamenti per nome o email.
- [x] Attività: caricamento dei dati operativi indipendente da costi e certificati riservati; indicatori economici e campi di costo/compenso coerenti con i permessi esistenti. I dati oscurati non diventano falsi zeri o pagamenti in sospeso.
- [x] Aggiunta dei partecipanti alle attività da un elenco dedicato: il POST usa lo `userId` esplicito fornito dal server, conservando separato il `membershipId`.
- [x] Menu profilo con tastiera, focus ed Esc; finestra informazioni con gestione focus; ricerca visibile e completa delle sole pagine autorizzate; titoli delle finestre specifici.
- [x] Manuale: URL relativi al base path, lingua normalizzata, fallback reale senza interpretare la pagina HTML come manuale, annullamento delle richieste obsolete e selettore dei capitoli su telefono.
- [x] Logo ufficiale e nome Angeli del Futuro APS; modulo Gestionale; attribuzioni AssociaGo e collegamento al sorgente conservati.
- [x] Test browser, lint, unit test e build della prima versione frontend.
- [x] Verifica frontend finale con i contratti aggiornati di presenze e attività.
- [x] Verifica congiunta del backend e delle migrazioni e pubblicazione del gestionale: 270 test server passati, V3 applicata, frontend e revisione server promossi insieme; pagina, JS e logo pubblici verificati dal coordinatore.

## Logo e identità

Fonte: `terzo-settore/sito/public/brand/logo-ufficiale.png`, fornita dall'associazione. Copia esatta in `desktop/src/renderer/src/assets/brand/logo-ufficiale.png`, con il file di provenienza originale accanto.

SHA-256: `24bfb608601d5a3127dc9ee59b09917de11e1857c1b42c9d9560d1c3a662fa5d`.

Nessuna ricostruzione o modifica dell'immagine. Nome e asset sono centralizzati in `branding.js`; i colori del gestionale web richiamano blu e corallo del logo. Gli stati di successo, avviso ed errore rimangono distinguibili. Sistemati il contrasto del testo secondario e dei colori di stato nel tema scuro.

## Verifiche eseguite sul frontend

Fixture di sviluppo: `frontend-harness.html`; monta i componenti reali con dati sintetici e conserva le chiamate in memoria. Non è un ingresso della build di produzione e non modifica il database.

- Evento: un solo aggiornamento dell'ID 41, nessuna creazione; `2026-10-30T00:15` preservato. Il mancato caricamento lascia Salva disabilitato e mostra il motivo.
- Assemblea: aggiornamento dell'ID 81 con data locale `2026-10-30T00:15` invariata.
- Presenze: Membership 301 aggiunta accanto al Member storico 301, senza collisioni; PUT della registrazione conserva il soggetto e salva note/orario `16:45`; DELETE confermata rimuove solo la registrazione scelta. Iscrizione storica scaduta 702 selezionabile. I partecipanti già registrati scompaiono dai candidati dello stesso tipo.
- Sessione sull’attività 71 con data `2026-10-30` e orari `17:00`–`18:30`, verificata nella prima iterazione.
- Elenco partecipanti negato con HTTP 403: errore e Riprova visibili, nessun identificativo personale inventato; il mancato elenco non cancella le registrazioni già caricate.
- Attività, ruolo coordinatore: dettaglio operativo consultabile, nessuna richiesta ai costi o ai modelli di attestato; pagamenti, compensi, importazioni e report economici nascosti. PUT dell’attività 71 omette `cost`, PUT dell’istruttore 801 omette `compensation` e `compensationType`; il backend conserva i valori esistenti. Presidente: indicatori, costi e attestati disponibili.
- Partecipante attività: candidato con `userId=91` e `membershipId=301`, POST con `user.id=91` e data locale corretta. Il selettore non legge l’anagrafica globale.
- Cruscotto e lista Eventi: errore visibile, Riprova ricarica i dati.
- Menu profilo: apertura da tastiera, frecce, finestra informazioni, Esc e ritorno del focus al pulsante.
- Ricerca: visibili anche Progetti e Fatture ricevute; navigazione al registro Presenze riuscita.
- Manuale: caricamento del capitolo reale riuscito.
- Test mobile esistente: 320, 375, 390, 768 e 1280 px, cassetto e finestra a schermo intero passati. Sul guscio completo a 320 px: nessuno scorrimento orizzontale e tutti i controlli della testata da almeno 44 px; titolo su riga propria.
- Audit automatico axe WCAG 2 A/AA: nessuna violazione sui due scenari provati (cruscotto scuro e presenze); verifiche manuali residue su contrasto di gradienti e controlli nativi, senza dichiarare una certificazione generale.
- `npm run lint`: passato senza avvisi; `npm run test:unit`: 18 test passati, inclusi URL circoscritti e riferimenti espliciti dei due contratti. Build web ed Electron passate. Restano gli avvisi preesistenti sulla dimensione dei bundle e sui dati Browserslist.

Le correzioni alle liste e i contratti aggiornati sono inclusi nella verifica frontend finale. Il dettaglio attività su telefono è stato corretto da 452 a 390 px di larghezza effettiva, senza uscita laterale. Nessuna pubblicazione remota da questa attività.

## Limiti da conservare

Il contratto presenze conserva `memberId` della tabella storica `members` e aggiunge `membershipId` per i soci nuovi. Ogni registrazione identifica esattamente un tipo; l’istruttore di sessione continua a usare soltanto un Member storico esplicito. L’interfaccia usa gli endpoint aggiornati insieme alle relative migrazioni del backend; non va pubblicata separatamente da quel completamento. La verifica frontend usa dati sintetici e non sostituisce la verifica del database o dei permessi lato server.
Le impostazioni cloud e l'autenticazione condivisa già funzionanti non fanno parte di questi interventi.
