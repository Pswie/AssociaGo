-- Rimborsi art. 17 CTS — gemella di
-- db/migration_mysql/V4__volunteer_expense_art17.sql.

ALTER TABLE volunteer_expenses ADD COLUMN reimbursement_type TEXT NOT NULL DEFAULT 'ANALITICO';
ALTER TABLE volunteer_expenses ADD COLUMN self_certified INTEGER NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS ix_volunteer_expenses_volunteer_date
    ON volunteer_expenses (volunteer_id, expense_date);
