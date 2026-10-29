-- Documenti di spesa — gemella di
-- db/migration_mysql/V5__expense_documents.sql.

CREATE TABLE IF NOT EXISTS expense_documents (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    association_id INTEGER NOT NULL,
    document_type TEXT NOT NULL DEFAULT 'fattura',
    number TEXT,
    supplier_name TEXT NOT NULL,
    supplier_vat TEXT,
    supplier_tax_code TEXT,
    document_date DATE NOT NULL,
    due_date DATE,
    total_amount NUMERIC NOT NULL,
    vat_amount NUMERIC,
    status TEXT NOT NULL DEFAULT 'da_pagare',
    project_id INTEGER,
    transaction_id INTEGER,
    file_path TEXT,
    source_hash TEXT,
    notes TEXT,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_expense_documents_source_hash
    ON expense_documents (source_hash);
CREATE INDEX IF NOT EXISTS ix_expense_documents_association_date
    ON expense_documents (association_id, document_date);
CREATE INDEX IF NOT EXISTS ix_expense_documents_project
    ON expense_documents (project_id);
