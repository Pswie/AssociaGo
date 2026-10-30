-- MySQL's tax_code UNIQUE constraint used a case-insensitive collation.
-- Preserve that identity invariant without changing opaque Clerk/suite IDs.
CREATE UNIQUE INDEX users_tax_code_case_insensitive
    ON "associago"."users" (lower("tax_code"));

-- Shared contact emails remain allowed, as in the existing domain model.
-- Spring Data IgnoreCase emits upper(email); keep that lookup indexed without
-- turning an email into a new identity key.
CREATE INDEX users_email_case_insensitive
    ON "associago"."users" (upper("email"));
