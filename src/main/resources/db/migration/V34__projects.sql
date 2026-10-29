-- Modulo Progetti — gemella di db/migration_mysql/V3__projects.sql.
-- Va tenuta allineata: l'entità Project è mappata in ogni profilo.

CREATE TABLE IF NOT EXISTS projects (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    association_id INTEGER NOT NULL,
    name TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'proposto',
    area TEXT,
    referent TEXT,
    funding_source TEXT,
    opportunity_url TEXT,
    budget NUMERIC,
    deadline DATE,
    needs TEXT,
    notes TEXT,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS ix_projects_association ON projects (association_id);
CREATE INDEX IF NOT EXISTS ix_projects_status_deadline ON projects (status, deadline);
CREATE INDEX IF NOT EXISTS ix_transactions_project ON transactions (project_id);
