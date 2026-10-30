"""Generate three private bundles and a separate LOCAL-only owner migration config.

The exact session-pooler hostname must come from the project's connection panel.
No hostname guessing, provider calls, database connections or credential output.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
from urllib.parse import quote

from backup_cloud_schemas import protect_directory


def generate(roles, keys, host):
    reference = roles["projectRef"]
    if not re.fullmatch(r"[a-z0-9]{20}", reference) or not re.fullmatch(
        r"[a-z0-9-]+\.pooler\.supabase\.com", host
    ):
        raise ValueError("Use the exact selected project's session-pooler hostname")
    passwords = roles["roles"]
    jdbc = (
        "jdbc:postgresql://"
        + host
        + ":5432/postgres?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory&currentSchema="
    )

    def pg_url(role):
        return (
            "postgresql+psycopg://"
            + role
            + "."
            + reference
            + ":"
            + quote(passwords[role], safe="")
            + "@"
            + host
            + ":5432/postgres?sslmode=verify-full&sslrootcert=system"
        )

    owners = {"projectRef": reference}
    bundles = {}
    for app in ("associago", "librepm"):
        bundles[app] = {
            "SPRING_DATASOURCE_URL": jdbc + app,
            "SPRING_DATASOURCE_USERNAME": app + "_app." + reference,
            "SPRING_DATASOURCE_PASSWORD": passwords[app + "_app"],
            "SUITE_ASSOCIAGO_KEY": keys["associagoPeerKey"],
        }
        owners[app] = {
            "jdbcUrl": jdbc + app,
            "ownerUser": app + "_owner." + reference,
            "ownerPassword": passwords[app + "_owner"],
        }
    bundles["librepm"]["SUITE_BANDI_KEY"] = keys["bandiPeerKey"]
    bundles["bandiPortal"] = {
        "COLLAB_DATABASE_URL": pg_url("bandi_portal_app"),
        "COLLAB_NODE_KEY": keys["nodeKey"],
        "COLLAB_CRON_KEY": keys["cronKey"],
        "SUITE_LIBREPM_KEY": keys["bandiPeerKey"],
    }
    owners["bandi_portal"] = {
        "ownerUrl": pg_url("bandi_portal_owner"),
        "ownerUser": "bandi_portal_owner." + reference,
    }
    return bundles, owners


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--roles", type=Path, required=True)
    parser.add_argument("--integration-keys", type=Path, required=True)
    parser.add_argument("--session-pooler-host", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        raise SystemExit("Refusing to replace an existing prepared release")
    bundles, owners = generate(
        json.loads(args.roles.read_text()),
        json.loads(args.integration_keys.read_text()),
        args.session_pooler_host,
    )
    protect_directory(args.output)
    for app, values in bundles.items():
        (args.output / (app + "-runtime.private.json")).write_text(
            json.dumps(values, indent=2) + "\n", encoding="utf-8"
        )
    (args.output / "owner-migrations.private.json").write_text(
        json.dumps(owners, indent=2) + "\n", encoding="utf-8"
    )
    print(
        "PASS three private runtime bundles plus separate local owner config; no remote calls"
    )


if __name__ == "__main__":
    main()
