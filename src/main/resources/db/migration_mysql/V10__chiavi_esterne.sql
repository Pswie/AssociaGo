-- Le chiavi esterne che il baseline MySQL aveva perso.
--
-- ---------------------------------------------------------------------------
-- Che cos'era successo
-- ---------------------------------------------------------------------------
-- Lo schema SQLite (la serie `db/migration`) dichiara le sue relazioni:
-- `FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE` e così
-- via. Il baseline MySQL è stato scritto consolidando quello schema in un file
-- solo, e nel travaso i vincoli sono rimasti indietro: `association_id`
-- presente in decine di tabelle con **una** sola chiave verso `associations`.
--
-- Cioè la colonna che separa i dati di un ente da quelli di un altro non era
-- garantita da nulla. Con una sola associazione il rischio di mescolare due
-- enti è teorico; le righe orfane — un certificato che sopravvive al socio
-- cancellato, un movimento che punta a un'attività che non c'è più — sono un
-- problema da subito.
--
-- ---------------------------------------------------------------------------
-- Perché RESTRICT e non CASCADE
-- ---------------------------------------------------------------------------
-- Su `association_id` la cancellazione è vietata, non propagata. Con CASCADE un
-- `DELETE FROM associations` svuoterebbe in silenzio l'intero database — soci,
-- movimenti, bilanci, verbali — in un colpo solo e senza un errore. Con
-- RESTRICT quel comando fallisce, ed è quel che deve fare: un ente non si
-- cancella per sbaglio.
--
-- Sui figli veri (le coperture di una polizza, le aggiunte di un movimento)
-- resta CASCADE, dichiarato dove quelle tabelle sono state create.
--
-- ---------------------------------------------------------------------------
-- Come è stato costruito questo elenco
-- ---------------------------------------------------------------------------
-- Interrogando `information_schema` del database vero, non leggendo il file
-- del baseline. La prima stesura era dedotta dal testo SQL e comprendeva
-- quattro tabelle che `association_id` non ce l'hanno affatto —
-- activity_instructors, assembly_participants, assembly_votes,
-- inventory_loans — e sarebbe fallita a metà, lasciando Flyway in stato di
-- migrazione fallita.

-- ---------------------------------------------------------------------------
-- association_id: 32 tabelle
-- ---------------------------------------------------------------------------

ALTER TABLE `accounts`
  ADD CONSTRAINT `fk_accounts_association`
    FOREIGN KEY (`association_id`) REFERENCES `associations` (`id`) ON DELETE RESTRICT;

ALTER TABLE `activities`
  ADD CONSTRAINT `fk_activities_association`
    FOREIGN KEY (`association_id`) REFERENCES `associations` (`id`) ON DELETE RESTRICT;

ALTER TABLE `annual_balances`
  ADD CONSTRAINT `fk_annual_balances_association`
    FOREIGN KEY (`association_id`) REFERENCES `associations` (`id`) ON DELETE RESTRICT;

ALTER TABLE `assemblies`
  ADD CONSTRAINT `fk_assemblies_association`
    FOREIGN KEY (`association_id`) REFERENCES `associations` (`id`) ON DELETE RESTRICT;

ALTER TABLE `association_deadlines`
  ADD CONSTRAINT `fk_association_deadlines_association`
    FOREIGN KEY (`association_id`) REFERENCES `associations` (`id`) ON DELETE RESTRICT;

ALTER TABLE `association_documents`
  ADD CONSTRAINT `fk_association_documents_association`
    FOREIGN KEY (`association_id`) REFERENCES `associations` (`id`) ON DELETE RESTRICT;

ALTER TABLE `association_locations`
  ADD CONSTRAINT `fk_association_locations_association`
    FOREIGN KEY (`association_id`) REFERENCES `associations` (`id`) ON DELETE RESTRICT;

ALTER TABLE `audit_log`
  ADD CONSTRAINT `fk_audit_log_association`
    FOREIGN KEY (`association_id`) REFERENCES `associations` (`id`) ON DELETE RESTRICT;

ALTER TABLE `budgets`
  ADD CONSTRAINT `fk_budgets_association`
    FOREIGN KEY (`association_id`) REFERENCES `associations` (`id`) ON DELETE RESTRICT;

ALTER TABLE `cash_registers`
  ADD CONSTRAINT `fk_cash_registers_association`
    FOREIGN KEY (`association_id`) REFERENCES `associations` (`id`) ON DELETE RESTRICT;

ALTER TABLE `certificate_templates`
  ADD CONSTRAINT `fk_certificate_templates_association`
    FOREIGN KEY (`association_id`) REFERENCES `associations` (`id`) ON DELETE RESTRICT;

ALTER TABLE `communication_templates`
  ADD CONSTRAINT `fk_communication_templates_association`
    FOREIGN KEY (`association_id`) REFERENCES `associations` (`id`) ON DELETE RESTRICT;

ALTER TABLE `communications`
  ADD CONSTRAINT `fk_communications_association`
    FOREIGN KEY (`association_id`) REFERENCES `associations` (`id`) ON DELETE RESTRICT;

ALTER TABLE `coupons`
  ADD CONSTRAINT `fk_coupons_association`
    FOREIGN KEY (`association_id`) REFERENCES `associations` (`id`) ON DELETE RESTRICT;

ALTER TABLE `events`
  ADD CONSTRAINT `fk_events_association`
    FOREIGN KEY (`association_id`) REFERENCES `associations` (`id`) ON DELETE RESTRICT;

