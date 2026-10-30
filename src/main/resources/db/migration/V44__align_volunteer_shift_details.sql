-- Fields already present in the entity and MySQL baseline, missing from SQLite.
-- Add nullable columns so historical shifts remain intact.
ALTER TABLE volunteer_shifts ADD COLUMN description TEXT;
ALTER TABLE volunteer_shifts ADD COLUMN hourly_value DECIMAL(38, 2);
ALTER TABLE volunteer_shifts ADD COLUMN verified_by INTEGER REFERENCES users(id);
ALTER TABLE volunteer_shifts ADD COLUMN verified_at DATETIME;
