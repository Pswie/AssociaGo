"""Render immutable private SQL for MCP; no psql syntax and no database connection."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import uuid

from prepare_initial_bootstrap import ROOT, verified_identity


def literal(value):
    if not isinstance(value, str) or "\x00" in value:
        raise ValueError("SQL literal must be valid text")
    return "'" + value.replace("'", "''") + "'"


def render(identity, parameters, public):
    fields = verified_identity(identity, public)
    if any(parameters.get(key) != value for key, value in fields.items()):
        raise ValueError("Prepared parameters no longer match the verified identity")
    owner_id = str(uuid.UUID(parameters["ADF_LIBREPM_OWNER_ID"]))
    uuid.UUID(parameters["ADF_SUITE_ORGANIZATION_ID"])
    values = {key: literal(value) for key, value in fields.items()}
    # A delimiter cannot be terminated by text embedded in a quoted SQL literal.
    tag = "$adf_bootstrap_" + uuid.uuid4().hex + "$"
    while any(tag in value for value in fields.values()):
        tag = "$adf_bootstrap_" + uuid.uuid4().hex + "$"
    return f"""BEGIN;
SET LOCAL standard_conforming_strings=on;
DO {tag}
DECLARE
    v_subject text := {values['ADF_OWNER_SUBJECT']};
    v_first_name text := {values['ADF_OWNER_FIRST_NAME']};
    v_last_name text := {values['ADF_OWNER_LAST_NAME']};
    v_email text := {values['ADF_OWNER_EMAIL']};
    v_email_verified boolean := {values['ADF_OWNER_EMAIL_VERIFIED']}::boolean;
    v_librepm_id uuid := {literal(owner_id)}::uuid;
    v_association_id bigint;
    v_user_id bigint;
BEGIN
    PERFORM pg_advisory_xact_lock(hashtext('adf_initial_verified_owner'));
    IF v_subject <> {literal(public['clerk']['ownerSubject'])} OR NOT v_email_verified
       OR length(trim(v_first_name)) = 0 OR length(trim(v_last_name)) = 0
       OR position('@' in v_email) < 2 THEN
        RAISE EXCEPTION 'An exact, verified owner identity is required.';
    END IF;
    IF EXISTS (SELECT 1 FROM associago.associations)
       OR EXISTS (SELECT 1 FROM associago.users)
       OR EXISTS (SELECT 1 FROM associago.user_associations)
       OR EXISTS (SELECT 1 FROM librepm.users) THEN
        RAISE EXCEPTION 'Bootstrap requires empty identity tables. Existing identities were not changed.';
    END IF;
    INSERT INTO associago.associations
        (name,slug,email,is_active,use_remote_db,db_ssl,created_at,updated_at)
    VALUES ('Angeli del Futuro APS','angeli-del-futuro-aps','angelidelfuturoaps@outlook.it',
            true,false,false,CURRENT_TIMESTAMP AT TIME ZONE 'UTC',CURRENT_TIMESTAMP AT TIME ZONE 'UTC')
    RETURNING id INTO v_association_id;
    INSERT INTO associago.users
        (first_name,last_name,email,clerk_user_id,created_at,updated_at)
    VALUES (v_first_name,v_last_name,v_email,v_subject,
            CURRENT_TIMESTAMP AT TIME ZONE 'UTC',CURRENT_TIMESTAMP AT TIME ZONE 'UTC')
    RETURNING id INTO v_user_id;
    INSERT INTO associago.user_associations
        (user_id,association_id,role,status,is_board_member,created_at,updated_at)
    VALUES (v_user_id,v_association_id,'ADMIN','ACTIVE',false,
            CURRENT_TIMESTAMP AT TIME ZONE 'UTC',CURRENT_TIMESTAMP AT TIME ZONE 'UTC');
    INSERT INTO librepm.users
        (id,username,display_name,email,clerk_user_id,active,is_ghost,password_hash,created_at,updated_at,sync_status)
    VALUES (v_librepm_id::text,'clerk-' || v_subject,v_first_name || ' ' || v_last_name,v_email,
            v_subject,true,false,'!clerk-only:' || gen_random_uuid()::text || gen_random_uuid()::text,
            to_char(CURRENT_TIMESTAMP AT TIME ZONE 'UTC','YYYY-MM-DD HH24:MI:SS'),
            to_char(CURRENT_TIMESTAMP AT TIME ZONE 'UTC','YYYY-MM-DD HH24:MI:SS'),'LOCAL_ONLY');
END;
{tag};
COMMIT;
SELECT ua.association_id, u.id AS associago_user_id, l.id AS librepm_owner_id
FROM associago.users u JOIN associago.user_associations ua ON ua.user_id=u.id
JOIN librepm.users l ON l.clerk_user_id=u.clerk_user_id
WHERE u.clerk_user_id={values['ADF_OWNER_SUBJECT']};
"""


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--identity", type=Path, required=True)
    parser.add_argument("--parameters", type=Path, required=True)
    parser.add_argument(
        "--public-settings", type=Path, default=ROOT / "suite-public-settings.json"
    )
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    parameters = json.loads(args.parameters.read_text(encoding="utf-8"))
    sql = render(
        json.loads(args.identity.read_text(encoding="utf-8")),
        parameters,
        json.loads(args.public_settings.read_text(encoding="utf-8")),
    )
    # Exclusive create: SQL and UUID parameters must never silently change after review.
    with args.output.open("x", encoding="utf-8", newline="\n") as stream:
        stream.write(sql)
    metadata = {
        "status": "prepared-not-executed",
        "sqlSha256": hashlib.sha256(sql.encode()).hexdigest(),
        "organizationId": parameters["ADF_SUITE_ORGANIZATION_ID"],
        "librepmOwnerId": parameters["ADF_LIBREPM_OWNER_ID"],
    }
    with args.output.with_suffix(".manifest.json").open(
        "x", encoding="utf-8"
    ) as stream:
        stream.write(json.dumps(metadata, indent=2) + "\n")
    print(
        "PASS immutable private SQL prepared; no database connection or profile output"
    )


if __name__ == "__main__":
    main()
