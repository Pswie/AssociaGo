-- Gemella SQLite di V6__buoni_e_aggiunte_uscite.sql (serie MySQL).
--
-- Le due serie devono restare allineate nel contenuto: il desktop gira su
-- SQLite, il cloud su MySQL, e una tabella che esiste da una parte sola è
-- un'applicazione che funziona in un posto e si rompe nell'altro.
--
-- La numerazione invece è diversa di proposito, e non è un errore: SQLite è
-- arrivata qui in 36 passi, MySQL con un baseline solo. Contano i nomi delle
-- tabelle, non i numeri.
--
-- Differenze di dialetto rispetto alla gemella:
--   * niente ENGINE/CHARSET/COLLATE — non esistono in SQLite;
--   * INTEGER PRIMARY KEY AUTOINCREMENT al posto di bigint AUTO_INCREMENT;
--   * NUMERIC al posto di decimal(38,2) — SQLite tipizza i valori, non le
--     colonne, e NUMERIC è la classe di affinità giusta per gli importi;
--   * un ALTER TABLE per istruzione: SQLite non accetta più modifiche insieme.

-- ---------------------------------------------------------------------------
-- 1. Certificati medici: restano tipo e scadenza
-- ---------------------------------------------------------------------------
-- Il perché sta nella gemella MySQL e in MedicalCertificate.java: erano dati
-- sulla salute (art. 9 GDPR) accanto a nome, codice fiscale e indirizzo, e il
-- file non esisteva davvero — puntava a un disco che al riavvio si azzera.
--
-- DROP COLUMN richiede SQLite 3.35+; il driver in uso è 3.46.

ALTER TABLE medical_certificates DROP COLUMN file_path;
ALTER TABLE medical_certificates DROP COLUMN notes;
ALTER TABLE medical_certificates DROP COLUMN issued_by;
ALTER TABLE medical_certificates DROP COLUMN medical_facility;

-- ---------------------------------------------------------------------------
-- 2. Il coupon è un buono, non uno sconto
-- ---------------------------------------------------------------------------
-- Un buono da 10 euro consegnato a qualcuno è denaro che esce: va nelle uscite.
-- Uno sconto sulla quota è denaro che non entra, ed è un'altra cosa — le sue
-- colonne restano solo perché la schermata desktop le scrive ancora.

ALTER TABLE coupons ADD COLUMN nominal_value NUMERIC;

-- A chi è andato. Due colonne perché i destinatari sono di due specie:
-- un socio sta in `members`; una persona assistita NON sta qui e non deve
-- starci — in beneficiary_ref va il suo codice di anagrafica (ASS-0042), mai
-- il suo nome. Scriverlo qui farebbe del gestionale un secondo posto dove i
-- nomi delle persone fragili stanno in chiaro.
ALTER TABLE coupons ADD COLUMN beneficiary_member_id INTEGER;
ALTER TABLE coupons ADD COLUMN beneficiary_ref VARCHAR(64);

ALTER TABLE coupons ADD COLUMN issued_date DATE;
ALTER TABLE coupons ADD COLUMN transaction_id INTEGER;
ALTER TABLE coupons ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'emesso';

CREATE INDEX IF NOT EXISTS idx_coupons_beneficiary_member ON coupons(beneficiary_member_id);
CREATE INDEX IF NOT EXISTS idx_coupons_beneficiary_ref ON coupons(beneficiary_ref);
CREATE INDEX IF NOT EXISTS idx_coupons_transaction ON coupons(transaction_id);

-- ---------------------------------------------------------------------------
-- 3. Le aggiunte su un'uscita
-- ---------------------------------------------------------------------------
-- Un movimento si registra quando il denaro si muove, ma la cifra definitiva
-- arriva dopo: con la fattura, con la commissione, con l'IVA. Riscrivere
-- l'importo registrato fa sparire la storia — a fine anno non si riconcilia
-- più con quello che si è dichiarato. Qui l'aggiunta è una riga sua:
--
--   amount = base_amount + somma delle aggiunte
--
-- e vale sempre, anche sulle righe di prima: il backfill le allinea.

ALTER TABLE transactions ADD COLUMN base_amount NUMERIC;

UPDATE transactions SET base_amount = amount WHERE base_amount IS NULL;

CREATE TABLE IF NOT EXISTS transaction_additions (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    transaction_id  INTEGER NOT NULL,

    -- iva | commissione | spese | altro. Aperto e non chiuso: sullo stesso
    -- buono la differenza può essere la commissione di chi lo emette, l'IVA o
    -- le spese di consegna, e raggrupparle per tipo è ciò che permette di dire
    -- «quest'anno avete pagato 200 euro di commissioni».
    kind            VARCHAR(32) NOT NULL DEFAULT 'altro',

    -- Con segno: una stima troppo alta si corregge aggiungendo un valore
    -- negativo, non riscrivendo l'originale.
    amount          NUMERIC NOT NULL,

    description     VARCHAR(255),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by      INTEGER,

    FOREIGN KEY (transaction_id) REFERENCES transactions(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_transaction_additions_transaction
    ON transaction_additions(transaction_id);
CREATE INDEX IF NOT EXISTS idx_transaction_additions_kind
    ON transaction_additions(kind);
