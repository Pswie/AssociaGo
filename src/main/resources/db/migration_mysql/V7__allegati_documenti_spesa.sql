-- Il file di una fattura, dentro il database.
--
-- ---------------------------------------------------------------------------
-- Perché nel database e non su disco
-- ---------------------------------------------------------------------------
-- `expense_documents.file_path` prometteva una cosa che non succedeva: puntava
-- a un percorso sul disco locale del contenitore, e su Cloud Run quel disco è
-- effimero. Al riavvio dell'istanza il PDF non c'era più, mentre la colonna
-- continuava a dire il contrario — lo stesso guasto che avevano i certificati
-- medici e che ha `transactions.receipt_path`.
--
-- Il posto giusto per un allegato è un archivio oggetti (S3, R2, Scaleway), ma
-- è un fornitore in più, un DPA in più e una regione in più da verificare. Nel
-- frattempo il database è l'unico posto che sopravvive a un riavvio, viene
-- salvato dai backup e si cancella insieme alla riga a cui appartiene.
--
-- ---------------------------------------------------------------------------
-- Perché in una tabella sua
-- ---------------------------------------------------------------------------
-- Se il BLOB stesse in `expense_documents`, ogni elenco delle fatture si
-- trascinerebbe dietro i megabyte anche mostrando solo numero, fornitore e
-- importo — e gli elenchi si aprono cento volte più spesso di quanto si scarichi
-- un allegato.
--
-- In una tabella separata l'elenco resta leggero, e il giorno che si passa a un
-- archivio oggetti si sostituisce questa tabella invece di smontare l'entità.

CREATE TABLE `expense_document_files` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `expense_document_id` bigint NOT NULL,

  `file_name` varchar(255) NOT NULL,
  `mime_type` varchar(128) DEFAULT NULL,
  `size_bytes` bigint NOT NULL,

  -- Impronta del contenuto: dice se due fatture caricate sono lo stesso file,
  -- e permette di accorgersi che un allegato è cambiato. `source_hash` su
  -- expense_documents fa la stessa cosa per l'XML della fattura elettronica;
  -- questo è per il file vero e proprio.
  `sha256` varchar(64) NOT NULL,

  -- LONGBLOB e non BLOB: BLOB si ferma a 64 KB, cioè meno di una pagina
  -- scansionata. Il limite vero lo mette l'applicazione, non il tipo.
  `content` longblob NOT NULL,

  `created_at` datetime(6) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,

  PRIMARY KEY (`id`),
  -- Un documento, un allegato. Caricarne un altro sostituisce il primo: due
  -- versioni della stessa fattura senza un modo di dire quale vale sono
  -- peggio di una sola.
  UNIQUE KEY `ux_expense_document_files_document` (`expense_document_id`),
  KEY `ix_expense_document_files_sha256` (`sha256`),
  CONSTRAINT `fk_expense_document_files_document`
    FOREIGN KEY (`expense_document_id`) REFERENCES `expense_documents` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
