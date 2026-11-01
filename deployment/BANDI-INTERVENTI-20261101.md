# Bandi: interventi e funzionamento

Verifica del 1 novembre 2026. Questo resoconto riguarda la revisione della sezione Bandi; le pubblicazioni precedenti della suite sono descritte in `VERIFICA-COMPLETA-20261101.md` e `LAVORI-COMPLETATI.md`.

## Come consultare i bandi

L'elenco principale è [Bandi](https://bandi.angelidelfuturoaps.com/bandi), con cinque schede per richiesta. I dettagli vengono caricati quando si apre una scheda. I filtri distinguono aperti, in apertura, da verificare e scaduti; si può cercare per testo, ente e stato dell'analisi.

Sul tuo PC, scegli una volta **Usa questo PC** e consenti al browser il collegamento locale, se lo richiede. Il browser ricorda il dispositivo. La lettura passa direttamente al catalogo locale, senza coda remota. Il primo collegamento lascia il tempo necessario per rispondere alla richiesta del browser.

Su telefono e altri computer la consultazione usa l'indice online. Se il lettore locale non risponde, il sito torna online. La disponibilità del tuo PC e il ruolo di amministratore non vengono usati per decidere che un altro dispositivo debba leggere dal PC.

L'accesso resta quello condiviso Clerk. Il lettore locale verifica il tuo account; i collaboratori non possono usarlo per controllare il computer. La lettura online conserva i controlli di accesso del portale.

## Perché l'elenco precedente era confuso

Nell'archivio locale c'erano 957 schede non archiviate: solo due risultavano aperte e una delle due aveva già superato la scadenza. C'erano inoltre risultati di progetti, assegnazioni, programmi ricorrenti e molte schede senza scadenza. Quei dati sono utili come archivio, ma non costituiscono un elenco di opportunità aperte.

Ho separato la consultazione operativa dalla conservazione dei dati storici. Una notizia, una graduatoria o un programma ricorrente non viene presentato come un bando aperto. Le opportunità senza informazioni sufficienti rimangono **Da verificare**. Una scadenza passata chiude una vecchia scheda anche se nell'archivio era ancora marcata aperta.

Le prime 87 segnalazioni delle quattro fonti quotidiane sono state controllate sui contenuti effettivi delle pagine: 1 aperta, 55 chiuse e 31 da verificare. Sono poi state aggiunte le schede locali pertinenti e le analisi già disponibili. Il numero cambia con le pubblicazioni successive; 87 non significa 87 bandi aperti.

Esempi corretti: “Bando Volontariato 2026”, scaduto il 30 settembre, resta chiuso; gli avvisi per reclutare operatori volontari o aderire a Carta Giovani Nazionale non diventano automaticamente opportunità di finanziamento per l'associazione.

## Analisi sul PC e memoria online

Il risultato di ogni analisi resta collegato all'identità del bando, alla versione delle fonti, al profilo dell'ente e al metodo utilizzato. Le nuove pubblicazioni aggiornano la scheda senza cancellare lo storico. Un errore non elimina l'ultima sintesi utile.

Se cambia una fonte, un profilo o il metodo, il precedente risultato viene indicato come da aggiornare. Non viene riutilizzato come una verifica attuale. Gli input invariati non devono essere analizzati nuovamente a ogni controllo.

La pubblicazione usa una coda persistente sul PC: prima conserva il pacchetto da inviare, poi registra la ricevuta del server. Una risposta persa può essere recuperata senza duplicare la pubblicazione. Un conflitto di versione richiede una verifica; il programma non sovrascrive alla cieca una versione più recente.

La prova reale ha riguardato **Fondazione Cattolica – Intrapresa Sociale 2026**, con scadenza 13 novembre. Il modello locale ha completato sei passaggi in circa dodici minuti, senza riparazioni o ripetizioni osservate. Il maggior fabbisogno misurato è stato 23.382 token sui 32.768 disponibili. Il fascicolo non è stato tagliato per farlo entrare.

Il risultato per APS e cooperativa è stato pubblicato e confrontato indipendentemente con quello locale: sintesi, prove, metodo e identificativi corrispondono. È una **valutazione preliminare statutaria**; non è una verifica completa di tutti i requisiti operativi, economici e documentali. L'interfaccia mantiene questa distinzione e non assegna un falso esito di ammissibilità.

La vecchia richiesta che richiedeva 51.855 token contro 32.768 disponibili è conservata. Ho verificato che si era fermata prima dell'inferenza e che i processi originali erano terminati; la revisione è registrata separatamente, senza riscrivere o riprovare quella richiesta. I nuovi casi troppo grandi vengono segnalati singolarmente senza bloccare tutti gli input compatibili.

