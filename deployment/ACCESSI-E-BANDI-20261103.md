# Accessi condivisi e verifica Bandi — 3 novembre 2026

## Cosa puoi fare adesso

Da **Impostazioni → Utenti e accessi** di Gestionale, Progetti o Bandi puoi gestire lo stesso elenco di persone. Una modifica vale per tutti e tre i siti.

1. La persona si registra con Clerk, usando lo stesso account per i siti.
2. Nel pannello premi **Aggiorna iscritti da Clerk**. Compaiono anche le persone che non hanno ancora aperto un servizio.
3. Seleziona la persona e passa lo stato da **In attesa** ad **Attivo**.
4. Per ciascuna applicazione scegli il ruolo oppure **Personalizzato**, con lettura e modifica delle singole aree.
5. Salva. Puoi successivamente sospendere l'accesso e riattivarlo conservando le assegnazioni.

L'elenco ha ricerca e paginazione di 20 utenti. Se gli stessi accessi sono stati modificati da un'altra scheda, il salvataggio richiede di ricaricare la persona: non sovrascrive silenziosamente l'altra modifica.

| Ruolo | Cosa permette |
| --- | --- |
| Nessun accesso | Nessuna area di quell'applicazione |
| Lettore | Consultazione delle aree operative |
| Operatore | Consultazione e modifica delle aree operative |
| Responsabile | Tutte le aree previste dal catalogo, comprese le impostazioni |
| Personalizzato | Lettura e modifica selezionate per area |

La modifica comprende la lettura. La gestione degli altri account e le operazioni AI/locali di Bandi restano del titolare. Il titolare è identificato dal tuo ID Clerk configurato e verificato, non dal nome, dall'email o dal fatto di essere il primo iscritto. Il pannello protegge il tuo account da sospensione e modifica dei privilegi.

## Dove è pubblicato

