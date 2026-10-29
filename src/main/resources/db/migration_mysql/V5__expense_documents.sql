-- Documenti di spesa: le fatture che l'associazione RICEVE.
--
-- La tabella `invoices` del baseline non serve allo scopo: è per le fatture
-- ATTIVE, quelle emesse dall'associazione verso un socio o un partecipante
-- (numero UNIQUE, `user_association_id` del destinatario). Le fatture passive
-- hanno un fornitore, un numero che è suo e non nostro, e possono benissimo
-- ripetersi fra fornitori diversi.
--
-- Alimenta la parte "uscite" del rendiconto e conserva il legame con il
-- movimento contabile quando la fattura viene pagata.

CREATE TABLE `expense_documents` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `association_id` bigint NOT NULL,
  -- fattura | ricevuta | scontrino | nota
  `document_type` varchar(32) NOT NULL DEFAULT 'fattura',
  -- Numero assegnato dal FORNITORE: non è univoco nel nostro archivio.
  `number` varchar(255) DEFAULT NULL,
  `supplier_name` varchar(255) NOT NULL,
  `supplier_vat` varchar(32) DEFAULT NULL,
  `supplier_tax_code` varchar(32) DEFAULT NULL,
  `document_date` date NOT NULL,
  `due_date` date DEFAULT NULL,
  `total_amount` decimal(38,2) NOT NULL,
  `vat_amount` decimal(38,2) DEFAULT NULL,
  -- da_pagare | pagata | registrata
  `status` varchar(32) NOT NULL DEFAULT 'da_pagare',
  `project_id` bigint DEFAULT NULL,
  `transaction_id` bigint DEFAULT NULL,
  `file_path` varchar(1024) DEFAULT NULL,
  -- Impronta del contenuto del file XML: impedisce di importare due volte
  -- la stessa fattura elettronica, che è l'errore più facile da commettere.
  `source_hash` varchar(64) DEFAULT NULL,
  `notes` text,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `ux_expense_documents_source_hash` (`source_hash`),
  KEY `ix_expense_documents_association_date` (`association_id`, `document_date`),
  KEY `ix_expense_documents_supplier` (`supplier_name`),
  KEY `ix_expense_documents_project` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
