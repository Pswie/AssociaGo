-- Modulo Progetti.
--
-- Lo schema presupponeva già questa tabella senza averla mai avuta:
-- `transactions.project_id` e `transactions.cost_center` esistono dal baseline,
-- ma non c'era nulla a cui puntassero. Finché resta così, la domanda «quanto è
-- costato davvero questo progetto» non ha risposta.
--
-- È anche il punto di contatto con l'osservatorio bandi: un bando intercettato
-- da ets_scout diventa un progetto qui, e da qui appende i propri movimenti.

CREATE TABLE `projects` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `association_id` bigint NOT NULL,
  `name` varchar(255) NOT NULL,
  -- proposto | attivo | da_completare | completato
  `status` varchar(255) NOT NULL DEFAULT 'proposto',
  `area` varchar(255) DEFAULT NULL,
  `referent` varchar(255) DEFAULT NULL,
  -- Da dove nasce: bando, contributo comunale, fondi propri, donazione.
  `funding_source` varchar(255) DEFAULT NULL,
  -- URL dell'avviso nell'osservatorio bandi, quando il progetto nasce da lì.
  `opportunity_url` varchar(1024) DEFAULT NULL,
  `budget` decimal(38,2) DEFAULT NULL,
  `deadline` date DEFAULT NULL,
  `needs` text,
  `notes` text,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_projects_association` (`association_id`),
  KEY `ix_projects_status_deadline` (`status`, `deadline`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Nessuna FOREIGN KEY su transactions.project_id: nel baseline quella colonna
-- ha già valori possibili e non validati, e una constraint retroattiva
-- farebbe fallire la migrazione su un database con dati. L'integrità la tiene
-- il servizio.
CREATE INDEX `ix_transactions_project` ON `transactions` (`project_id`);
