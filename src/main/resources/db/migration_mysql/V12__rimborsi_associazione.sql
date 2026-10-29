-- A quale associazione appartiene un rimborso.
--
-- ---------------------------------------------------------------------------
-- Il segnaposto che era rimasto
-- ---------------------------------------------------------------------------
-- `VolunteerService`, quando un rimborso passa a PAID, crea il movimento
-- contabile e ci scriveva dentro:
--
--     transaction.setAssociationId(1L); // Placeholder
--
-- Con una sola associazione funzionava per coincidenza. Da quando V10 ha messo
-- la chiave esterna su `transactions.association_id`, un identificativo
-- sbagliato non produce più una riga orfana: fa fallire l'inserimento. Il
-- segnaposto è passato da «impreciso» a «rotto appena cambia qualcosa».
--
-- ---------------------------------------------------------------------------
-- Perché non si poteva ricavare
-- ---------------------------------------------------------------------------
-- Non è pigrizia di chi l'ha scritto: il percorso non esiste. `volunteers`
-- punta a `members`, e `members` non ha né `association_id` né un legame con
-- `users` — l'appartenenza all'ente sta in `user_associations`, che parte da
-- `users`, un'entità diversa dal socio. Da un volontario non si arriva alla sua
-- associazione con nessuna JOIN.
--
-- Il dato mancava, quindi si aggiunge invece di dedurlo.
--
-- ---------------------------------------------------------------------------
-- Nullable, e il perché
-- ---------------------------------------------------------------------------
-- Le righe già inserite non hanno modo di sapere a chi appartengono, e
-- riempirle con un valore inventato sarebbe peggio di lasciarle vuote. La
-- colonna resta quindi facoltativa, e il controllo si sposta dove conta: il
-- servizio rifiuta di creare il movimento se manca, con un messaggio che lo
-- dice. Meglio un rifiuto spiegato che un movimento attribuito a caso.

ALTER TABLE `volunteer_expenses`
  ADD COLUMN `association_id` bigint DEFAULT NULL AFTER `volunteer_id`,
  ADD KEY `ix_volunteer_expenses_association` (`association_id`),
  ADD CONSTRAINT `fk_volunteer_expenses_association`
    FOREIGN KEY (`association_id`) REFERENCES `associations` (`id`) ON DELETE RESTRICT;
