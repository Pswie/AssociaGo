"""Prepare private one-time provisioning for the selected empty Supabase project.

This writes local credentials and reviewed SQL only; it makes no network call.
The SQL refuses existing application roles/schemas or populated public tables.
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import secrets

from backup_cloud_schemas import outside_repository, protect_directory

SCHEMAS = ("associago", "librepm", "bandi_portal")


def prepare(reference: str, base: Path) -> Path:
    if re.fullmatch(r"[a-z0-9]{20}", reference) is None:
        raise ValueError("A real Supabase project reference is required")
    root = outside_repository(base) / ("release-" + reference)
    root.mkdir(parents=True, exist_ok=False)
    protect_directory(root)
    roles = {schema + suffix: secrets.token_urlsafe(48)
             for schema in SCHEMAS for suffix in ("_owner", "_app")}
    assert len(set(roles.values())) == 6
    source = (Path(__file__).resolve().parents[1] / "scripts/supabase/provision_portal.psql").read_text(encoding="utf-8")
    # psql's client-side password check is performed above; all database checks
    # and transactional statements remain the already tested provisioning SQL.
    sql = "BEGIN;\n" + source[source.index("DO $checks$"):]
    for role, password in roles.items():
        assert re.fullmatch(r"[A-Za-z0-9_-]{64}", password)
        sql = sql.replace(":'" + role + "_password'", "'" + password + "'")
    if re.search(r":'[a-z_]+'", sql) or any(line.startswith("\\") for line in sql.splitlines()):
        raise ValueError("Unexpanded psql input in the provisioning SQL")
    (root / "roles.json").write_text(json.dumps({"projectRef": reference, "roles": roles}, indent=2), encoding="utf-8")
    keys = {name: secrets.token_urlsafe(48) for name in ("associagoPeerKey", "bandiPeerKey", "nodeKey", "cronKey")}
    (root / "integration-keys.json").write_text(json.dumps(keys, indent=2), encoding="utf-8")
    (root / "provision.sql").write_text(sql, encoding="utf-8")
    (root / "provision-state.json").write_text(json.dumps({"status": "prepared", "projectRef": reference,
        "schemas": list(SCHEMAS), "applied": False}, indent=2), encoding="utf-8")
    return root


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project-ref", required=True)
    parser.add_argument("--private-root", type=Path, default=Path(os.environ["LOCALAPPDATA"]) / "BandiSuite/private")
    args = parser.parse_args()
    root = prepare(args.project_ref, args.private_root)
    print("Private provisioning prepared: " + str(root))


if __name__ == "__main__":
    main()
