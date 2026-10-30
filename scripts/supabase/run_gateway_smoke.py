"""Native LibrePM JAR -> HTTP gateway -> private PostgreSQL smoke, bounded to 240 seconds.

Creates and drops only one UUID-named scratch database on the explicitly supplied
labelled loopback sandbox. No Supabase, existing application schemas, PC archive,
model/AI calls, real Clerk session or container lifecycle operation is performed.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import secrets
import shutil
import subprocess
import sys
import tempfile
import threading
import time
import uuid

import jwt
from cryptography.hazmat.primitives import serialization
from psycopg import sql

from run_postgres_smoke import (
    AUDIENCE,
    ORG,
    OWNER,
    PREFIX,
    SUBJECT,
    Issuer,
    PostgresSmoke,
    clean_environment,
    load_json,
)

PORTAL = "/api/collaboration/v1"
FAKE_GATEWAY_ISSUER = "https://synthetic-gateway.example.invalid"


def validate(args):
    state, connection, roles = map(
        load_json, (args.pg_state, args.pg_connection, args.pg_roles)
    )
    if (
        state.get("status") != "running"
        or state.get("stage") != "ready"
        or not state.get("container_name", "").startswith("codex-supabase-pg-")
        or connection.get("host") != "127.0.0.1"
        or int(connection.get("port", 0)) != state.get("port")
    ):
        raise ValueError("An active literal-loopback Codex sandbox is required")
    check = subprocess.run(
        ["docker", "inspect", state["container_id"]],
        capture_output=True,
        text=True,
        timeout=10,
        check=True,
    )
    actual = json.loads(check.stdout)[0]
    if (
        actual["Id"] != state["container_id"]
        or not actual["State"]["Running"]
        or actual["Config"].get("Labels", {}).get("codex.task") != "supabase-conversion"
    ):
        raise ValueError("Sandbox identity does not match its supervision state")
    for system in ("librepm", "bandi_portal"):
        if not all(roles.get(system + suffix) for suffix in ("_owner", "_app")):
            raise ValueError("Synthetic owner/runtime credentials are required")
    if (
        not args.librepm_jar.is_file()
        or not (args.bandi_repo / "apps/api/collaboration/gateway.py").is_file()
    ):
        raise ValueError("Native JAR and gateway source are required")
    return connection, roles


class GatewaySmoke(PostgresSmoke):
    def bootstrap_database(self):
        name = "codex_suite_smoke_" + uuid.uuid4().hex
        with self.connect(target=False, autocommit=True) as admin:
            admin.execute(
                sql.SQL("CREATE DATABASE {} TEMPLATE template0").format(
                    sql.Identifier(name)
                )
            )
        self.database = name
        self.state.update(database=name, stage="provisioning-two-private-schemas")
        self.publish()
        with self.connect() as admin:
            for app in ("librepm", "bandi_portal"):
                schema, owner, runtime = map(
                    sql.Identifier, (app, app + "_owner", app + "_app")
                )
                admin.execute(
                    sql.SQL("CREATE SCHEMA {} AUTHORIZATION {}").format(schema, owner)
                )
                admin.execute(
                    sql.SQL("REVOKE ALL ON SCHEMA {} FROM PUBLIC").format(schema)
                )
                admin.execute(
                    sql.SQL("GRANT USAGE ON SCHEMA {} TO {}").format(schema, runtime)
                )
                admin.execute(
                    sql.SQL(
                        "ALTER DEFAULT PRIVILEGES FOR ROLE {} IN SCHEMA {} "
                        "GRANT SELECT,INSERT,UPDATE,DELETE ON TABLES TO {}"
                    ).format(owner, schema, runtime)
                )
                admin.execute(
                    sql.SQL(
                        "ALTER DEFAULT PRIVILEGES FOR ROLE {} IN SCHEMA {} "
                        "GRANT USAGE,SELECT ON SEQUENCES TO {}"
                    ).format(owner, schema, runtime)
                )
                admin.execute(
                    sql.SQL(
                        "ALTER DEFAULT PRIVILEGES FOR ROLE {} "
                        "REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC"
                    ).format(owner)
                )

    def seed_identity(self):
        with self.connect() as admin:
            self.insert(
                admin,
                "librepm",
                "users",
                {
                    "id": OWNER,
                    "username": "gateway-smoke",
                    "display_name": "Synthetic owner",
                    "email": "synthetic@example.invalid",
                    "clerk_user_id": SUBJECT,
                    "password_hash": "not-a-login-password",
                    "active": True,
                    "is_ghost": False,
                    "created_at": "2026-10-30 00:00:00",
                    "updated_at": "2026-10-30 00:00:00",
                    "version": 0,
                    "sync_status": "LOCAL_ONLY",
                },
            )

    def portal(self, method, path, body=None, headers=None, expected=200):
        status, payload = self.request("bandi", method, PORTAL + path, body, headers)
        if status != expected:
            code = (
                payload.get("code", "unknown")
                if isinstance(payload, dict)
                else "non-json"
            )
            raise AssertionError(
                f"Gateway {method} expected {expected}, got {status} ({code})"
            )
        return payload


def run(args, smoke):
    sys.path.insert(0, str(args.bandi_repo.resolve()))
    from apps.api.collaboration.migrate import migrate_portal
    from apps.api.config import Settings

    smoke.bootstrap_database()
    smoke.issuer = Issuer()
    owner_token = smoke.issuer.token()
    smoke.tokens.append(owner_token)
    smoke.state["stage"] = "migrating-gateway"
    smoke.publish()
    key, cron_key = secrets.token_urlsafe(40), secrets.token_urlsafe(40)
    smoke.tokens.extend([key, cron_key])
    settings = {
        "app_env": "test",
        "bandi_mode": "gateway",
        "agno_enabled": False,
        "collab_database_url": smoke.database_url("bandi_portal_app"),
        "collab_migration_database_url": smoke.database_url("bandi_portal_owner"),
        "collab_clerk_issuer": FAKE_GATEWAY_ISSUER,
        "collab_owner_subject": SUBJECT,
        "collab_node_id": "synthetic-native-smoke-node",
        "collab_node_key": key,
        "collab_cron_key": cron_key,
        "cors_origins": "http://127.0.0.1",
        "suite_enabled": True,
        "suite_organization_id": ORG,
        "suite_librepm_key": smoke.keys["bandi"],
    }
    migrate_portal(Settings(_env_file=None, **settings))
    public = (
        smoke.issuer.key.public_key()
        .public_bytes(
            serialization.Encoding.PEM, serialization.PublicFormat.SubjectPublicKeyInfo
        )
        .decode()
    )
    config = smoke.directory / "gateway-config.json"
    config.write_text(
        json.dumps(
            {
                "settings": settings,
                "publicKey": public,
                "listenHost": "127.0.0.1",
                "listenPort": int(smoke.urls["bandi"].rsplit(":", 1)[1]),
            }
        ),
        encoding="utf-8",
    )
    smoke.start(
        "bandi",
        [
            sys.executable,
            str(Path(__file__).with_name("gateway_smoke_server.py")),
            "--config",
            str(config),
            "--bandi-repo",
            str(args.bandi_repo.resolve()),
        ],
        clean_environment(),
    )
    smoke.ready("bandi", "/health")
    jar = smoke.directory / "librepm.jar"
    shutil.copyfile(args.librepm_jar.resolve(), jar)
    smoke.state.update(
        stage="starting-native-librepm",
        librepm_jar_sha256=hashlib.file_digest(jar.open("rb"), "sha256").hexdigest(),
    )
    smoke.publish()
    data = smoke.directory / "librepm-data"
    data.mkdir()
    jdbc = f"jdbc:postgresql://127.0.0.1:{smoke.connection['port']}/{smoke.database}?currentSchema=librepm"
    env = {
        **clean_environment(),
        "SPRING_DATASOURCE_URL": jdbc,
        "SPRING_DATASOURCE_USERNAME": "librepm_app",
        "SPRING_DATASOURCE_PASSWORD": smoke.roles["librepm_app"],
        "SPRING_FLYWAY_URL": jdbc,
        "SPRING_FLYWAY_USER": "librepm_owner",
        "SPRING_FLYWAY_PASSWORD": smoke.roles["librepm_owner"],
        "SUITE_ENABLED": "true",
        "SUITE_ORGANIZATION_ID": ORG,
        "SUITE_IMPORT_OWNER_ID": OWNER,
        "SUITE_ASSOCIAGO_KEY": smoke.keys["associago"],
        "SUITE_BANDI_KEY": smoke.keys["bandi"],
        "SUITE_BANDI_API_URL": smoke.urls["bandi"],
        "SUITE_BANDI_PUBLIC_URL": smoke.urls["bandi"],
        "SUITE_LIBREPM_PUBLIC_URL": smoke.urls["librepm"],
        "CLERK_ISSUER_URI": smoke.issuer.url,
        "CLERK_AUDIENCE": AUDIENCE,
        "DB_POOL_MAX": "2",
        "DB_POOL_MIN": "0",
        "CORS_ALLOWED_ORIGINS": "http://127.0.0.1",
        "LIBREPM_DATA_PATH": str(data),
        "SUITE_HTTP_TIMEOUT_SECONDS": "3",
        "SUITE_SYNC_INITIAL_DELAY_MS": "3600000",
        "SUITE_SYNC_INTERVAL_MS": "3600000",
    }
    java = shutil.which("java")
    if java is None:
        raise RuntimeError("Java21 is required")
    smoke.start(
        "librepm",
        [
            java,
            "-Dlibrepm.data.path=" + str(data),
            "-jar",
            str(jar),
            "--spring.profiles.active=cloud-postgres",
            "--server.address=127.0.0.1",
            "--server.port=" + smoke.urls["librepm"].rsplit(":", 1)[1],
            "--logging.level.root=WARN",
        ],
        env,
    )
    smoke.ready("librepm", "/api/me")
    smoke.seed_identity()
    status, _ = smoke.request(
        "librepm", "GET", "/api/me", headers={"Authorization": "Bearer " + owner_token}
    )
    assert status == 200
    smoke.checks.append("librepm-native-rs256-jwks-linked-identity")
    with smoke.connect() as admin:
        admin.execute("REVOKE ALL ON librepm.flyway_schema_history FROM librepm_app")
        admin.execute(
            "REVOKE ALL ON bandi_portal.portal_schema_migrations FROM bandi_portal_app"
        )
        admin.execute(
            "REVOKE UPDATE,DELETE,TRUNCATE ON bandi_portal.portal_revisions FROM bandi_portal_app"
        )
    now = int(time.time())
    token = jwt.encode(
        {
            "iss": FAKE_GATEWAY_ISSUER,
            "sub": SUBJECT,
            "iat": now,
            "exp": now + 600,
            "azp": "http://127.0.0.1",
        },
        smoke.issuer.key,
        algorithm="RS256",
    )
    smoke.tokens.append(token)
    auth = {"Authorization": "Bearer " + token}
    assert smoke.portal("GET", "/me", headers=auth)["role"] == "owner"
    smoke.checks.append("gateway-real-http-rs256-injected-public-key")
    smoke.state["stage"] = "native-peer-sync-and-owner-approval"
    smoke.publish()
    seed = {
        "schemaVersion": 1,
        "organizationId": ORG,
        "source": {
            "system": "associago",
            "entity": "project",
            "localId": "1",
            "revision": "v1",
        },
        "title": "Synthetic gateway project",
        "description": "Public common metadata",
        "plannedBudget": {"amount": "1000.25", "currency": "EUR"},
        "projectDeadline": None,
        "actorSubject": SUBJECT,
    }

    def registry(name, method, path, body=None):
        template, suite_id = path, None
        if path.startswith(PREFIX + "/peer/projects/"):
            parts = path[len(PREFIX + "/peer/projects/") :].split("/")
            suite_id = parts[0]
            template = PREFIX + "/peer/projects/{suiteProjectId}"
            if len(parts) == 2:
                template += "/" + parts[1]
        return smoke.expect(
            name,
            "librepm",
            method,
            template,
            200,
            body=body,
            headers=smoke.peer("librepm", peer="associago"),
            suite_id=suite_id,
        )

    context = registry(
        "native-lp-imports-common-project", "POST", PREFIX + "/peer/projects", seed
    )
    sid = context["suiteProjectId"]
    repeated = registry(
        "native-source-repeat-preserves-uuid", "POST", PREFIX + "/peer/projects", seed
    )
    assert repeated["suiteProjectId"] == sid
    sync_path = PREFIX + "/peer/projects/" + sid + "/sync"

    def sync(name):
        return registry(name, "POST", sync_path)

    empty = sync("lp-publishes-to-live-gateway")
    assert (
        next(v for v in empty["sync"] if v["system"] == "bandi")["status"] == "synced"
    )
    assert empty["preparation"] is None
    portal = smoke.portal("GET", "/projects/" + sid, headers=auth)
    assert portal["projection"]["title"] == seed["title"]
    assert portal["snapshot"]["localId"] is None
    smoke.checks.append("unapproved-gateway-has-no-fabricated-preparation")
    draft = smoke.portal(
        "POST",
        "/drafts",
        {
            "title": "Approved candidature title",
            "suiteProjectId": sid,
            "content": "PRIVATE DRAFT CONTENT",
        },
        auth,
        expected=201,
    )
    before = sync("private-draft-link-does-not-publish")
    assert before["preparation"] is None
    approved = smoke.portal(
        "POST",
        "/projects/" + sid + "/preparation",
        {"draftId": draft["id"], "expectedDraftVersion": 1},
        auth,
    )
    assert approved["snapshot"]["preparation"]["version"] == 1
    shared = sync("native-lp-receives-approved-candidature-snapshot")
    assert shared["preparation"]["localId"] == draft["id"]
    assert (
        shared["preparation"]["title"] == draft["title"]
        and shared["preparation"]["version"] == 1
    )
    assert "PRIVATE DRAFT CONTENT" not in json.dumps(shared)
    linked = [v for v in shared["instances"] if v["system"] == "bandi"]
    assert len(linked) == 1 and linked[0]["localId"] == draft["id"]
    again = sync("target-local-id-repeat-is-stable")
    assert again["preparation"] == shared["preparation"]
    changed = smoke.portal(
        "PATCH",
        "/drafts/" + draft["id"],
        {"expectedVersion": 1, "title": "Unapproved private title"},
        auth,
    )
    assert changed["version"] == 2
    private = sync("private-edit-does-not-update-approved-snapshot")
    assert private["preparation"] == shared["preparation"]
    other = registry(
        "same-title-source-has-distinct-native-uuid",
        "POST",
        PREFIX + "/peer/projects",
        {**seed, "source": {**seed["source"], "localId": "2"}},
    )
    assert other["suiteProjectId"] != sid
    registry(
        "second-project-projection-is-distinct",
        "POST",
        PREFIX + "/peer/projects/" + other["suiteProjectId"] + "/sync",
    )
    with smoke.connect() as admin:
        assert admin.execute("SELECT count(*) FROM librepm.projects").fetchone()[0] == 2
        assert (
            admin.execute(
                "SELECT count(*) FROM bandi_portal.portal_suite_projects"
            ).fetchone()[0]
            == 2
        )
        assert (
            admin.execute("SELECT count(*) FROM bandi_portal.portal_drafts").fetchone()[
                0
            ]
            == 1
        )
        assert (
            admin.execute(
                "SELECT count(*) FROM bandi_portal.portal_requests"
            ).fetchone()[0]
            == 0
        )
    smoke.checks.append("two-real-projects-one-private-draft-zero-pc-jobs")
    smoke.state.update(
        status="succeeded",
        stage="verified",
        checks=smoke.checks,
        fidelity="Native LP JAR + real HTTP gateway + PG17; synthetic OIDC/JWKS, not real Clerk SSO",
    )
    smoke.persist_evidence()
    smoke.publish()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("pg-state", "pg-connection", "pg-roles", "librepm-jar", "bandi-repo"):
        parser.add_argument("--" + name, type=Path, required=True)
    parser.add_argument(
        "--output",
        type=Path,
        default=Path(__file__).resolve().parents[2]
        / "deployment/.artifacts/gateway-native.json",
    )
    args = parser.parse_args()
    connection, roles = validate(args)
    with tempfile.TemporaryDirectory(prefix="suite-gateway-native-") as directory:
        smoke = GatewaySmoke(Path(directory), args.output.resolve(), connection, roles)
        watchdog = threading.Timer(240, smoke.stop)
        watchdog.daemon = True
        watchdog.start()
        try:
            run(args, smoke)
            print(
                f"PASS: {len(smoke.checks)} native LP/gateway/PG checks; synthetic identities only"
            )
        except Exception as error:
            smoke.state.update(
                status="failed", stage="failed", error=type(error).__name__
            )
            smoke.publish()
            smoke.persist_evidence()
            smoke.persist_diagnostics()
            raise
        finally:
            watchdog.cancel()
            smoke.stop()
        if smoke.state.get("cleanup_error"):
            raise RuntimeError(
                "Scratch database cleanup failed; see the sanitized state"
            )


if __name__ == "__main__":
    main()
