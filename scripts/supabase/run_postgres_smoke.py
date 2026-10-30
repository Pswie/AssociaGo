"""Three native backends, real PostgreSQL17 and real JWT verification on disposable local data.

Only a labelled sandbox created by local_database.py is accepted. This runner creates
one UUID-named database, uses existing synthetic roles, and drops only that database.
It never stops the shared PostgreSQL container or contacts a hosted Supabase project.
"""

from __future__ import annotations

import argparse
import base64
from datetime import datetime, timezone
from decimal import Decimal
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import sys
import tempfile
import threading
import time
import uuid

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "integration"))
from run_native_smoke import (
    OWNER,
    ORG,
    PREFIX,
    PREPARATION,
    Smoke,
    clean_environment,
    port,
)

import jwt
import psycopg
from psycopg import sql
from cryptography.hazmat.primitives.asymmetric import rsa

APPS = ("associago", "librepm", "bandi")
SUBJECT = "user_synthetic_postgres_smoke"
AUDIENCE = "suite-postgres-smoke"


def load_json(path):
    return json.loads(path.read_text(encoding="utf-8"))


def validate_local_sandbox(args):
    state = load_json(args.pg_state)
    connection = load_json(args.pg_connection)
    roles = load_json(args.pg_roles)
    if (
        state.get("status") != "running"
        or state.get("stage") != "ready"
        or not state.get("container_name", "").startswith("codex-supabase-pg-")
        or connection.get("host") != "127.0.0.1"
        or int(connection.get("port", 0)) != int(state.get("port", -1))
    ):
        raise RuntimeError(
            "An active, loopback-only Codex PostgreSQL sandbox is required"
        )
    inspected = subprocess.run(
        ["docker", "inspect", state["container_id"]],
        capture_output=True,
        text=True,
        timeout=10,
        check=True,
    )
    actual = json.loads(inspected.stdout)[0]
    if (
        actual["Id"] != state["container_id"]
        or not actual["State"]["Running"]
        or actual["Config"].get("Labels", {}).get("codex.task") != "supabase-conversion"
    ):
        raise RuntimeError(
            "The recorded sandbox identity does not match the running container"
        )
    for app in APPS:
        if any(not roles.get(app + suffix) for suffix in ("_owner", "_app")):
            raise RuntimeError("Synthetic sandbox application roles are missing")
    return connection, roles


class Issuer:
    """Loopback OIDC/JWKS with a fresh in-memory RSA key; no auth bypass in either Java app."""

    def __init__(self):
        self.key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        self.kid = "synthetic-" + uuid.uuid4().hex
        numbers = self.key.public_key().public_numbers()

        def encoded(value):
            return (
                base64.urlsafe_b64encode(
                    value.to_bytes((value.bit_length() + 7) // 8, "big")
                )
                .rstrip(b"=")
                .decode()
            )

        public = {
            "keys": [
                {
                    "kty": "RSA",
                    "use": "sig",
                    "alg": "RS256",
                    "kid": self.kid,
                    "n": encoded(numbers.n),
                    "e": encoded(numbers.e),
                }
            ]
        }
        owner = self

        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                if self.path == "/.well-known/openid-configuration":
                    payload = {
                        "issuer": owner.url,
                        "jwks_uri": owner.url + "/keys",
                        "authorization_endpoint": owner.url + "/authorize",
                        "token_endpoint": owner.url + "/token",
                        "response_types_supported": ["id_token"],
                        "subject_types_supported": ["public"],
                        "id_token_signing_alg_values_supported": ["RS256"],
                    }
                elif self.path == "/keys":
                    payload = public
                else:
                    self.send_error(404)
                    return
                data = json.dumps(payload).encode()
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(data)))
                self.end_headers()
                self.wfile.write(data)

            def log_message(self, *_):
                pass

        self.server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self.url = "http://127.0.0.1:" + str(self.server.server_address[1])
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()

    def token(self, role="presidente"):
        now = int(time.time())
        return jwt.encode(
            {
                "iss": self.url,
                "sub": SUBJECT,
                "aud": AUDIENCE,
                "iat": now,
                "nbf": now - 5,
                "exp": now + 600,
                "role": role,
                "email": "synthetic@example.invalid",
                "email_verified": True,
            },
            self.key,
            algorithm="RS256",
            headers={"kid": self.kid},
        )

    def close(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=3)