Il caso Campania per l'autismo resta oltre il limite del metodo e richiede un'analisi estesa. Il bando CIS sui beni confiscati è stato avviato automaticamente dopo il ripristino della fonte: una risposta del modello non rispettava il contratto sulle regole di accesso ed è stata rifiutata. Quel risultato non è una verifica valida. La richiesta fallita viene conservata e non viene ripetuta automaticamente con gli stessi input; la sua revisione operativa permette agli altri bandi di proseguire. Le date mostrate sono scadenze di calendario: l'orario preciso resta nell'avviso della fonte.

## Ricerca quotidiana e nuove fonti

La ricerca online continua tramite **cron-job.org**, job `8766630`: il richiamo avviene ogni cinque minuti, con almeno quindici minuti tra elaborazioni e almeno ventiquattro ore prima di ricontrollare la stessa fonte. Il job `8758073` resta dedicato alla manutenzione. Non è stato aggiunto un cron GitHub.

La copertura quotidiana verificata comprende Fondazione CON IL SUD, Fondo per la Repubblica Digitale, Politiche Giovanili e PN Inclusione. Questo non equivale a una ricerca esaustiva di ogni sito italiano. Una nuova pubblicazione viene prima classificata; non viene etichettata automaticamente come bando adatto all'associazione.

Ho corretto anche il lettore locale di Fondazione CON IL SUD: non riconosceva il badge ufficiale “In scadenza” e aveva bloccato il controllo della fonte. La ripresa è stata limitata a quella risorsa, con copia dello stato precedente e confronto prima della modifica. Sono state rilette 50 schede e verificato il bando corrente: i fatti erano invariati, quindi non sono serviti nuovi record PostgreSQL, backup o analisi. La conferma riguarda la singola scheda provata, non tutte le schede della stessa pagina.

Quando il PC è spento, la raccolta delle segnalazioni online continua e le analisi già pubblicate restano consultabili. Le nuove analisi locali attendono che il PC sia acceso.

Il coordinatore **AngeliDelFuturo-BandiCatalog** è attivo su Windows ogni cinque minuti, con una sola istanza. Riconcilia i lavori, pubblica al massimo cinque schede e controlla cinque riferimenti online per giro. Le nuove analisi usano il metodo locale qualificato e un solo writer alla volta. La raccolta di nuove risorse riusa tre adattatori dichiarati: CON IL SUD, Politiche Giovanili e PN Inclusione. I nuovi URL non coperti da un adattatore, compresi quelli del Fondo per la Repubblica Digitale diversi dalla risorsa già dichiarata, restano da verificare.

Il precedente task **Bandi - Analisi locale** è disabilitato e conservato con il suo XML. Non sono stati modificati i task di preparazione dei progetti, backup e avvio del nodo. La coda è attiva, senza pausa globale; le richieste fallite già revisionate non vengono rianalizzate con gli stessi input.

Il controllo completo del coordinatore è limitato a venti minuti, con limite Windows di ventidue minuti e rilevamento di stallo di novanta secondi. I backup di dati nuovi possono richiedere diversi minuti: il supervisore osserva PID reale e crescita dei byte. Dopo un errore nella fase di backup o scrittura la fonte resta bloccata per revisione, senza ripetizioni automatiche. I dump parziali sono conservati. Un controllo della fonte invariata richiede solo un'attestazione, senza backup dell'intero database.

## Dati e spazio

| Dove | Contenuto |
| --- | --- |
| Siti delle fonti | Pagine e documenti ufficiali, raggiungibili dal collegamento nella scheda |
| PC | Archivio originale, documenti necessari all'AI, profili privati, inferenza e ricevute locali |
| Cloudflare D1 privato | Schede essenziali, stato dei bandi e proiezioni sintetiche delle analisi |
| Supabase | Database della suite e copia storica già presente, conservata |

Le pagine dei siti non offrono tutte un'API uniforme utilizzabile in streaming. Per avere ricerca, filtri e analisi consultabili a PC spento serve un piccolo indice online. I documenti si aprono dalla fonte; non vengono copiati in D1. L'AI può acquisire sul PC il fascicolo che deve leggere.

Il nuovo indice occupava **400 KiB** dopo la prima importazione e circa **500 KB** dopo le prime due pubblicazioni locali. Non è la copia dell'archivio da 203 MB. Non sono stati aggiunti documenti o nuove tabelle per questo indice su Supabase; la vecchia copia Supabase è conservata e il suo spazio non è stato dichiarato liberato.