- [Area riservata](https://www.angelidelfuturoaps.com/admin)
- [Gestionale](https://gestionale.angelidelfuturoaps.com)
- [Progetti](https://progetti.angelidelfuturoaps.com)
- [Bandi: impostazioni](https://bandi.angelidelfuturoaps.com/impostazioni)

Il selettore principale consente di raggiungere le tre applicazioni dopo l'accesso Clerk. L'abilitazione effettiva viene controllata da ciascuna applicazione attraverso il servizio comune: un nuovo iscritto resta in attesa fino alla tua approvazione. Il trasferimento della sessione Clerk già presente è conservato.

## Come funzionano le autorizzazioni

Clerk continua a verificare l'identità. Le autorizzazioni sono conservate in due nuove tabelle private nello schema `associago` su Supabase: `suite_access_users` e `suite_access_audit`.

Le API rileggono i permessi a ogni richiesta. La revoca è quindi efficace anche con lo stesso token ancora valido; i vecchi ruoli contenuti nei token non possono ripristinare un permesso revocato. Le interfacce aggiornano inoltre la visibilità delle aree al ritorno sulla finestra e periodicamente. Una decisione già verificata viene riutilizzata solo all'interno della medesima richiesta, evitando una query duplicata senza introdurre una cache dei permessi tra richieste.

Se il servizio centrale non è disponibile, le API segnalano indisponibilità e non concedono accessi per ripiego. I collegamenti tecnici tra server conservano credenziali e controlli separati. Progetti e bozze mantengono i rispettivi controlli di proprietà e appartenenza.

L'approvazione di un account non crea un'iscrizione legale nel libro soci. L'anagrafica per accedere e l'iscrizione associativa sono registrazioni separate.

La chiave Clerk server già configurata sul sito principale è rimasta su Vercel. Un nuovo endpoint server, accessibile soltanto al titolare, restituisce l'elenco minimo degli iscritti. Il servizio centrale inoltra la sessione verificata del titolare; la chiave non è stata esportata, copiata nel browser o inserita nel codice. L'endpoint anonimo è stato verificato e restituisce 401.

## Altri problemi corretti

- Le API dei metodi di pagamento, del fisco, delle impostazioni dell'associazione e delle statistiche verificano anche l'associazione reale dei dati.
- L'autore delle operazioni fiscali deriva dall'identità verificata; un parametro nel browser non può scegliere un'altra persona.
- Le somme fiscali, gli eventi e il riepilogo centrale usano l'associazione configurata. Il riepilogo soci centrale deriva dal libro soci di quell'associazione.
- Le risposte dei metodi di pagamento nascondono le configurazioni riservate ai lettori. Un salvataggio ordinario aggiorna soltanto le colonne ordinarie: non può cancellare o sovrascrivere credenziali modificate contemporaneamente dal titolare.
- Le configurazioni riservate del gestore pagamenti e del database sono protette dal controllo del titolare.
- Le statistiche rispettano i permessi economici e non inventano aventi diritto storici o percentuali quando i dati non consentono di ricostruirli.
- Le operazioni legacy sui buoni restano del titolare; il menu del titolare le mantiene visibili. La sola assegnazione di un ruolo contabile non concede questi percorsi ancora riservati.

## Database, rilascio e conservazione

La migrazione PostgreSQL **V4** è stata applicata una sola volta tramite Flyway, con ruolo proprietario separato dalle credenziali dell'applicazione. Prima è stata verificata su PostgreSQL isolato; dopo sono stati controllati privilegi, RLS e struttura.

Le **64 tabelle precedenti** conservano struttura e conteggi. Sono state aggiunte soltanto le due tabelle degli accessi. I ruoli pubblici Supabase non possono leggerle; il ruolo del backend può aggiungere eventi all'audit, senza aggiornarli o cancellarli. Sono previste anche le migrazioni equivalenti per SQLite e MySQL.

Sono state compilate e pubblicate immagini distinte per i tre backend. Ogni candidato è stato avviato senza traffico, controllato e poi promosso. Le tre revisioni attive terminano con `access-20261103-a`. Segreti, volumi, limiti, account di servizio e altre configurazioni dei servizi Google sono stati conservati. Non sono stati attivati piani a pagamento o nuove versioni di segreti.

Gli originali, le ricevute, le versioni precedenti e gli artefatti di verifica sono conservati. I lavori locali Bandi e i dati sul PC non sono stati sostituiti da una copia web.

## Verifiche completate

| Area | Esito |
| --- | --- |
| AssociaGo: Java, sicurezza, integrazioni, presenze e scope | 157 test superati |
| AssociaGo: client API, runtime e permessi UI | 24 test superati |
| LibrePM: Java | 40 test superati |
| LibrePM: client e permessi UI | 30 test superati |
| Bandi: nuove autorizzazioni API e catalogo gateway | 56 test superati |
| Bandi: interfaccia | 936 test superati in 102 file |
| Sito principale: directory e navigazione | 14 test superati |
| Strumenti di preparazione e artefatti | 46 test superati |
| Produzione dopo il rilascio | 23 controlli HTTP, due asset SPA e CSP verificati |

Superate anche le compilazioni e le verifiche statiche pertinenti. I controlli negativi includono utenti in attesa o sospesi, ruoli di organizzazioni estranee, revoca con token identico, modifica concorrente, accesso ad associazioni diverse e protezione del titolare. Le tre origini autorizzate sono accettate da CORS; un'origine estranea è respinta.

**Limite della verifica:** non è stata ancora verificata l'interazione nella tua sessione reale. Il connettore Opera richiede nuovamente autenticazione; la richiesta per ricollegarlo è stata lasciata aperta. I test automatici e le verifiche pubbliche non vengono presentati come un accesso effettuato al tuo posto.

## Stato reale delle analisi Bandi

La consultazione dell'archivio e l'esecuzione di un'analisi AI hanno esiti distinti.

- **Fabbrica:** documenti acquisiti integri e verificati tramite hash. Tutte le 88 unità pubbliche dei tre documenti erano presenti nell'input. Il tentativo v2.15 è terminato dopo 1.800 secondi senza una risposta AI completa: non esiste un'analisi valida da dichiarare riuscita.
- **Campania:** la richiesta non era partita; il bando è scaduto il 2 novembre. Non è stata forzata un'analisi automatica dopo la scadenza.
- **CON IL SUD:** la precedente analisi preliminare con sei chiamate completate è conservata; non viene trasformata in una nuova verifica completa.

Dopo il riavvio del PC mancavano Docker e Ollama, mentre il broker conservava un lavoro incerto. Il ripristino ha verificato l'identità dei processi e l'assenza di inferenze attive; quel solo lavoro è stato chiuso come fallito senza ripeterlo. I sei successi precedenti sono invariati. Docker, Ollama, API, worker e Node sono stati ripristinati.

Il vecchio lettore poteva richiedere 8.192 token di uscita, incompatibili con il limite di 30 minuti alla velocità osservata. È stata preparata una nuova lettura per tre ruoli, risposte brevi, corpus sempre completo e suddivisione esplicita in caso di overflow. Il replay delle ricevute ricostruisce le partizioni e respinge risultati incompleti o manomessi. La v2.16 preparata ha superato **230 test offline**, senza avviare inferenze.

**Verifica AI ancora in corso:** prima della distribuzione resta da chiudere il limite della successiva riparazione metadata e svolgere una prova nativa nuova, con API e proiezione coerenti. I test offline non dimostrano che il modello abbia completato un'analisi reale, né certificano semanticamente ogni documento. Questo paragrafo verrà aggiornato con l'esito effettivo della prova.

## Codice e ricevute

- AssociaGo: `bc4a81dae2ff05ccf171d7f88d64915c9f00a72a`.
- LibrePM: `111eee0750c1df607368c61218f55c4c1d068e68`.
- Bandi, accessi: `70953a3ef528a026674f4b76bb189f39d085eb68`.
- Sito principale: ramo `release/adf-suite-access-20261103`, commit `32cefcd9578f36c9b663d1272a1f30bd3ac33c76`.

Gli otto lavori originali non committati del sito principale sono rimasti intatti. Il ramo di rilascio contiene anche l'esatta base SSO già pubblicata. Il collegamento Git automatico di quel solo progetto Vercel è stato disconnesso per evitare di pubblicare la vecchia base del ramo principale; le pubblicazioni controllate tramite CLI restano operative.

Ricevute locali principali in `AssociaGo/deployment/.artifacts`:

- `nov3-shared-access-publication-a3e80e18f80e45fc9d34a3b60d968dff/publication-receipt.json`.
- `nov3-independent-public-check-bf5be155eef841758155a7681f73f355/receipt.json`.
- `nov3-access-migration-b3e055721c9f42b3a02d7256e63f5bad/receipt.json`.
- `nov3-associago-access-v4-a115e966abb94950a8b8583121d6a7ad/receipt.json`.
- `nov3-primary-navigation-329f265c5c1642bf96a88028787c1691/promotion-receipt.json`.

Le credenziali e i log riservati restano nella cartella privata del PC e non sono inclusi in questo documento.