ALTER TABLE `expense_documents`
  ADD CONSTRAINT `fk_expense_documents_association`
    FOREIGN KEY (`association_id`) REFERENCES `associations` (`id`) ON DELETE RESTRICT;

ALTER TABLE `fiscal_comparisons`
  ADD CONSTRAINT `fk_fiscal_comparisons_association`
    FOREIGN KEY (`association_id`) REFERENCES `associations` (`id`) ON DELETE RESTRICT;

ALTER TABLE `fiscal_year_closures`
  ADD CONSTRAINT `fk_fiscal_year_closures_association`
    FOREIGN KEY (`association_id`) REFERENCES `associations` (`id`) ON DELETE RESTRICT;

ALTER TABLE `import_logs`
  ADD CONSTRAINT `fk_import_logs_association`
    FOREIGN KEY (`association_id`) REFERENCES `associations` (`id`) ON DELETE RESTRICT;

ALTER TABLE `institutional_signatures`
  ADD CONSTRAINT `fk_institutional_signatures_association`
    FOREIGN KEY (`association_id`) REFERENCES `associations` (`id`) ON DELETE RESTRICT;

ALTER TABLE `inventory_items`
  ADD CONSTRAINT `fk_inventory_items_association`
    FOREIGN KEY (`association_id`) REFERENCES `associations` (`id`) ON DELETE RESTRICT;

ALTER TABLE `invoice_checks`
  ADD CONSTRAINT `fk_invoice_checks_association`
    FOREIGN KEY (`association_id`) REFERENCES `associations` (`id`) ON DELETE RESTRICT;

ALTER TABLE `invoices`
  ADD CONSTRAINT `fk_invoices_association`
    FOREIGN KEY (`association_id`) REFERENCES `associations` (`id`) ON DELETE RESTRICT;

ALTER TABLE `issued_certificates`
  ADD CONSTRAINT `fk_issued_certificates_association`
    FOREIGN KEY (`association_id`) REFERENCES `associations` (`id`) ON DELETE RESTRICT;

ALTER TABLE `journal_entries`
  ADD CONSTRAINT `fk_journal_entries_association`
    FOREIGN KEY (`association_id`) REFERENCES `associations` (`id`) ON DELETE RESTRICT;

ALTER TABLE `medical_certificates`
  ADD CONSTRAINT `fk_medical_certificates_association`
    FOREIGN KEY (`association_id`) REFERENCES `associations` (`id`) ON DELETE RESTRICT;

ALTER TABLE `member_consents`
  ADD CONSTRAINT `fk_member_consents_association`
    FOREIGN KEY (`association_id`) REFERENCES `associations` (`id`) ON DELETE RESTRICT;

ALTER TABLE `payment_methods`
  ADD CONSTRAINT `fk_payment_methods_association`
    FOREIGN KEY (`association_id`) REFERENCES `associations` (`id`) ON DELETE RESTRICT;

ALTER TABLE `projects`
  ADD CONSTRAINT `fk_projects_association`
    FOREIGN KEY (`association_id`) REFERENCES `associations` (`id`) ON DELETE RESTRICT;

ALTER TABLE `resource_bookings`
  ADD CONSTRAINT `fk_resource_bookings_association`
    FOREIGN KEY (`association_id`) REFERENCES `associations` (`id`) ON DELETE RESTRICT;

ALTER TABLE `resources`
  ADD CONSTRAINT `fk_resources_association`
    FOREIGN KEY (`association_id`) REFERENCES `associations` (`id`) ON DELETE RESTRICT;

ALTER TABLE `transactions`
  ADD CONSTRAINT `fk_transactions_association`
    FOREIGN KEY (`association_id`) REFERENCES `associations` (`id`) ON DELETE RESTRICT;

-- ---------------------------------------------------------------------------
-- I legami fra persone e ciò che le riguarda
-- ---------------------------------------------------------------------------
-- Qui CASCADE è corretto: sono dati che esistono solo in quanto riferiti a
-- quella persona e che alla sua cancellazione non devono sopravvivere. È anche
-- il modo in cui una richiesta di cancellazione ex art. 17 GDPR si esegue
-- davvero, invece di lasciare tracce sparse.

ALTER TABLE `volunteers`
  ADD CONSTRAINT `fk_volunteers_member`
    FOREIGN KEY (`member_id`) REFERENCES `members` (`id`) ON DELETE CASCADE;

ALTER TABLE `medical_certificates`
  ADD CONSTRAINT `fk_medical_certificates_member`
    FOREIGN KEY (`member_id`) REFERENCES `members` (`id`) ON DELETE CASCADE;

ALTER TABLE `member_consents`
  ADD CONSTRAINT `fk_member_consents_member`
    FOREIGN KEY (`member_id`) REFERENCES `members` (`id`) ON DELETE CASCADE;

-- Il buono punta al socio che l'ha ricevuto e al movimento che l'ha pagato.
-- Su `transaction_id` la cancellazione è vietata: un buono il cui movimento
-- sparisse resterebbe denaro uscito senza traccia contabile.
ALTER TABLE `coupons`
  ADD CONSTRAINT `fk_coupons_beneficiary_member`
    FOREIGN KEY (`beneficiary_member_id`) REFERENCES `members` (`id`) ON DELETE SET NULL,
  ADD CONSTRAINT `fk_coupons_transaction`
    FOREIGN KEY (`transaction_id`) REFERENCES `transactions` (`id`) ON DELETE RESTRICT;
