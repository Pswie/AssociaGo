-- Rimborsi ai volontari secondo l'art. 17 del Codice del Terzo Settore.
--
-- Lo schema originale tratta il rimborso come una spesa qualsiasi: importo,
-- data, giustificativo. Ma il CTS distingue due regimi con regole diverse:
--
--   * ANALITICO (art. 17 c. 3) — spesa effettivamente sostenuta e documentata.
--     Nessun tetto, ma serve il giustificativo.
--
--   * FORFETTARIO (art. 17 c. 4) — rimborso su autocertificazione, ammesso
--     solo entro 10 euro al giorno e 150 euro al mese, e solo se l'organo
--     sociale ha deliberato tipologie di spesa e attività ammesse.
--
-- Senza questa distinzione in banca dati, un controllo non può essere superato
-- e chi inserisce non ha modo di sapere se sta sforando.

ALTER TABLE `volunteer_expenses`
    ADD COLUMN `reimbursement_type` varchar(32) NOT NULL DEFAULT 'ANALITICO';

ALTER TABLE `volunteer_expenses`
    ADD COLUMN `self_certified` bit(1) NOT NULL DEFAULT b'0';

CREATE INDEX `ix_volunteer_expenses_volunteer_date`
    ON `volunteer_expenses` (`volunteer_id`, `expense_date`);
