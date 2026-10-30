# Presenze: completamento e confini verificati

## Interventi

- [x] Registrare presenze delle iscrizioni reali (`UserAssociation`) mediante
  `membershipId`, separato dall'anagrafica storica `memberId`.
- [x] Conservare i record storici, anche con identificativi numerici sovrapposti
  o duplicati legacy, senza collegamenti dedotti da nomi, email o codici fiscali.
- [x] Fornire candidati per attività, limitati alla sua associazione e ai soli
  campi necessari alla selezione.
- [x] Controllare lato server identità autenticata, ruolo e iscrizione ACTIVE
  dell'operatore nell'associazione effettiva della risorsa.
- [x] Rifiutare spostamenti di sessioni, presenze e risorse figlie verso altri
  genitori; impedire duplicati mediante transazione, lock della sessione e indice.
- [x] Eliminare esplicitamente le presenze quando si elimina la sessione.
- [x] Proteggere anche il confine Activity: CRUD, elenco, candidati e risorse
  figlie verificano l'associazione originale; i POST non possono sovrascrivere ID.
- [x] Redigere gli importi e i pagamenti nelle viste operative; costi e riepilogo
  economico richiedono i ruoli finanziari. Le copie di risposta non modificano
  le entity persistenti. Gli aggiornamenti operativi conservano compensi/costi
  omessi perché non visibili al ruolo.
- [x] Impedire che Membership POST crei l'iscrizione in un'associazione esterna
  necessaria per autorizzare lo stesso chiamante. Controllare prima ogni CRUD
  sul genitore originale e risolvere i riferimenti annidati esclusivamente per ID.
- [x] Usare DTO Membership senza password dell'associazione, credenziali database
  o dati di accesso utente. I rinnovi e gli altri processi interni non cambiano.
- [x] Impedire User POST cloud con ID o collegamento Clerk precompilati e User PUT
  che tenti di cambiare identità o collegamento di accesso.
- [x] Creare un backup SQLite consistente prima della ricostruzione V45 e
  interrompere l'aggiornamento se il backup non riesce.
- [x] Suite completa e JAR finale: 270 test, zero fallimenti/errori/skip;
  `test bootJar` riuscito con PostgreSQL17/MySQL8.4 locali e ruoli PG separati.
- [ ] Applicare le migrazioni e distribuire backend/frontend coordinati: fase
  remota riservata al responsabile del rilascio, dopo il backup autorizzato.

## Contratti aggiunti

`GET /api/v1/attendance/activity/{activityId}/participants` restituisce:

```json
{
  "participantType": "MEMBERSHIP",
  "membershipId": 301,
  "memberId": null,
  "displayName": "Nome Cognome",
  "membershipCardNumber": "2026-0001",
  "status": "ACTIVE"
}
```

Il tipo alternativo è `LEGACY_MEMBER`, con solo `memberId`. Ogni nuovo record
richiede esattamente uno dei due riferimenti. L'aggiornamento può omettere
entrambi per conservare il partecipante originale e modificare solo lo stato;
in quel caso le note/ora omesse restano invariate. Un riferimento esplicito deve
coincidere con quello già registrato. Per correggere il partecipante si elimina
il record con `DELETE /api/v1/attendance/record/{id}` e se ne crea uno corretto.

`GET /api/v1/activities/{activityId}/participant-candidates` restituisce
`userId`, `membershipId`, `firstName`, `lastName`, `membershipCardNumber`, `status`.
L'iscrizione all'attività usa **userId**; la presenza associativa usa
**membershipId**. Per l'attività i candidati sono distinti per User ID esplicito,
senza confronti tra stringhe anagrafiche.

Gli endpoint esistenti restano disponibili. Il selettore `X-Association-Id`
non attribuisce autorizzazioni e deve coincidere con l'associazione effettiva.
Il cloud richiede la stessa verifica sui riferimenti inseriti nel corpo.

## Migrazioni e conservazione

- PostgreSQL: `V3__attendance_membership_reference.sql`.
- MySQL: `V14__attendance_membership_reference.sql`.
- SQLite: `V45__attendance_membership_reference.sql`.

Nessuna migrazione storica viene modificata. In PostgreSQL/MySQL si aggiungono
colonna nullable, FK a `user_associations` e indice univoco
`(session_id, membership_id)`. I precedenti NULL e duplicati Member restano validi.

SQLite V9 imponeva `member_id NOT NULL`: V45 ricostruisce solo `attendance_records`
e copia integralmente ID, riferimenti, stato, ora e note. Conserva anche il limite
AUTOINCREMENT precedente, inclusi gli ID già cancellati. Il backup iniziale
conserva la versione precedente; una prova reale verifica righe, note, indice,
FK, duplicati storici e progressivo 99 → 100.

I riferimenti legacy non hanno associazione propria. Il cloud consente nuovi
riferimenti legacy soltanto quando esiste una sola associazione e coincide con
quella dell'attività. Le righe già esistenti restano consultabili attraverso la
sessione autorizzata. Il desktop conserva il comportamento locale.

## Prove e limiti

Il gate intermedio ha superato 59 test senza skip su PostgreSQL17, MySQL8.4,
SQLite e H2: schema/JPA, roundtrip membership, conservazione legacy, privacy,
ruoli, scope e concorrenza PostgreSQL con un salvataggio e un conflitto.
La successiva prova HTTP H2 ha verificato il confine Membership con due
associazioni e il blocco degli aggiornamenti dell'identità User.
Il gate finale completo ha superato 270 test, incluse tutte le verifiche nuove,
senza skip. Il JAR congelato ha SHA256
`2f32a52968a1d3c6e7c3925801562f657463307c585b93490d02a0e23f09a846`;
il receipt locale sanitizzato è `deployment/.artifacts/attendance-backend-final-receipt.json`.
Sono fixture locali sintetiche, non sessioni o dati di persone reali.

Questa modifica **non certifica l'isolamento multiassociazione dell'intera API**.
Il CRUD anagrafico User generale e il modello globale Member legacy restano
confini da trattare separatamente. Il loro uso su un'installazione cloud con
più associazioni richiede una revisione dedicata; non si deve dedurre tale
garanzia dai controlli Activity/Attendance/Membership qui implementati.
