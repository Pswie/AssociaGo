-- Un solo posto per tutti gli allegati.
--
-- ---------------------------------------------------------------------------
-- Il problema
-- ---------------------------------------------------------------------------
-- C'erano ventiquattro colonne `*_path` sparse in diciassette tabelle, e tutte
-- avevano lo stesso guasto: puntavano al disco locale del contenitore, che su
-- Cloud Run si azzera a ogni riavvio. La colonna continuava a sostenere che il
-- file ci fosse.
--
-- Non era un guasto solo estetico. `Art17Rules` decideva se un rimborso a un
-- volontario fosse ammissibile controllando che `receipt_path` non fosse
-- vuoto: una regola di ammissibilità appoggiata a una stringa che puntava al
-- nulla.
--
-- ---------------------------------------------------------------------------
-- Perché una tabella sola e non ventiquattro colonne BLOB
-- ---------------------------------------------------------------------------
-- Ventiquattro colonne binarie sarebbero ventiquattro percorsi di codice da
-- scrivere, ventiquattro limiti di dimensione da ricordare e ventiquattro
-- posti dove sbagliare. Qui il meccanismo è uno: si carica, si scarica, si
-- cancella, e chiunque abbia bisogno di allegare qualcosa dichiara soltanto a
-- che cosa lo attacca.
--
-- Il prezzo, detto per iscritto: `owner_type` + `owner_id` è un riferimento
-- che il database non può garantire — non esiste una chiave esterna
-- polimorfica. Il vincolo è nel codice (`AttachmentOwner`), e la pulizia delle
-- righe orfane è a carico dell'applicazione. È il compromesso che si accetta
-- per non avere ventiquattro meccanismi; con diciassette tabelle proprietarie
-- l'alternativa era peggiore.
--
-- Il posto definitivo di un allegato resta un archivio oggetti (S3, R2,
-- Scaleway). Ma è un fornitore in più, un DPA in più e una regione in più da
-- verificare: nel frattempo il database sopravvive ai riavvii, finisce nei
-- backup e si cancella insieme a ciò a cui appartiene.

-- ---------------------------------------------------------------------------
-- La tabella
-- ---------------------------------------------------------------------------

CREATE TABLE `attachments` (
  `id` bigint NOT NULL AUTO_INCREMENT,

  -- A che cosa è attaccato. I valori ammessi sono quelli dell'enum
  -- AttachmentOwner: un valore inventato non arriva alla query.
  `owner_type` varchar(48) NOT NULL,
  `owner_id` bigint NOT NULL,

  -- Quale allegato è, quando lo stesso proprietario può averne più d'uno:
  -- un'associazione ha statuto e regolamento, un'attività un'immagine e un
  -- documento. Senza questo servirebbe una colonna per ciascuno, cioè si
  -- tornerebbe da capo.
  `kind` varchar(48) NOT NULL DEFAULT 'documento',

  `file_name` varchar(255) NOT NULL,
  `mime_type` varchar(128) DEFAULT NULL,
  `size_bytes` bigint NOT NULL,
  `sha256` varchar(64) NOT NULL,
  `content` longblob NOT NULL,

  `created_at` datetime(6) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,

  PRIMARY KEY (`id`),
  -- Un proprietario, un tipo, un file. Ricaricare sostituisce: due versioni
  -- dello stesso statuto senza un modo di dire quale valga sono peggio di una.
  UNIQUE KEY `ux_attachments_owner_kind` (`owner_type`, `owner_id`, `kind`),
  KEY `ix_attachments_owner` (`owner_type`, `owner_id`),
  KEY `ix_attachments_sha256` (`sha256`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------
-- Via la tabella provvisoria
-- ---------------------------------------------------------------------------
-- `expense_document_files` faceva la cosa giusta per un solo tipo di documento.
-- Ora il meccanismo è generale e quella tabella sarebbe un secondo modo di
-- fare la stessa cosa — cioè la premessa perché fra sei mesi metà degli
-- allegati stia di qua e metà di là.

DROP TABLE IF EXISTS `expense_document_files`;

-- ---------------------------------------------------------------------------
-- Via le ventiquattro colonne percorso
-- ---------------------------------------------------------------------------

ALTER TABLE `activities`            DROP COLUMN `image_path`, DROP COLUMN `document_path`;
ALTER TABLE `activity_costs`        DROP COLUMN `receipt_path`;
ALTER TABLE `assemblies`            DROP COLUMN `minutes_path`;
ALTER TABLE `assembly_documents`    DROP COLUMN `file_path`;
ALTER TABLE `associations`          DROP COLUMN `statute_path`,
                                    DROP COLUMN `regulation_path`,
                                    DROP COLUMN `secondary_logo_path`,
                                    DROP COLUMN `partner_logo_path`;
ALTER TABLE `association_documents` DROP COLUMN `file_path`;
ALTER TABLE `annual_balances`       DROP COLUMN `pdf_path`;
ALTER TABLE `certificate_templates` DROP COLUMN `header_image_path`,
                                    DROP COLUMN `footer_image_path`,
                                    DROP COLUMN `background_image_path`;
ALTER TABLE `issued_certificates`   DROP COLUMN `pdf_path`;
ALTER TABLE `expense_documents`     DROP COLUMN `file_path`;
ALTER TABLE `fiscal_comparisons`    DROP COLUMN `pdf_path`, DROP COLUMN `csv_path`;
ALTER TABLE `invoice_checks`        DROP COLUMN `pdf_path`;
ALTER TABLE `invoices`              DROP COLUMN `file_path`;
ALTER TABLE `transactions`          DROP COLUMN `receipt_path`;
ALTER TABLE `inventory_items`       DROP COLUMN `photo_path`;
ALTER TABLE `resources`             DROP COLUMN `image_path`;
ALTER TABLE `volunteer_expenses`    DROP COLUMN `receipt_path`;

-- `associations.logo` resta com'è: è già un BLOB nella riga, funziona, e
-- l'interfaccia lo legge da lì. Lo stesso vale per
-- `institutional_signatures.signature_image`. Erano le due sole cose fatte
-- bene, e non si toccano per simmetria.
