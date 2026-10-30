CREATE TABLE suite_project_links (
    suite_project_id VARCHAR(36) NOT NULL PRIMARY KEY,
    organization_id VARCHAR(36) NOT NULL,
    project_id BIGINT NOT NULL UNIQUE,
    source_version BIGINT NOT NULL DEFAULT 0,
    source_fingerprint VARCHAR(64),
    source_description TEXT,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_suite_project FOREIGN KEY (project_id) REFERENCES projects(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE project_person_assignments (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    person_type VARCHAR(16) NOT NULL,
    person_id BIGINT NOT NULL,
    CONSTRAINT uq_project_person UNIQUE (project_id, person_type, person_id),
    CONSTRAINT fk_project_person_project FOREIGN KEY (project_id) REFERENCES projects(id),
    CONSTRAINT ck_project_person_type CHECK (person_type IN ('MEMBER', 'VOLUNTEER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
ALTER TABLE volunteer_shifts ADD COLUMN project_id BIGINT NULL;
CREATE INDEX ix_volunteer_shifts_project ON volunteer_shifts(project_id);
ALTER TABLE volunteer_shifts ADD CONSTRAINT fk_volunteer_shift_project FOREIGN KEY (project_id) REFERENCES projects(id);