class PostgresSmoke(Smoke):
    def __init__(self, directory, output, connection, roles):
        super().__init__(directory, output)
        self.connection = connection
        self.roles = roles
        self.database = None
        self.issuer = None
        self.tokens = []
        self.cleanup_lock = threading.Lock()
        self.closed = False
        self.state = {
            "status": "running",
            "stage": "initializing",
            "runner_pid": os.getpid(),
            "database": None,
        }
        self.publish()

    def publish(self):
        self.state["updated_at"] = datetime.now(timezone.utc).isoformat()
        target = self.output.with_suffix(".state.json")
        target.parent.mkdir(parents=True, exist_ok=True)
        temporary = target.with_suffix(".tmp")
        temporary.write_text(json.dumps(self.state, indent=2) + "\n", encoding="utf-8")
        temporary.replace(target)

    def connect(self, *, role=None, target=True, autocommit=False):
        values = {
            key: self.connection[key] for key in ("host", "port", "user", "password")
        }
        values["dbname"] = self.database if target else self.connection["database"]
        if role:
            values.update(user=role, password=self.roles[role])
        return psycopg.connect(
            **values,
            connect_timeout=5,
            options="-c statement_timeout=10000 -c lock_timeout=5000",
            autocommit=autocommit,
        )

    def bootstrap_database(self):
        name = "codex_suite_smoke_" + uuid.uuid4().hex
        with self.connect(target=False, autocommit=True) as admin:
            admin.execute(
                sql.SQL("CREATE DATABASE {} TEMPLATE template0").format(
                    sql.Identifier(name)
                )
            )
        self.database = name
        self.state.update(database=name, stage="provisioning-private-schemas")
        self.publish()
        with self.connect() as admin:
            admin.execute("CREATE SCHEMA extensions")
            admin.execute("CREATE EXTENSION vector WITH SCHEMA extensions")
            for app in APPS:
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
                    sql.SQL("GRANT USAGE ON SCHEMA extensions TO {}, {}").format(
                        owner, runtime
                    )
                )
                admin.execute(
                    sql.SQL(
                        "ALTER DEFAULT PRIVILEGES FOR ROLE {} IN SCHEMA {} GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO {}"
                    ).format(owner, schema, runtime)
                )
                admin.execute(
                    sql.SQL(
                        "ALTER DEFAULT PRIVILEGES FOR ROLE {} IN SCHEMA {} GRANT USAGE, SELECT ON SEQUENCES TO {}"
                    ).format(owner, schema, runtime)
                )
                admin.execute(
                    sql.SQL(
                        "ALTER DEFAULT PRIVILEGES FOR ROLE {} REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC"
                    ).format(owner)
                )

    def database_url(self, role):
        from sqlalchemy.engine import URL

        return URL.create(
            "postgresql+psycopg",
            username=role,
            password=self.roles[role],
            host="127.0.0.1",
            port=self.connection["port"],
            database=self.database,
        ).render_as_string(hide_password=False)

    def insert(self, admin, schema, table, values):
        columns = {
            row[0]
            for row in admin.execute(
                "SELECT column_name FROM information_schema.columns WHERE table_schema=%s AND table_name=%s",
                (schema, table),
            )
        }
        values = {key: value for key, value in values.items() if key in columns}
        admin.execute(
            sql.SQL("INSERT INTO {} ({}) VALUES ({})").format(
                sql.Identifier(schema, table),
                sql.SQL(",").join(map(sql.Identifier, values)),
                sql.SQL(",").join(sql.Placeholder() for _ in values),
            ),
            list(values.values()),
        )

    def seed_identity(self):
        with self.connect() as admin:
            self.insert(
                admin,
                "librepm",
                "users",
                {
                    "id": OWNER,
                    "username": "suite-pg-smoke",
                    "display_name": "Synthetic owner",
                    "email": "synthetic@example.invalid",
                    "clerk_user_id": SUBJECT,
                    "password_hash": "not-a-login-password",
                    "active": True,
                    "is_ghost": False,
                    "created_at": "2026-10-30 00:00:00.000",
                    "updated_at": "2026-10-30 00:00:00.000",
                    "version": 0,
                    "sync_status": "LOCAL_ONLY",
                },
            )
            self.insert(
                admin,
                "associago",
                "associations",
                {
                    "id": 1,
                    "name": "Synthetic PG association",
                    "email": "synthetic@example.invalid",
                    "slug": "synthetic-pg-association",
                    "type": "APS",
                    "is_active": True,
                    "use_remote_db": False,
                    "db_ssl": False,
                },
            )
            self.insert(
                admin,
                "associago",
                "users",
                {
                    "id": 1,
                    "first_name": "Synthetic",
                    "last_name": "Operator",
                    "email": "synthetic@example.invalid",
                    "clerk_user_id": SUBJECT,
                    "tax_code": "RSSMRA80A01H501U",
                },
            )
            self.insert(
                admin,
                "associago",
                "user_associations",
                {
                    "id": 1,
                    "user_id": 1,
                    "association_id": 1,
                    "role": "PRESIDENT",
                    "status": "ACTIVE",
                    "is_board_member": True,
                },
            )
            self.insert(
                admin,
                "associago",
                "members",
                {
                    "id": 1,
                    "first_name": "Synthetic",
                    "last_name": "Operator",
                    "fiscal_code": "RSSMRA80A01H501U",
                },
            )
            self.insert(
                admin,
                "associago",
                "volunteers",
                {"id": 1, "member_id": 1, "status": "ACTIVE"},
            )

    def finalize_and_check_privileges(self):
        with self.connect() as admin:
            for app in APPS:
                history = (
                    "alembic_version" if app == "bandi" else "flyway_schema_history"
                )
                admin.execute(
                    sql.SQL("REVOKE ALL ON TABLE {} FROM {}").format(
                        sql.Identifier(app, history), sql.Identifier(app + "_app")
                    )
                )
                assert not admin.execute(
                    "SELECT has_table_privilege(%s,%s,'SELECT')",
                    (app + "_app", app + "." + history),
                ).fetchone()[0]
                assert not admin.execute(
                    "SELECT has_schema_privilege(%s,%s,'CREATE')", (app + "_app", app)
                ).fetchone()[0]
                for other in APPS:
                    if other != app:
                        assert not admin.execute(
                            "SELECT has_schema_privilege(%s,%s,'USAGE')",
                            (app + "_app", other),
                        ).fetchone()[0]
        with self.connect(role="bandi_app") as runtime:
            assert not runtime.execute(
                "SELECT has_function_privilege(current_user, "
                "'bandi.reject_project_preparation_revision_mutation()', 'EXECUTE')"
            ).fetchone()[0]
            revision_id = uuid.uuid4()
            runtime.execute(
                "INSERT INTO bandi.project_preparation_revisions "
                "(id,preparation_id,revision_number,version,event,snapshot,created_at) "
                "VALUES (%s,%s,1,1,'created','{}'::json,CURRENT_TIMESTAMP)",
                (revision_id, uuid.UUID(PREPARATION)),
            )
            try:
                with runtime.transaction():
                    runtime.execute(
                        "UPDATE bandi.project_preparation_revisions SET event='forbidden' WHERE id=%s",
                        (revision_id,),
                    )
            except psycopg.errors.RaiseException as error:
                assert error.sqlstate == "P0001"
            else:
                raise AssertionError(
                    "The immutable revision trigger must reject the runtime UPDATE"
                )
            assert (
                runtime.execute(
                    "SELECT event FROM bandi.project_preparation_revisions WHERE id=%s",
                    (revision_id,),
                ).fetchone()[0]
                == "created"
            )
        self.checks.append(
            "private-role-isolation-migration-history-and-trigger-protection"
        )

    def persist_diagnostics(self):
        target = self.output.parent / (self.output.stem + "-diagnostics")
        target.mkdir(parents=True, exist_ok=True)
        secrets_to_hide = [
            *self.keys.values(),
            *self.roles.values(),
            self.connection["password"],
            *self.tokens,
        ]
        for log in self.directory.glob("*.log"):
            value = log.read_text(encoding="utf-8", errors="replace")
            for secret in secrets_to_hide:
                value = value.replace(secret, "[redacted-synthetic-credential]")
            (target / log.name).write_text(
                "\n".join(value.splitlines()[-100:]) + "\n", encoding="utf-8"
            )

    def stop(self):
        with self.cleanup_lock:
            if self.closed:
                return
            self.closed = True
            super().stop()
            if self.issuer:
                self.issuer.close()
            if self.database:
                if not self.database.startswith("codex_suite_smoke_"):
                    raise RuntimeError(
                        "Refusing to drop a database outside this smoke run"
                    )
                try:
                    with self.connect(target=False, autocommit=True) as admin:
                        admin.execute(
                            sql.SQL("DROP DATABASE {} WITH (FORCE)").format(
                                sql.Identifier(self.database)
                            )
                        )
                    self.state["database_removed"] = True
                except Exception as error:
                    self.state.update(
                        status="failed", cleanup_error=type(error).__name__
                    )
            self.state["stage"] = "cleaned"
            if self.state["status"] == "running":
                self.state["status"] = "failed"
            self.publish()


