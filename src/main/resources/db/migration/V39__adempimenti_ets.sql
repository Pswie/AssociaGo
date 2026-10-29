-- Gemella SQLite di V8__adempimenti_ets.sql (serie MySQL).
--
-- Assicurazione dei volontari (art. 18 CTS), date nel registro dei volontari
-- (art. 17 c.1) e iscrizione al RUNTS. Il ragionamento completo sta nella
-- gemella: qui cambia solo il dialetto.

-- ---------------------------------------------------------------------------
-- 1. L'assicurazione dei volontari — art. 18 D.Lgs. 117/2017
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS volunteer_insurance_policies (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    association_id  INTEGER NOT NULL,

    policy_number   VARCHAR(64) NOT NULL,
    company         VARCHAR(255) NOT NULL,

    -- infortuni | rc_terzi | infortuni_rc — l'art. 18 ne chiede due cose
    -- diverse, e capita che siano due polizze con due scadenze diverse.
    coverage_type   VARCHAR(32) NOT NULL DEFAULT 'infortuni_rc',

    start_date      DATE NOT NULL,
    end_date        DATE NOT NULL,
    notes           TEXT,

    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (association_id) REFERENCES associations(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_insurance_policies_association
    ON volunteer_insurance_policies(association_id, end_date);

-- Polizza senza righe qui = cumulativa, copre tutti. Con righe = nominativa,
-- copre solo gli elencati. Tenerlo esplicito evita di credere che una polizza
-- nominativa copra anche chi è arrivato dopo averla firmata.
CREATE TABLE IF NOT EXISTS volunteer_policy_coverage (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    policy_id    INTEGER NOT NULL,
    volunteer_id INTEGER NOT NULL,
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    UNIQUE (policy_id, volunteer_id),

    FOREIGN KEY (policy_id) REFERENCES volunteer_insurance_policies(id) ON DELETE CASCADE,
    FOREIGN KEY (volunteer_id) REFERENCES volunteers(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_policy_coverage_volunteer
    ON volunteer_policy_coverage(volunteer_id);

-- ---------------------------------------------------------------------------
-- 2. Il registro dei volontari diventa un registro — art. 17 c.1
-- ---------------------------------------------------------------------------
-- `status` diceva com'è adesso, non da quando. Il registro serve a rispondere
-- a «chi era volontario il 14 marzo», ed è la prima domanda in caso di
-- infortunio.

ALTER TABLE volunteers ADD COLUMN started_at DATE;
ALTER TABLE volunteers ADD COLUMN ended_at DATE;

CREATE INDEX IF NOT EXISTS idx_volunteers_periodo ON volunteers(started_at, ended_at);

-- Chi c'è già risulta volontario dal giorno dell'inserimento: è l'unica data
-- disponibile ed è meglio di nessuna. Le cessazioni restano da scrivere a mano
-- — una data inventata sarebbe peggio di un registro incompleto.
UPDATE volunteers SET started_at = DATE(created_at)
 WHERE started_at IS NULL AND created_at IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 3. Il RUNTS
-- ---------------------------------------------------------------------------

ALTER TABLE associations ADD COLUMN runts_number VARCHAR(64);
-- La sezione (APS, ODV, ETS, impresa sociale) decide quali adempimenti si
-- applicano: non è un dettaglio anagrafico.
ALTER TABLE associations ADD COLUMN runts_section VARCHAR(64);
ALTER TABLE associations ADD COLUMN runts_registered_at DATE;
