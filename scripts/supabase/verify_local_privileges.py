"""Exercise real PostgreSQL permissions in the supervisor's local test database.

Never accepts a remote host or arbitrary database/container. The only temporary
objects are newly created, uniquely named probe tables in the three test schemas.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import subprocess
import uuid


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run-directory", required=True, type=Path)
    args = parser.parse_args()
    directory = args.run_directory.resolve()
    state = json.loads((directory / "state.json").read_text())
    connection = json.loads((directory / "connection.json").read_text())
    roles = json.loads((directory / "roles.json").read_text())
    if state["status"] != "running" or state["stage"] != "ready" or connection["host"] != "127.0.0.1" or connection["database"] != "suite_validation":
        raise RuntimeError("Only the running local validation sandbox is allowed")
    container = state["container_id"]
    info = subprocess.run(["docker", "inspect", container], check=True, capture_output=True, text=True, timeout=10)
    metadata = json.loads(info.stdout)[0]
    if metadata["Config"]["Labels"].get("codex.task") != "supabase-conversion":
        raise RuntimeError("The database container is not owned by this test workflow")
    table = "_codex_probe_" + uuid.uuid4().hex[:12]
    created = []
    checks = []

    def sql(role, statement, permitted=True):
        command = ["docker", "exec", "-e", "PGPASSWORD=" + roles[role], container, "psql", "-X", "-h", "127.0.0.1",
                   "-U", role, "-d", connection["database"], "-v", "ON_ERROR_STOP=1", "-v", "VERBOSITY=sqlstate", "-Atc", statement]
        result = subprocess.run(command, capture_output=True, text=True, timeout=15)
        if permitted and result.returncode:
            raise AssertionError("An authorized operation failed for " + role)
        if not permitted and (result.returncode == 0 or "42501" not in result.stderr):
            raise AssertionError("Expected insufficient_privilege (42501) for " + role)
        return result.stdout.strip()

    try:
        for app in ("associago", "librepm", "bandi"):
            sql(app + "_owner", f'CREATE TABLE "{app}"."{table}" (id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY, value text NOT NULL)')
            created.append(app)
            checks.append(app + ": owner DDL")
            sql(app + "_owner", f'CREATE FUNCTION "{app}"."{table}_function" () RETURNS integer LANGUAGE sql AS \'SELECT 1\'')
            sql(app + "_app", f'SELECT "{app}"."{table}_function" ()', permitted=False)
            checks.append(app + ": function execute requires an explicit grant")
            inserted = sql(app + "_app", f'INSERT INTO "{app}"."{table}" (value) VALUES (\'synthetic\') RETURNING id')
            if not inserted.startswith("1"):
                raise AssertionError("Runtime sequence generation failed")
            if sql(app + "_app", f'SELECT value FROM "{app}"."{table}"') != "synthetic":
                raise AssertionError("Runtime row round-trip failed")
            checks.append(app + ": runtime insert, sequence and select")
            sql(app + "_app", f'CREATE TABLE "{app}"."{table}_forbidden" (id int)', permitted=False)
            checks.append(app + ": persistent runtime DDL denied")
        for app in ("associago", "librepm", "bandi"):
            for other in ("associago", "librepm", "bandi"):
                if app != other:
                    sql(app + "_app", f'SELECT * FROM "{other}"."{table}"', permitted=False)
                    checks.append(app + ": cross-schema read denied: " + other)
            history = "alembic_version" if app == "bandi" else "flyway_schema_history"
            sql(app + "_app", f'DELETE FROM "{app}"."{history}" WHERE false', permitted=False)
            checks.append(app + ": migration history write denied")
    finally:
        for app in reversed(created):
            # Also clean the uniquely named negative probe if permissions were
            # unexpectedly permissive and the assertion above rejected it.
            sql(app + "_owner", f'DROP TABLE IF EXISTS "{app}"."{table}_forbidden"')
            sql(app + "_owner", f'DROP FUNCTION IF EXISTS "{app}"."{table}_function" ()')
            sql(app + "_owner", f'DROP TABLE "{app}"."{table}"')
    result = {"status": "succeeded", "checks": checks}
    (directory / "privilege-checks.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
    print(f"PASS: {len(checks)} real PostgreSQL privilege checks")


if __name__ == "__main__":
    main()
