# Verifica dei claim di ruolo Clerk

30 ottobre 2026 — **claim di ruolo applicato e verificato con una nuova lettura**
nell'istanza Clerk di sviluppo condivisa. Nessun token personale richiesto o
generato; nessuna chiave o scheda utente letta durante questa operazione.

È stata eseguita una sola fusione minima autorizzata di
`session.claims.role = "{{user.public_metadata.role}}"`, dopo il dry-run validato.
La lettura finale conferma la configurazione persistente `v1_89bd4c45`.
[clerk-role-applied.json](clerk-role-applied.json) riporta payload, stato prima e
dopo ed esito sanitizzato. Il collaudo del normale accesso e dello SSO resta
distinto dalla verifica di configurazione.

Il ruolo tecnico `presidente` esiste già nei metadata pubblici amministrati
dal server. Non è però sufficiente che il browser lo legga: le API Java
autorizzano le operazioni in base ai claim del JWT verificato.

Il primo controllo MCP in sola lettura dell'istanza esistente aveva prodotto:

- Schema `session.claims`: descritto come “Claims added to session tokens”.
- Configurazione richiesta con chiave `session`: valore **null**, versione
  `v1_bda339da`.
- Nessun tool di lettura dedicato ai template JWT disponibile; la chiave
  `jwt_templates` non è riconosciuta da questo schema.

**Risultato iniziale: non verificabile con quelle letture MCP.** `session: null` non
dimostra da solo che i claim siano assenti. Non è stata confermata alcuna
mappatura del ruolo inclusa nel token di sessione predefinito.
Evidenza sanitizzata: `deployment/.artifacts/clerk-claims-result.json`.

I frontend chiamano `getToken()` senza selezionare un template nominato. Per
questo un eventuale template separato, anche corretto, non dimostra che il
ruolo sia presente nei token ricevuti ora dalle API. Prima del collaudo online
va verificata la configurazione effettiva in Dashboard o tramite un normale
accesso applicativo autorizzato, senza copiare token o chiavi nei rapporti.

Entrambi i decoder accettano `role`, `roles`, `org_role`, i corrispondenti campi
in `public_metadata`, oppure il ruolo firmato dell’organizzazione attiva
`o.rol`. Quest’ultimo deve prevalere sui metadata precedenti, anche se nullo,
sconosciuto o privo di un ruolo ammesso. La compatibilità legacy rimane quando
`o.rol` non è presente. LibrePM è stato allineato a tale precedenza per evitare
di mantenere privilegi revocati nell’organizzazione.

La correzione applicata usa il ruolo tecnico già assegnato sul server, senza
inventare ruoli dalla UI né trasferire interi oggetti di metadata nel JWT.

## Proposta concreta verificata sullo schema MCP

Una seconda lettura dello schema ufficiale espone `session.claims` come oggetto
“Claims added to session tokens”. Il tool `patch_instance_config` accetta
`application_id`, `instance`, `config`, `destructive` e `dry_run`; documenta la
fusione profonda della configurazione e l'anteprima senza applicazione con
`dry_run: true`. Non espone JSON Patch né un parametro `config_version`.
La versione restituita dalla lettura resta `v1_bda339da`, con `session: null`:
l'esistenza dei claim attuali rimane quindi non verificata da tale lettura.

La proposta minima, lasciando tutti i campi non menzionati alla fusione
documentata del tool, è:

```json
{
  "session": {
    "claims": {
      "role": "{{user.public_metadata.role}}"
    }
  }
}
```

Il percorso di configurazione è `/session/claims/role`; il risultato atteso nel
JWT predefinito è `$.role`, ricavato per ciascun utente dal campo amministrato
sul server. Non viene fissato il valore `presidente`, copiato l'intero oggetto
metadata o modificato `o.rol`. Il formato dell'espressione segue l'esempio
ufficiale di un singolo campo `user.public_metadata` nella
[guida al rinnovo del token](https://clerk.com/docs/guides/sessions/force-token-refresh).

Il **dry-run MCP è stato validato dal server**, senza applicazione. Risposta
sanitizzata dell'anteprima:

```json
{
  "config_version": "v1_89bd4c45",
  "dry_run": true,
  "before": { "session": null },
  "after": {
    "session": {
      "allowed_clock_skew": 5,
      "claims": { "role": "{{user.public_metadata.role}}" },
      "lifetime": 60
    }
  }
}
```

La versione `v1_89bd4c45` identifica il risultato dell'anteprima, non prova una
modifica persistente. `allowed_clock_skew` e `lifetime` sono restituiti dal
server; il payload inviato aggiunge soltanto `claims.role`.
[clerk-role-preview.json](clerk-role-preview.json) contiene la chiamata proposta
e la risposta; l'evidenza limitata ai claim è in
`deployment/.artifacts/clerk-claims-preview-result.json`.

Il primo tentativo era stato fermato dal contesto `read-only` scelto dall'agente
per l'ispezione. Il tentativo riuscito usa il profilo operativo già autorizzato
`danger-full-access`, mantenendo `approval_policy: never`, lo stesso tool MCP
ufficiale e `dry_run: true`. Non sono state cambiate policy, chiavi o API.
La successiva applicazione live autorizzata è descritta sotto.

## Applicazione e verifica finale

Prima della scrittura è stata ripetuta la lettura limitata a `session`:
`v1_bda339da`, `session: null`, uguale allo stato iniziale. Una sola chiamata
`patch_instance_config`, con il payload minimo già mostrato,
`destructive: false` e `dry_run: false`, ha restituito:

```json
{
  "config_version": "v1_89bd4c45",
  "dry_run": false,
  "before": { "session": null },
  "after": {
    "session": {
      "allowed_clock_skew": 5,
      "claims": { "role": "{{user.public_metadata.role}}" },
      "lifetime": 60
    }
  }
}
```

Una nuova chiamata `get_instance_config` limitata a `session` ha confermato
la stessa versione e la stessa espressione salvata. Nessun altro claim è stato
richiesto nel payload; la fusione documentata conserva i campi non menzionati.
L'evidenza completa sanitizzata è in
`deployment/.artifacts/clerk-claims-apply-result.json`.

La configurazione riguarda i nuovi token di sessione dell'istanza di sviluppo
condivisa. I JWT già emessi conservano
il vecchio payload fino al rinnovo: Clerk documenta un rinnovo ogni 60 secondi.
Un normale client autenticato può richiedere `getToken({ skipCache: true })` per
un nuovo token; non è stato eseguito su alcun utente in questo controllo.
La presenza del claim firmato va poi verificata tramite le normali API dei tre
siti dopo l'accesso, senza stampare il JWT. Questa mappatura del ruolo non
dimostra da sola il funzionamento SSO tra domini.

Riferimenti ufficiali: [personalizzazione del token di sessione](https://clerk.com/docs/guides/sessions/customize-session-tokens),
[metadata e claim](https://clerk.com/docs/guides/users/extending),
[differenza dai template JWT nominati](https://clerk.com/docs/guides/sessions/jwt-templates).
