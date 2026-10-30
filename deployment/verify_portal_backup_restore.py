"""Real restore drill, limited to two newly created UUID databases in the local sandbox."""

from __future__ import annotations

import argparse
from dataclasses import replace
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
import uuid

from backup_cloud_schemas import (
    BackupError,
    Connection,
    docker_command,
    outside_repository,
    run_backup,
    state_write,
    timestamp,
)


def fingerprint(connection):
    from psycopg import sql

    result = {}
    for table in ("portal_drafts", "portal_revisions"):
        digest, count = hashlib.sha256(), 0
        with connection.cursor() as cursor:
            cursor.execute(
                sql.SQL(
                    "SELECT row_to_json(t)::text FROM {}.{} t ORDER BY row_to_json(t)::text"
                ).format(sql.Identifier("bandi_portal"), sql.Identifier(table))
            )
            while rows := cursor.fetchmany(500):
                for (row,) in rows:
                    digest.update(row.encode() + b"\n")
                    count += 1
        result[table] = {"count": count, "sha256": digest.hexdigest()}
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--sandbox",
        type=Path,
        required=True,
        help="Ignored root-owned sandbox run directory",
    )
    parser.add_argument("--bandi-repo", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()
    import psycopg
    from psycopg import sql
    from sqlalchemy.engine import URL

    state = json.loads((args.sandbox / "state.json").read_text())
    conn = json.loads((args.sandbox / "connection.json").read_text())
    roles = json.loads((args.sandbox / "roles.json").read_text())
    if (
        state.get("status") != "running"
        or state.get("stage") != "ready"
        or conn.get("host") != "127.0.0.1"
        or conn.get("database") != "suite_validation"
        or conn.get("port") != state.get("port")
    ):
        raise BackupError("RESTORE_LOCAL_SANDBOX_REQUIRED")
    inspected = subprocess.run(
        ["docker", "inspect", state["container_id"]],
        capture_output=True,
        text=True,
        check=True,
        timeout=8,
    )
    metadata = json.loads(inspected.stdout)[0]
    if (
        metadata["Id"] != state["container_id"]
        or metadata["Config"].get("Labels", {}).get("codex.task")
        != "supabase-conversion"
        or not metadata["State"]["Running"]
    ):
        raise BackupError("RESTORE_SANDBOX_ID_MISMATCH")
    network = "container:" + state["container_id"]
    output = outside_repository(args.output_dir)
    output.mkdir(parents=True, exist_ok=False)
    deadline = time.monotonic() + 200
    created = []
    fixture_db = "codex_cloud_backup_fixture_" + uuid.uuid4().hex
    target_db = "codex_cloud_backup_restore_" + uuid.uuid4().hex
    operator = Connection(
        "127.0.0.1",
        5432,
        conn["database"],
        "bandi_portal_owner",
        roles["bandi_portal_owner"],
        "disable",
    )
    drill = {
        "status": "running",
        "stage": "copy_source",
        "runner_pid": os.getpid(),
        "started_at": timestamp(),
        "created_databases": [],
    }
    state_write(output, drill)

    def connect(database=None, *, readonly=False, autocommit=False):
        connection = psycopg.connect(
            host="127.0.0.1",
            hostaddr="127.0.0.1",
            port=conn["port"],
            dbname=database or conn["database"],
            user=conn["user"],
            password=conn["password"],
            connect_timeout=5,
            options="-c statement_timeout=10000 -c lock_timeout=5000",
            autocommit=autocommit,
        )
        if readonly:
            connection.execute(
                "SET TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY"
            )
            connection.execute("SET TIME ZONE 'UTC'")
        return connection

    def restore(archive, database):
        target = Connection(
            "127.0.0.1", 5432, database, conn["user"], conn["password"], "disable"
        )
        docker_command(
            archive,
            [
                "pg_restore",
                "--no-owner",
                "--no-acl",
                "--exit-on-error",
                "--single-transaction",
                "--dbname=" + database,
                "/out/archive.dump",
            ],
            drill,
            seconds=35,
            source=target,
            network=network,
            deadline=deadline,
        )

    try:
        # Source is read-only. Only a disposable clone receives synthetic test rows.
        original = run_backup(
            operator,
            output,
            schemas=("bandi_portal",),
            network=network,
            deadline=deadline,
        )
        with connect(autocommit=True) as admin:
            for database in (fixture_db, target_db):
                if not re.fullmatch(
                    r"codex_cloud_backup_(?:fixture|restore)_[0-9a-f]{32}", database
                ):
                    raise BackupError("RESTORE_TARGET_INVALID")
                admin.execute(
                    sql.SQL("CREATE DATABASE {} TEMPLATE template0").format(
                        sql.Identifier(database)
                    )
                )
                created.append(database)
        drill.update(stage="seed_disposable_clone", created_databases=created[:])
        state_write(output, drill)
        restore(original, fixture_db)
        sys.path.insert(0, str(args.bandi_repo.resolve()))
        from apps.api.collaboration.schemas import DraftCreate, DraftPatch
        from apps.api.collaboration.security import Principal
        from apps.api.collaboration.store import PortalStore
        from apps.api.config import Settings

        fixture_url = URL.create(
            "postgresql+psycopg",
            username=conn["user"],
            password=conn["password"],
            host="127.0.0.1",
            port=conn["port"],
            database=fixture_db,
        ).render_as_string(hide_password=False)
        subject = "restore_fixture_" + uuid.uuid4().hex
        settings = Settings(
            _env_file=None,
            app_env="test",
            bandi_mode="gateway",
            collab_database_url=fixture_url,
            collab_clerk_issuer="https://fixture.clerk.accounts.dev",
            collab_owner_subject=subject,
            collab_node_id="restore-fixture-node",
            collab_node_key="synthetic-restoration-node-key-at-least-thirty-two",
            cors_origins="https://fixture.example.org",
            agno_enabled=False,
        )
        store = PortalStore(settings)
        try:
            principal = Principal(subject, "owner", None)
            draft = store.create_draft(
                DraftCreate(
                    title="Synthetic restore fixture",
                    content="Version one: synthetic only",
                ),
                principal,
            )
            store.patch_draft(
                draft.id,
                DraftPatch(
                    expected_version=draft.version,
                    content="Version two: synthetic only",
                ),
                principal,
            )
        finally:
            store.close()
        drill.update(stage="dump_snapshot")
        state_write(output, drill)
        with connect(fixture_db, readonly=True) as source:
            expected = fingerprint(source)
            snapshot = source.execute("SELECT pg_export_snapshot()").fetchone()[0]
            archive = run_backup(
                replace(
                    operator,
                    database=fixture_db,
                    user=conn["user"],
                    password=conn["password"],
                ),
                output,
                schemas=("bandi_portal",),
                snapshot=snapshot,
                network=network,
                deadline=deadline,
            )
        drill.update(stage="restore_and_compare")
        state_write(output, drill)
        restore(archive, target_db)
        with connect(target_db, readonly=True) as target:
            actual = fingerprint(target)
        if (
            expected != actual
            or expected["portal_drafts"]["count"] < 1
            or expected["portal_revisions"]["count"] < 2
        ):
            raise BackupError("RESTORE_DATA_MISMATCH")
        result = {
            "status": "succeeded",
            "checked_schema": "bandi_portal",
            "source_original_modified": False,
            "source_snapshot": expected,
            "restored_snapshot": actual,
            "archive": str(archive),
            "completed_at": timestamp(),
        }
        (output / "restore-verification.json").write_text(
            json.dumps(result, indent=2) + "\n", encoding="utf-8"
        )
        manifest = json.loads((archive / "manifest.json").read_text())
        manifest["restore_tested"] = True
        (archive / "manifest.json").write_text(
            json.dumps(manifest, indent=2) + "\n", encoding="utf-8"
        )
        drill.update(status="succeeded", stage="complete")
    except BaseException as error:
        drill.update(
            status="failed",
            stage="failed",
            error_code=str(error)
            if isinstance(error, BackupError)
            else "RESTORE_DRILL_FAILED",
        )
        raise
    finally:
        # Drop only database names successfully created by this process, never the source.
        with connect(autocommit=True) as admin:
            for database in reversed(created):
                if database not in {fixture_db, target_db} or not re.fullmatch(
                    r"codex_cloud_backup_(?:fixture|restore)_[0-9a-f]{32}", database
                ):
                    raise BackupError("RESTORE_CLEANUP_REFUSED")
                admin.execute(
                    "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname=%s AND pid<>pg_backend_pid()",
                    (database,),
                )
                admin.execute(
                    sql.SQL("DROP DATABASE {}").format(sql.Identifier(database))
                )
        drill["temporary_databases_removed"] = len(created)
        state_write(output, drill)
    print(
        "PASS: ripristino reale bandi_portal, righe e SHA256 identici, database temporanei rimossi. "
        + str(output)
    )


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(
            str(error) if isinstance(error, BackupError) else "RESTORE_DRILL_FAILED",
            file=sys.stderr,
        )
        raise SystemExit(1) from None
