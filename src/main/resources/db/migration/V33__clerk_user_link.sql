-- Collegamento fra l'identità Clerk e l'anagrafica interna.
--
-- Gemella di db/migration_mysql/V2__clerk_user_link.sql. Va tenuta allineata:
-- l'entità User mappa la colonna in ogni profilo, quindi se manca qui il
-- desktop fallisce su qualunque query che tocchi `users`.

ALTER TABLE users ADD COLUMN clerk_user_id TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS ux_users_clerk_user_id ON users (clerk_user_id);