def run(args, smoke):
    smoke.bootstrap_database()
    smoke.issuer = Issuer()
    token = smoke.issuer.token()
    smoke.tokens.append(token)
    authorization = {"Authorization": "Bearer " + token}
    java = shutil.which("java")
    if not java:
        raise RuntimeError("Java21 is required")
    common = {
        "SUITE_ENABLED": "true",
        "SUITE_ORGANIZATION_ID": ORG,
        "SUITE_ASSOCIAGO_KEY": smoke.keys["associago"],
        "SUITE_BANDI_KEY": smoke.keys["bandi"],
        "CLERK_ISSUER_URI": smoke.issuer.url,
        "CLERK_AUDIENCE": AUDIENCE,
        "CORS_ALLOWED_ORIGINS": "http://127.0.0.1",
        "DB_POOL_MAX": "2",
        "DB_POOL_MIN": "0",
    }
    for system, url in smoke.urls.items():
        common[f"SUITE_{system.upper()}_API_URL"] = url
        common[f"SUITE_{system.upper()}_PUBLIC_URL"] = url
    for system, jar in (
        ("librepm", args.librepm_jar),
        ("associago", args.associago_jar),
    ):
        data = smoke.directory / system
        data.mkdir()
        snapshot = smoke.directory / (system + ".jar")
        shutil.copyfile(jar.resolve(), snapshot)
        jdbc = f"jdbc:postgresql://127.0.0.1:{smoke.connection['port']}/{smoke.database}?currentSchema={system}"
        env = {
            **clean_environment(),
            **common,
            "SPRING_DATASOURCE_URL": jdbc,
            "SPRING_DATASOURCE_USERNAME": system + "_app",
            "SPRING_DATASOURCE_PASSWORD": smoke.roles[system + "_app"],
            "SPRING_FLYWAY_URL": jdbc,
            "SPRING_FLYWAY_USER": system + "_owner",
            "SPRING_FLYWAY_PASSWORD": smoke.roles[system + "_owner"],
            "SUITE_IMPORT_OWNER_ID": OWNER,
            "SUITE_ASSOCIATION_ID": "1",
            "SUITE_HTTP_TIMEOUT_SECONDS": "3",
            "SUITE_SYNC_INTERVAL_MS": "3600000",
            "SUITE_SYNC_INITIAL_DELAY_MS": "3600000",
            "LIBREPM_DATA_PATH": str(data),
        }
        smoke.start(
            system,
            [
                java,
                f"-D{system}.data.path={data}",
                "-jar",
                str(snapshot),
                "--spring.profiles.active=cloud-postgres",
                "--server.address=127.0.0.1",
                "--server.port=" + smoke.urls[system].rsplit(":", 1)[1],
                "--logging.level.root=WARN",
                "--logging.level.org.hibernate.SQL=WARN",
            ],
            env,
        )
    smoke.ready("librepm", "/api/me")
    smoke.ready("associago", "/actuator/health")
    smoke.seed_identity()
    password = secrets.token_urlsafe(24)
    config = smoke.directory / "bandi-pg-config.json"
    config.write_text(
        json.dumps(
            {
                "databaseUrl": smoke.database_url("bandi_app"),
                "migrationUrl": smoke.database_url("bandi_owner"),
                "organizationId": ORG,
                "preparationId": PREPARATION,
                "key": smoke.keys["bandi"],
                "password": password,
                "sessionSecret": secrets.token_urlsafe(48),
                "registryUrl": smoke.urls["librepm"],
            }
        ),
        encoding="utf-8",
    )
    python = (
        args.bandi_repo
        / ".venv"
        / ("Scripts/python.exe" if os.name == "nt" else "bin/python")
    )
    bandi_child = smoke.start(
        "bandi",
        [
            str(python.resolve()),
            str(Path(__file__).with_name("bandi_pg_fixture.py")),
            "--repo",
            str(args.bandi_repo.resolve()),
            "--config",
            str(config),
            "--port",
            smoke.urls["bandi"].rsplit(":", 1)[1],
        ],
        clean_environment(),
    )
    smoke.ready("bandi", "/auth/me")
    smoke.finalize_and_check_privileges()
    smoke.state["stage"] = "verifying-native-http-workflow"
    smoke.publish()
    print(
        "Three PostgreSQL-backed services ready; verifying JWT, operator login and shared projects.",
        flush=True,
    )
    for app in ("associago", "librepm"):
        status, _ = smoke.request(app, "GET", PREFIX + "/projects")
        assert status == 401, f"Cloud authentication must remain required for {app}"
        smoke.checks.append(app + "-cloud-user-auth-required")
    smoke.expect("operator-auth-required", "bandi", "GET", PREFIX + "/projects", 401)
    for app, path in (("associago", "/api/v1/me"), ("librepm", "/api/me")):
        status, _ = smoke.request(app, "GET", path, headers=authorization)
        assert status == 200, f"Verified JWT must resolve the seeded {app} identity"
        smoke.checks.append(app + "-signed-jwt-and-linked-local-identity")
    status, _ = smoke.request(
        "bandi",
        "POST",
        "/auth/login",
        {"username": "suite-smoke", "password": password},
    )
    assert status == 200, "Real operator session login failed"
    smoke.checks.append("real-operator-session-login")
    shared = smoke.expect(
        "bandi-share-through-cloud-registry",
        "bandi",
        "POST",
        PREFIX + "/projects/share",
        200,
        body={"localId": PREPARATION},
    )
    sid = shared["suiteProjectId"]
    repeated = smoke.expect(
        "share-idempotent-on-postgres",
        "bandi",
        "POST",
        PREFIX + "/projects/share",
        200,
        body={"localId": PREPARATION},
    )
    assert repeated["suiteProjectId"] == sid
    context = smoke.expect(
        "three-postgres-services-synchronized",
        "librepm",
        "POST",
        PREFIX + "/peer/projects/{suiteProjectId}/sync",
        200,
        headers=smoke.peer("librepm"),
        suite_id=sid,
    )
    assert {item["system"]: item["status"] for item in context["sync"]} == {
        "associago": "synced",
        "bandi": "synced",
    }
    assert (
        Decimal(context["financial"]["income"]) == 0
        and Decimal(context["financial"]["expense"]) == 0
    )
    assert context["preparation"]["localId"] == PREPARATION
    accounting_id = next(
        item["localId"]
        for item in context["instances"]
        if item["system"] == "associago"
    )
    with smoke.connect() as admin:
        for app in ("librepm", "associago"):
            assert (
                admin.execute(
                    sql.SQL("SELECT count(*) FROM {}").format(
                        sql.Identifier(app, "projects")
                    )
                ).fetchone()[0]
                == 1
            )
        assert (
            admin.execute("SELECT count(*) FROM associago.transactions").fetchone()[0]
            == 0
        )
        assert Decimal(
            admin.execute(
                "SELECT budget FROM associago.projects WHERE id=%s",
                (int(accounting_id),),
            ).fetchone()[0]
        ) == Decimal("1234.50")
    smoke.checks.append("real-project-count-budget-and-zero-fabricated-ledger")
    smoke.expect(
        "cloud-user-opens-shared-project",
        "associago",
        "GET",
        PREFIX + "/projects/{suiteProjectId}",
        200,
        headers=authorization,
        suite_id=sid,
    )
    smoke.expect(
        "bandi-opens-same-project",
        "bandi",
        "GET",
        PREFIX + "/projects/{suiteProjectId}",
        200,
        suite_id=sid,
    )
    for path, body in (
        (
            f"/api/v1/projects/{accounting_id}/assignments",
            {"personType": "VOLUNTEER", "personId": 1},
        ),
        (
            f"/api/v1/projects/{accounting_id}/shifts",
            {
                "volunteerId": 1,
                "startTime": "2026-10-30T10:00:00",
                "endTime": "2026-10-30T12:30:00",
                "hoursWorked": "2.5",
                "status": "COMPLETED",
            },
        ),
        (
            "/api/v1/finance/transactions",
            {
                "associationId": 1,
                "projectId": int(accounting_id),
                "date": "2026-10-30",
                "amount": "250.00",
                "type": "INCOME",
                "description": "Synthetic test grant",
            },
        ),
    ):
        status, _ = smoke.request("associago", "POST", path, body, authorization)
        assert status == 200, "Authenticated synthetic project operation failed"
        smoke.checks.append("jwt-project-write-" + path.rsplit("/", 1)[1])
    context = smoke.expect(
        "actual-ledger-and-hours-propagate",
        "librepm",
        "POST",
        PREFIX + "/peer/projects/{suiteProjectId}/sync",
        200,
        headers=smoke.peer("librepm"),
        suite_id=sid,
    )
    assert Decimal(context["financial"]["income"]) == Decimal("250.00")
    assert Decimal(context["workforce"]["completedHours"]) == Decimal("2.5")
    assert context["workforce"]["assignedCount"] == 1
    restricted = smoke.issuer.token("coordinatore-servizi")
    smoke.tokens.append(restricted)
    readonly = smoke.expect(
        "coordinator-financial-redaction",
        "associago",
        "GET",
        PREFIX + "/projects/{suiteProjectId}",
        200,
        headers={"Authorization": "Bearer " + restricted},
        suite_id=sid,
    )
    assert readonly["financial"] is None
    smoke.terminate(bandi_child)
    bandi_child.wait(timeout=8)
    partial = smoke.expect(
        "peer-outage-preserves-healthy-postgres-peer",
        "librepm",
        "POST",
        PREFIX + "/peer/projects/{suiteProjectId}/sync",
        200,
        headers=smoke.peer("librepm"),
        suite_id=sid,
    )
    assert {item["system"]: item["status"] for item in partial["sync"]} == {
        "associago": "synced",
        "bandi": "error",
    }
    assert partial["financial"] is not None
    smoke.persist_evidence()
    smoke.state.update(status="succeeded", stage="verified", checks=len(smoke.checks))
    smoke.publish()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for option in (
        "pg-state",
        "pg-connection",
        "pg-roles",
        "librepm-jar",
        "associago-jar",
        "bandi-repo",
    ):
        parser.add_argument("--" + option, type=Path, required=True)
    parser.add_argument(
        "--output",
        type=Path,
        default=Path(__file__).resolve().parents[2]
        / "integration/.artifacts/postgres-responses.json",
    )
    args = parser.parse_args()
    connection, roles = validate_local_sandbox(args)
    with tempfile.TemporaryDirectory(prefix="suite-postgres-smoke-") as directory:
        smoke = PostgresSmoke(Path(directory), args.output.resolve(), connection, roles)
        watchdog = threading.Timer(240, smoke.stop)
        watchdog.daemon = True
        watchdog.start()
        try:
            run(args, smoke)
            print(
                f"PASS: {len(smoke.checks)} PostgreSQL/native/JWT checks; sanitized evidence: {args.output}"
            )
        except Exception:
            smoke.state.update(status="failed", stage="failed")
            smoke.publish()
            smoke.persist_evidence()
            smoke.persist_diagnostics()
            raise
        finally:
            watchdog.cancel()
            smoke.stop()
        if smoke.state.get("cleanup_error"):
            raise RuntimeError(
                "Disposable database cleanup failed; see the sanitized state file"
            )


if __name__ == "__main__":
    main()
