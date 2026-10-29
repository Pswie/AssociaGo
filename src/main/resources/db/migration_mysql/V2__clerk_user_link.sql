-- Collegamento fra l'identità Clerk e l'anagrafica interna.
--
-- Sul web l'autenticazione la fa Clerk, che conosce l'utente con un proprio
-- identificativo (il claim `sub` del JWT, del tipo user_2ab...). Il gestionale
-- conosce le persone con la riga in `users`. Senza un ponte esplicito fra i due
-- non è possibile rispondere alla domanda su cui si regge tutta l'area
-- personale di soci e volontari: «quali sono i *miei* dati?».
--
-- L'alternativa sarebbe l'abbinamento per email, che resta come ripiego nel
-- codice ma non basta da sola: l'email in Clerk si può cambiare, e due persone
-- possono condividerne una (capita fra coniugi e in famiglia).

ALTER TABLE `users`
    ADD COLUMN `clerk_user_id` varchar(255) DEFAULT NULL;

-- Univoco ma nullable: le anagrafiche inserite dal desktop non hanno, e non
-- devono avere, un corrispettivo Clerk. L'indice impedisce che due persone
-- diverse finiscano legate alla stessa identità.
CREATE UNIQUE INDEX `ux_users_clerk_user_id` ON `users` (`clerk_user_id`);
