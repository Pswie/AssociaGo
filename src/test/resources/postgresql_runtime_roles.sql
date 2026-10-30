-- Test-only provisioning for the fresh PostgreSQL CI service, never a production database.
-- psql receives owner_password and runtime_password from the CI step.
\set ON_ERROR_STOP on
BEGIN;
CREATE ROLE associago_test_owner LOGIN PASSWORD :'owner_password'
    NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS;
CREATE ROLE associago_test_app LOGIN PASSWORD :'runtime_password'
    NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS;
CREATE SCHEMA associago AUTHORIZATION associago_test_owner;
REVOKE ALL ON SCHEMA associago FROM PUBLIC;
GRANT USAGE ON SCHEMA associago TO associago_test_app;
ALTER DEFAULT PRIVILEGES FOR ROLE associago_test_owner IN SCHEMA associago
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO associago_test_app;
ALTER DEFAULT PRIVILEGES FOR ROLE associago_test_owner IN SCHEMA associago
    GRANT USAGE, SELECT ON SEQUENCES TO associago_test_app;
ALTER DEFAULT PRIVILEGES FOR ROLE associago_test_owner
    REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;
COMMIT;
