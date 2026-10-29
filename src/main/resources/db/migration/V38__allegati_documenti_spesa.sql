-- Gemella SQLite di V7__allegati_documenti_spesa.sql (serie MySQL).
--
-- Il file di una fattura dentro il database, invece che su un percorso.
-- `expense_documents.file_path` prometteva una conservazione che non avveniva:
-- puntava al disco locale del contenitore, che su Cloud Run al riavvio si
-- azzera. Sul desktop il disco c'è davvero, ma un archivio che funziona in un
-- posto e non nell'altro non è un archivio.
--
-- In una tabella sua e non dentro expense_documents: se il BLOB stesse nella
-- riga, ogni elenco delle fatture si trascinerebbe i megabyte anche mostrando
-- solo numero e importo, e gli elenchi si aprono molto più spesso di quanto si
-- scarichi un allegato.
--
-- Dialetto: BLOB invece di longblob (in SQLite non c'è distinzione di
-- dimensione), INTEGER PRIMARY KEY AUTOINCREMENT, niente ENGINE/CHARSET.

CREATE TABLE IF NOT EXISTS expense_document_files (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    expense_document_id INTEGER NOT NULL,

    file_name           VARCHAR(255) NOT NULL,
    mime_type           VARCHAR(128),
    size_bytes          INTEGER NOT NULL,

    -- Impronta del contenuto: dice se due allegati sono lo stesso file e
    -- permette di accorgersi che uno è cambiato. `source_hash` su
    -- expense_documents fa lo stesso per l'XML della fattura elettronica.
    sha256              VARCHAR(64) NOT NULL,

    content             BLOB NOT NULL,

    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by          INTEGER,

    -- Un documento, un allegato: ricaricare sostituisce. Due versioni della
    -- stessa fattura senza un modo di dire quale valga sono peggio di una sola.
    UNIQUE (expense_document_id),

    FOREIGN KEY (expense_document_id) REFERENCES expense_documents(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_expense_document_files_sha256
    ON expense_document_files(sha256);
