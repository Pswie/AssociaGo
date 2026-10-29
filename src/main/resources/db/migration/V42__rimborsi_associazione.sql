-- Gemella SQLite di V12__rimborsi_associazione.sql (serie MySQL).
--
-- `VolunteerService` scriveva `setAssociationId(1L) // Placeholder` nel
-- movimento contabile del rimborso. Non era pigrizia: da un volontario non si
-- arriva alla sua associazione con nessuna JOIN — `volunteers` punta a
-- `members`, e `members` non ha né `association_id` né un legame con `users`,
-- dove l'appartenenza all'ente è registrata. Il dato mancava, quindi si
-- aggiunge invece di dedurlo.
--
-- Facoltativa: le righe già inserite non sanno a chi appartengono, e riempirle
-- con un valore inventato sarebbe peggio che lasciarle vuote. Il controllo sta
-- nel servizio, che rifiuta di creare il movimento se manca.
--
-- Dialetto: SQLite non sa aggiungere una chiave esterna a una tabella
-- esistente — si dichiara solo alla creazione. Qui resta la colonna con il suo
-- indice; il vincolo vero vive nella gemella MySQL, e su entrambi i lati il
-- controllo applicativo è lo stesso.

ALTER TABLE volunteer_expenses ADD COLUMN association_id INTEGER;

CREATE INDEX IF NOT EXISTS idx_volunteer_expenses_association
    ON volunteer_expenses(association_id);
