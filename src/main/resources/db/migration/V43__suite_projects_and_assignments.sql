CREATE TABLE suite_project_links (
    suite_project_id VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    project_id INTEGER NOT NULL UNIQUE REFERENCES projects(id),
    source_version BIGINT NOT NULL DEFAULT 0,
    source_fingerprint VARCHAR(64),
    source_description TEXT,
    row_version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE project_person_assignments (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id INTEGER NOT NULL REFERENCES projects(id),
    person_type VARCHAR(16) NOT NULL CHECK (person_type IN ('MEMBER', 'VOLUNTEER')),
    person_id INTEGER NOT NULL,
    UNIQUE (project_id, person_type, person_id)
);
ALTER TABLE volunteer_shifts ADD COLUMN project_id INTEGER REFERENCES projects(id);
CREATE INDEX ix_volunteer_shifts_project ON volunteer_shifts(project_id);
