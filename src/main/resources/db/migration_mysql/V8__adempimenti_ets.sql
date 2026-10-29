-- Tre adempimenti del Codice del Terzo Settore che nel modello non c'erano.
--
-- Non è una svista di chi ha scritto il programma: il gestionale è nato per
-- un'associazione sportiva dilettantistica — l'intestazione di
-- V31__medical_certificates.sql lo dice testualmente — e un'ASD ha altri
-- obblighi. Un'APS iscritta al RUNTS ha questi.

-- ---------------------------------------------------------------------------
-- 1. L'assicurazione dei volontari — art. 18 D.Lgs. 117/2017
-- ---------------------------------------------------------------------------
-- È obbligatoria e non derogabile: l'ente deve assicurare i volontari contro
-- gli infortuni e le malattie connessi all'attività, e per la responsabilità
-- civile verso terzi. Senza copertura un volontario non può operare — e la
-- responsabilità di averlo fatto operare scoperto è dell'ente.
--
-- Nell'area riservata del sito il compito «Verificare copertura assicurativa
-- volontari» esiste già fra quelli del referente volontari. Mancava il posto
-- dove segnare la polizza che quel compito dovrebbe verificare.

CREATE TABLE `volunteer_insurance_policies` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `association_id` bigint NOT NULL,

  `policy_number` varchar(64) NOT NULL,
  `company` varchar(255) NOT NULL,

  -- infortuni | rc_terzi | infortuni_rc
  --
  -- Distinti perché l'art. 18 ne chiede due cose diverse, e capita che siano
  -- due polizze separate con due scadenze diverse: una sola delle due non
  -- basta, e un modello che non sa dirlo lascia credere di essere in regola.
  `coverage_type` varchar(32) NOT NULL DEFAULT 'infortuni_rc',

  `start_date` date NOT NULL,
  `end_date` date NOT NULL,
  `notes` text,

  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,

  PRIMARY KEY (`id`),
  KEY `ix_insurance_policies_association` (`association_id`, `end_date`),
  CONSTRAINT `fk_insurance_policies_association`
    FOREIGN KEY (`association_id`) REFERENCES `associations` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Le polizze nominative: l'elenco delle persone coperte.
--
-- Una polizza **senza** righe qui è cumulativa e copre tutti i volontari
-- dell'ente. Una **con** righe copre solo quelli elencati. È la distinzione
-- che si usa davvero, e tenerla esplicita evita l'errore peggiore: credere
-- che una polizza nominativa copra anche chi è arrivato dopo averla firmata.
CREATE TABLE `volunteer_policy_coverage` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `policy_id` bigint NOT NULL,
  `volunteer_id` bigint NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,

  PRIMARY KEY (`id`),
  UNIQUE KEY `ux_policy_coverage` (`policy_id`, `volunteer_id`),
  KEY `ix_policy_coverage_volunteer` (`volunteer_id`),
  CONSTRAINT `fk_policy_coverage_policy`
    FOREIGN KEY (`policy_id`) REFERENCES `volunteer_insurance_policies` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_policy_coverage_volunteer`
    FOREIGN KEY (`volunteer_id`) REFERENCES `volunteers` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------
-- 2. Il registro dei volontari diventa un registro — art. 17 c.1
-- ---------------------------------------------------------------------------
-- `volunteers` aveva `status` ACTIVE/INACTIVE, che dice com'è adesso e non da
-- quando. Il registro serve proprio a rispondere alla domanda che quel campo
-- non regge: «chi era volontario il 14 marzo». Senza le date non lo si può
-- ricostruire, e in caso di infortunio è la prima cosa che viene chiesta.

ALTER TABLE `volunteers`
  ADD COLUMN `started_at` date DEFAULT NULL AFTER `member_id`,
  ADD COLUMN `ended_at`   date DEFAULT NULL AFTER `started_at`,
  ADD KEY `ix_volunteers_periodo` (`started_at`, `ended_at`);

-- Chi è già dentro risulta volontario dal giorno in cui è stato inserito: è
-- l'unica data disponibile, ed è meglio di nessuna. Le righe già cessate
-- restano senza data di fine finché qualcuno non la scrive — un registro con
-- una data inventata sarebbe peggio di uno incompleto.
UPDATE `volunteers` SET `started_at` = DATE(`created_at`)
 WHERE `started_at` IS NULL AND `created_at` IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 3. Il RUNTS
-- ---------------------------------------------------------------------------
-- `associations` aveva `federation_registration_number` e `federation_provider`
-- — il numero di affiliazione a una federazione sportiva — e non aveva il
-- numero di iscrizione al RUNTS, che è il registro in cui un'APS deve stare
-- per essere un'APS. I campi della federazione se ne vanno con la potatura
-- del modulo `federation` (migrazione successiva).

ALTER TABLE `associations`
  ADD COLUMN `runts_number` varchar(64) DEFAULT NULL,
  -- La sezione a cui si è iscritti: APS, ODV, ETS, impresa sociale… Non è un
  -- dettaglio anagrafico, decide quali adempimenti si applicano.
  ADD COLUMN `runts_section` varchar(64) DEFAULT NULL,
  ADD COLUMN `runts_registered_at` date DEFAULT NULL;
