# Aggiornare Bandi dopo il trasferimento Cloudflare

Stato del 1 novembre 2026: gateway Google `adf-bandi-portale-api-cf-20261101-b`,
immagine finale `sha256:2efd9f2ab6181f9004dff36586bdb21c5e81b06e6d2efad9a6c0c43461b6a6bf`,
bundle runtime Bandi **versione 2**. Sorgenti e segreti precedenti sono
conservati; le immagini Docker obsolete sono state eliminate sulla
successiva richiesta esplicita dell'utente.
Il frontend corrente è `dpl_A7fNLX4ygPifSKS4izrbYFryeQZR`.

## Configurazione corrente

Il file privato `release-config.private.json` è stato aggiornato alla versione 2,
con copia integrale della configurazione precedente. Il bundle contiene gli
stessi accessi applicativi precedenti e due nuove chiavi di sola lettura,
distinte da quelle del nodo PC, della manutenzione e dei collegamenti tra app.
Le chiavi restano in Secret Manager e nei percorsi privati, mai nel frontend.

`deploy_runtime_candidates.py` è l'**inizializzatore storico di versione 1**:
non riutilizzarlo sulla configurazione corrente. Il suo rifiuto della versione
2 è intenzionale. Anche `prepare_cloudflare_gateway.py` prepara soltanto il
passaggio iniziale 1→2, non una nuova installazione dei tre servizi.

Per aggiornare solo il codice del gateway, costruire il contesto esplicito con
`build_runtime.py`, verificare i test, controllare lo spazio del registro e
usare un nuovo digest immutabile. Un aggiornamento di immagine deve ereditare
la configurazione corrente e il mount della versione 2, senza creare altri
segreti. Prima versione senza traffico, poi collaudo, infine promozione.
Non copiare automaticamente comandi dai tentativi falliti negli artefatti.

## Aggiornamento dell'archivio completo

1. Esportare dal PC con `cloud-catalog-sync.private.json` e
   `publishEnabled=false`. Il vecchio publisher PostgreSQL resta disabilitato.
2. Preparare una nuova cartella privata con il
   [pacchettizzatore](../../Bandi/integration/cloudflare-archive/README.md).
   Controllare dimensioni, hash e decompressione prima del caricamento.
3. Verificare con Wrangler l'account esistente. Pubblicare il Worker
   `angeli-del-futuro-archivio` conservando la chiave server e il passaggio
   obbligatorio dal Worker su tutti i percorsi.
4. Rileggere manifest e tutti i blocchi dal servizio. Verificare anonimo 401,
   chiave errata 401, hash completi e letture applicative prima di dichiarare
   riuscito l'aggiornamento. Un upload riuscito da solo non basta.

Non riattivare `--publish-export` del vecchio percorso PostgreSQL. Originali,
esportazioni e copia Supabase sono conservati. La nuova istruzione dell'utente
consente la pulizia dei Docker Google obsoleti: dopo il collaudo controllare
i riferimenti globali e rimuovere solo immagini inutilizzate, proteggendo
indice OCI, manifest di piattaforma e attestazioni delle build attive.
Per questo motivo lo spazio della vecchia copia Supabase non è stato liberato.

## Novità quotidiane

Il Worker `angeli-del-futuro-bandi-quotidiani` usa un D1 dedicato, senza PDF o
AI. Il job cron-job.org **8766630** richiama il controllo ogni 5 minuti, con almeno 15 minuti tra elaborazioni;
la stessa fonte non viene ricontrollata prima di 24 ore. Il job **8758073**
resta dedicato alla manutenzione. Nessun cron GitHub.

La copertura iniziale è di quattro indici ufficiali, descritti in
[RICERCA-QUOTIDIANA.md](../../Bandi/integration/RICERCA-QUOTIDIANA.md).
I riferimenti rimangono distinti da bandi ancora aperti o verificati. Errori
e ultimo successo sono visibili nella pagina Novità; la data di rilevamento
non viene usata come data di pubblicazione.

I picchi CPU osservati di 14 e 15 ms hanno avuto esito positivo grazie
all'elasticità documentata per picchi infrequenti. Il valore nominale Free
resta 10 ms: non è un margine garantito. Sono quattro scansioni al giorno,
con invocazioni intermedie senza scansione. Il primo giro reale delle
quattro fonti è riuscito: 87 riferimenti, picchi CPU 14/15/8/14 ms. Monitorare errori 1102 e quote;
non passare automaticamente a un piano a pagamento.

## Verifiche del rilascio

- Archivio: 13 blocchi, hash completi e byte ricostruiti verificati.
- Google: 35 letture reali riuscite dall'immagine finale, senza usare il PC.
- API: 115 regressioni mirate, inclusa la lettura ASGI del catalogo remoto.
- Frontend: 208 regressioni, TypeScript, lint, build Node 22 e 13 risposte
  pubbliche identiche alla candidata.
- CI: aggiunti test offline di archivio, HTMLRewriter e D1 nativi; il workflow
  non aggiunge pianificazioni. I controlli locali non sono un run GitHub.

Il collaudo con la sessione reale dell'utente resta distinto: Opera richiede
una nuova autenticazione. Non sono stati creati token per impersonarlo.
