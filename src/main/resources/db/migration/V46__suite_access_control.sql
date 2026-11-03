-- Desktop keeps its existing authentication. These tables are inert unless suite access is explicitly enabled.
CREATE TABLE suite_access_users (
    subject varchar(128) PRIMARY KEY,
    display_name varchar(200) NOT NULL,
    first_name varchar(100), last_name varchar(100), email varchar(254),
    status varchar(16) NOT NULL CHECK (status IN ('pending', 'active', 'suspended')),
    applications_json text NOT NULL, version bigint NOT NULL DEFAULT 0,
    created_at timestamp NOT NULL, updated_at timestamp NOT NULL
);
CREATE INDEX ix_suite_access_users_created ON suite_access_users (created_at, subject);
CREATE TABLE suite_access_audit (
    id varchar(36) PRIMARY KEY, actor_subject varchar(128) NOT NULL,
    target_subject varchar(128) NOT NULL, before_json text NOT NULL,
    after_json text NOT NULL, created_at timestamp NOT NULL
);
CREATE INDEX ix_suite_access_audit_target ON suite_access_audit (target_subject, created_at);