Il database completo sul PC misura circa **12,7 GB fisici**: non è necessario trasferirlo interamente per consultare i bandi. Le nuove importazioni locali sono limitate e mantengono i controlli del database e del backup; i file originali rimangono nel loro archivio.

## Velocità e consumi

- Cinque risultati effettivi per richiesta, con cursore del server e dettagli su richiesta.
- Nessuna materializzazione dell'archivio da 203 MB per il nuovo elenco Bandi. Quel percorso rimane separato per le consultazioni storiche che lo usano.
- Nessuno scaricamento di tutte le pagine o avvio delle analisi aprendo l'elenco.
- Eliminati i controlli periodici inutili sul nodo nelle pagine operative; il pannello dei lavori controlla i lavori attivi quando è visibile.
- Le interrogazioni identiche condividono una lettura breve sul gateway; una ricerca lenta non blocca tutte le altre.
- La lettura delle analisi locali usa proiezioni verificate in background: non deve ripetere l'OCR nei GET della lista.
- Il nodo controlla il server ogni trenta secondi quando è inattivo, con presenza ogni sessanta secondi. I lavori attivi mantengono rinnovi ogni dieci secondi. I comandi remoti che richiedono il PC attendono il successivo controllo; la nuova lista Bandi legge direttamente dal PC abbinato o dall'indice online.

Sul Worker reale le pagine di cinque schede misurate trasferivano circa **3,8–4,7 KB**, in **0,65–0,75 secondi** dal PC. La CPU misurata in produzione era 5 ms per una lista, 3 ms per un dettaglio e 4 ms per il manifest. Un controllo cron naturale senza nuovo crawl ha impiegato 2 ms. Sono misure del campione, non una garanzia di tempi per ogni rete o futura scansione dei siti.

La lettura locale misurata in un processo nuovo impiegava **0,74 secondi** per la lista e **0,53 secondi** per il dettaglio. Le letture successive impiegavano circa mezzo secondo. Le prove sostituivano il vecchio lettore OCR con un errore deliberato: la consultazione ha continuato a funzionare, con zero OCR, inferenze e scritture SQL nei GET.

