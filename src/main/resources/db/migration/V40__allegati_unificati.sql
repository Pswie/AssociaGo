-- Gemella SQLite di V9__allegati_unificati.sql (serie MySQL).
--
-- Un solo posto per tutti gli allegati, al posto delle ventiquattro colonne
-- `*_path` sparse in diciassette tabelle — tutte puntavano a un disco che su
-- Cloud Run si azzera, e `Art17Rules` ci appoggiava sopra una regola di
-- ammissibilità dei rimborsi. Il ragionamento completo sta nella gemella.
--
-- Dialetto: BLOB invece di longblob, INTEGER PRIMARY KEY AUTOINCREMENT, e un
-- DROP COLUMN per istruzione — SQLite non ne accetta più insieme.

CREATE TABLE IF NOT EXISTS attachments (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,

    -- A che cosa è attaccato. I valori ammessi sono quelli dell'enum
    -- AttachmentOwner: il vincolo è nel codice, perché una chiave esterna
    -- polimorfica non esiste. È il prezzo dichiarato di avere un meccanismo
    -- solo invece di diciassette.
    owner_type  VARCHAR(48) NOT NULL,
    owner_id    INTEGER NOT NULL,

    -- Quale allegato è: un'associazione ha statuto e regolamento, un'attività
    -- un'immagine e un documento.
    kind        VARCHAR(48) NOT NULL DEFAULT 'documento',

    file_name   VARCHAR(255) NOT NULL,
    mime_type   VARCHAR(128),
    size_bytes  INTEGER NOT NULL,
    sha256      VARCHAR(64) NOT NULL,
    content     BLOB NOT NULL,

    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by  INTEGER,

    -- Un proprietario, un tipo, un file: ricaricare sostituisce.
    UNIQUE (owner_type, owner_id, kind)
);

CREATE INDEX IF NOT EXISTS idx_attachments_owner ON attachments(owner_type, owner_id);
CREATE INDEX IF NOT EXISTS idx_attachments_sha256 ON attachments(sha256);

-- Via la tabella provvisoria: ora il meccanismo è generale, e tenerne due
-- sarebbe la premessa perché fra sei mesi metà degli allegati stia di qua e
-- metà di là.
DROP TABLE IF EXISTS expense_document_files;

-- ---------------------------------------------------------------------------
-- Via le ventiquattro colonne percorso
-- ---------------------------------------------------------------------------

ALTER TABLE activities DROP COLUMN image_path;
ALTER TABLE activities DROP COLUMN document_path;
ALTER TABLE activity_costs DROP COLUMN receipt_path;
ALTER TABLE assemblies DROP COLUMN minutes_path;
ALTER TABLE assembly_documents DROP COLUMN file_path;
ALTER TABLE associations DROP COLUMN statute_path;
ALTER TABLE associations DROP COLUMN regulation_path;
ALTER TABLE associations DROP COLUMN secondary_logo_path;
ALTER TABLE associations DROP COLUMN partner_logo_path;
ALTER TABLE association_documents DROP COLUMN file_path;
ALTER TABLE annual_balances DROP COLUMN pdf_path;
ALTER TABLE certificate_templates DROP COLUMN header_image_path;
ALTER TABLE certificate_templates DROP COLUMN footer_image_path;
ALTER TABLE certificate_templates DROP COLUMN background_image_path;
ALTER TABLE issued_certificates DROP COLUMN pdf_path;
ALTER TABLE expense_documents DROP COLUMN file_path;
ALTER TABLE fiscal_comparisons DROP COLUMN pdf_path;
ALTER TABLE fiscal_comparisons DROP COLUMN csv_path;
ALTER TABLE invoice_checks DROP COLUMN pdf_path;
-- Su `invoices` le due serie divergono: SQLite la chiama `pdf_path`, MySQL
-- `file_path`. Non è un refuso di questa migrazione — è il segno che il
-- baseline MySQL è stato scritto a mano invece che derivato, e che le due
-- serie vanno riallineate. Qui si toglie la colonna che esiste davvero.
ALTER TABLE invoices DROP COLUMN pdf_path;

-- `logo_path` esiste solo di qua: V1 della serie SQLite lo aveva, e quando il
-- logo è diventato un BLOB nella riga (`logo`) la vecchia colonna è rimasta.
-- Se ne va con le altre.
ALTER TABLE associations DROP COLUMN logo_path;
ALTER TABLE transactions DROP COLUMN receipt_path;
ALTER TABLE inventory_items DROP COLUMN photo_path;
ALTER TABLE resources DROP COLUMN image_path;
ALTER TABLE volunteer_expenses DROP COLUMN receipt_path;

-- `associations.logo` e `institutional_signatures.signature_image` restano:
-- erano già BLOB nella riga, funzionano, e l'interfaccia li legge da lì.
