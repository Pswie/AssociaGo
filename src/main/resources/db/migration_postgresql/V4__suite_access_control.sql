-- Private server-side authorization store. Clerk remains the identity provider.
CREATE TABLE associago.suite_access_users (
    subject varchar(128) PRIMARY KEY,
    display_name varchar(200) NOT NULL,
    first_name varchar(100),
    last_name varchar(100),
    email varchar(254),
    status varchar(16) NOT NULL CHECK (status IN ('pending', 'active', 'suspended')),
    applications_json text NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamp NOT NULL,
    updated_at timestamp NOT NULL
);
CREATE INDEX ix_suite_access_users_created ON associago.suite_access_users (created_at, subject);
CREATE TABLE associago.suite_access_audit (
    id varchar(36) PRIMARY KEY,
    actor_subject varchar(128) NOT NULL,
    target_subject varchar(128) NOT NULL,
    before_json text NOT NULL,
    after_json text NOT NULL,
    created_at timestamp NOT NULL
);
CREATE INDEX ix_suite_access_audit_target ON associago.suite_access_audit (target_subject, created_at);
REVOKE ALL ON associago.suite_access_users, associago.suite_access_audit FROM PUBLIC, anon, authenticated;
REVOKE DELETE, TRUNCATE ON associago.suite_access_users FROM associago_app;
REVOKE UPDATE, DELETE, TRUNCATE ON associago.suite_access_audit FROM associago_app;
GRANT SELECT, INSERT, UPDATE ON associago.suite_access_users TO associago_app;
GRANT SELECT, INSERT ON associago.suite_access_audit TO associago_app;
ALTER TABLE associago.suite_access_users ENABLE ROW LEVEL SECURITY;
ALTER TABLE associago.suite_access_audit ENABLE ROW LEVEL SECURITY;
-- This is the dedicated private backend role, not Supabase's public authenticated role.
CREATE POLICY suite_access_users_select ON associago.suite_access_users FOR SELECT TO associago_app USING (true);
CREATE POLICY suite_access_users_insert ON associago.suite_access_users FOR INSERT TO associago_app WITH CHECK (true);
CREATE POLICY suite_access_users_update ON associago.suite_access_users FOR UPDATE TO associago_app USING (true) WITH CHECK (true);
CREATE POLICY suite_access_audit_select ON associago.suite_access_audit FOR SELECT TO associago_app USING (true);
CREATE POLICY suite_access_audit_insert ON associago.suite_access_audit FOR INSERT TO associago_app WITH CHECK (true);