Cloudflare resta sul piano Workers Free, senza attivazione di prodotti a pagamento. D1 Free applica limiti giornalieri: al superamento rifiuta le richieste. [Documentazione Cloudflare](https://developers.cloudflare.com/changelog/post/2026-09-01-d1-free-tier-limit-enforcement/).

Google Cloud conserva la fatturazione già attiva. Ho ridotto traffico e lavoro necessario e mantenuto i limiti delle istanze; un avviso di budget non costituisce un tetto di spesa. Il registro dopo le due piccole build di questa revisione misurava circa **402,3 MB**, sotto la soglia conservativa utilizzata di 500 MB. I vecchi 52 MB in attesa della pulizia interna risultano recuperati nella nuova misura.

Le versioni attive dei segreti nel progetto Google sono **sei**, comprese le versioni precedenti conservate: è il limite gratuito pubblicato per Secret Manager. Nessun altro aggiornamento di segreti è stato aggiunto per la correzione della paginazione. [Prezzi Secret Manager](https://cloud.google.com/secret-manager/pricing).

Non posso garantire costo zero illimitato per l'intera infrastruttura Google già configurata. Non sono stati attivati nuovi piani a pagamento, account o R2.

## Verifiche

| Prova | Esito |
| --- | --- |
| Frontend prima dell'ultima correzione del primo collegamento locale | 907 test in 100 file superati; TypeScript ed ESLint superati |
| Ultima modifica del collegamento al PC | 23 test mirati superati, inclusa l'attesa del permesso del browser; TypeScript ed ESLint superati |
| API, autenticazione del lettore, gateway, cursori e nodo | 95 test finali superati |
| Catalogo locale, cache, pubblicazione e ciclo delle analisi | 109 test superati; copie precedenti, attesa, errori e provenienza verificati |
| Lettura delle fonti e importazione limitata | 82 test superati, inclusi caso reale del badge, blocchi dei writer e conservazione dei backup parziali |
| Worker/D1 | 29 test nel runtime nativo superati; 35 controlli iniziali remoti e 87 segnalazioni percorse tutte con pagine da cinque |
| Gateway Google pubblicato | 22 controlli sul codice effettivo nell'immagine cloud e 9 controlli HTTP pubblici superati |
| Frontend pubblicato | 19 controlli HTTP sul dominio: stato, dimensione e hash corrispondono al candidato verificato |
| Browser con sessione sintetica | Tre larghezze, cinque schede, paginazione, dettaglio, errori, assenza di falsi verdi e controlli di accessibilità superati |
| AI reale e pubblicazione | Sei passaggi conclusi; risultato locale e online confrontato, due revisioni del bando conservate |
| Lettore sul PC | Salute, blocco senza autenticazione, origine errata, preflight CORS e arresto ordinato verificati |

L'ultimo controllo dei dati reali ha restituito tre bandi aperti, con date coerenti e gli stessi stati descritti sopra, in 0,83 secondi. Sono state validate anche le tre schede di dettaglio, impedendo esplicitamente scritture SQL e il vecchio percorso OCR. Questi numeri appartengono a verifiche diverse e non vengono sommati per creare un totale artificiale.

Pubblicazioni effettive:

| Componente | Versione |
| --- | --- |
| Frontend sul dominio Bandi | `dpl_4zhagLBQf6vjHFz9gP1o6a1Maeqb` |
| Gateway Google, 100% traffico | `adf-bandi-portale-api-operativi-20261101-b` |
| Immagine gateway | `sha256:b1e1ba70a3c5688855066297fcd9c0880a41fd6c881b92247c11b4b08a2f98cc` |
| Configurazione privata gateway | Versione 3; versioni precedenti conservate |
| Worker indice quotidiano | `e5693862-4387-47a3-9dc3-3c048714bf21` |

La correzione della paginazione è stata verificata con il cursore firmato reale, passando da cinque risultati ai successivi cinque senza duplicati. I motivi di errore Campania e CIS sono stati ripubblicati con ricevute e controllati online; le sintesi, le prove e le versioni storiche precedenti sono rimaste presenti. Nessuna verifica AI non valida è stata trasformata in un esito positivo.

Le prove nella tua sessione personale Opera non sono state eseguite: il connettore richiede ancora una nuova autenticazione. I test con sessioni sintetiche non vengono presentati come una prova del tuo login reale. Il permesso locale del browser va concesso dal browser stesso.

## Conservazione e manutenzione

Non sono stati eliminati originali, dati, analisi o backup. I tentativi di collaudo falliti sono conservati con la causa identificata: un errore del supervisore nell'interpretare l'arresto richiesto, uno schema locale assente copiato nell'environment e un controllo di collaudo incompatibile con i router lazy di FastAPI. Le correzioni sono state verificate prima del riavvio o della nuova esecuzione.

Stato operativo rilevato il 2026-11-01 19:50 UTC:

| Processo | Stato / riferimenti |
| --- | --- |
| Lettore locale | `running / serving`; PID 28700, supervisore 27616; `C:/Users/lange/AppData/Local/BandiSuite/private/bandi-local-reader-20261101-a/state/state.json` |
| Nodo dei comandi remoti | `running / polling`; PID 11524, supervisore 62420; `C:/Users/lange/AppData/Local/BandiSuite/private/release-tfeplskriazdybiampgg/node-state/state.json` |
| Coordinatore periodico | Ultimo giro `succeeded`, nessuna nuova richiesta o ripetizione; `G:/Terzi settore/state/bandi-catalog-controller-v1/state.json` |
| Coda analisi | failed: 3, succeeded: 2; richieste attive 0; revisione delle failure completata |

Log del lettore: `C:/Users/lange/AppData/Local/BandiSuite/private/bandi-local-reader-20261101-a/state/process.log`. Log del nodo: `C:/Users/lange/AppData/Local/BandiSuite/private/release-tfeplskriazdybiampgg/node-state/process.log`. Log del coordinatore: `G:/Terzi settore/state/bandi-catalog-controller-v1/process.log`. Pacchetti e ricevute di pubblicazione: `G:/Terzi settore/state/bandi-catalog-publisher-v1`.

Il lettore deve rispondere sulla sola porta locale 8768 con identità e PID coerenti; il suo supervisore verifica la risposta ogni cinque secondi e conserva l'esito terminale di ogni avvio. Il nodo deve avanzare nei tentativi e mantenere recenti i successi del gateway. Ogni giro periodico deve terminare con stato e ricevuta coerenti: l'uscita senza ricevuta, lo stallo, un'identità di processo dubbia o un errore di scrittura non autorizzano un riavvio automatico del writer. I PID indicati cambiano ai successivi avvii; fanno fede i file di stato aggiornati.

Il file di piano iniziale è `Bandi/integration/BANDI-OPERATIVI-PIANO.md`. Sorgenti e controlli della nuova lista si trovano in `apps/api/bandi_catalog`, `apps/web/src/app/bandi` e `integration/cloudflare-discovery`. Le ricevute operative sono conservate in `AssociaGo/deployment/.artifacts`, senza pubblicare le chiavi private.
