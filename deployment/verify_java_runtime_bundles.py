"""Bounded local proof of uppercase SPRING_APPLICATION_JSON on frozen Java artifacts.

No cloud credentials/configuration are read. PostgreSQL17, passwords and OIDC are
synthetic; only the digest-pinned public image names identify release artifacts.
"""

from __future__ import annotations

import argparse
from datetime import UTC, datetime
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess
import sys
import threading
import time
from urllib.error import HTTPError, URLError
from urllib.request import ProxyHandler, Request, build_opener
import uuid

from backup_cloud_schemas import protect_directory
import migrate_owner_once
import psycopg
from psycopg import sql

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts" / "supabase"))
from run_postgres_smoke import Issuer  # noqa: E402
from run_native_smoke import ORG, clean_environment, port  # noqa: E402


class Proof:
    def __init__(self, directory):
        self.root = directory.resolve()
        if not self.root.is_relative_to((ROOT / "deployment" / ".artifacts").resolve()):
            raise ValueError("LOCAL_PROOF_ARTIFACT_DIRECTORY_REQUIRED")
        self.root.mkdir(parents=True, exist_ok=False)
        protect_directory(self.root)
        self.deadline = time.monotonic() + 175
        self.children, self.logs = [], []
        self.issuer, self.container = None, None
        self.nonce = uuid.uuid4().hex
        self.container_name = "codex-java-bundles-" + self.nonce
        self.password = secrets.token_urlsafe(40)
        self.roles = {
            app + suffix: secrets.token_urlsafe(40)
            for app in ("associago", "librepm")
            for suffix in ("_owner", "_app")
        }
        self.keys = {name: secrets.token_urlsafe(40) for name in ("associago", "bandi")}
        self.db_port = None
        self.state = {
            "status": "running",
            "stage": "initializing",
            "runner_pid": os.getpid(),
            "checks": [],
            "artifacts": {},
            "child_pids": [],
            "container_removed": False,
            "remote_database_connections": False,
        }
        self.lock = threading.Lock()
        self.closed = False
        # Reserve at least 45 seconds for child, issuer and Docker cleanup.
        self.timer = threading.Timer(185, self.close)
        self.timer.daemon = True
        self.timer.start()
        self.publish()

    def publish(self):
        self.state["updated_at"] = datetime.now(UTC).isoformat()
        temporary = self.root / "state.json.tmp"
        temporary.write_text(json.dumps(self.state, indent=2) + "\n", encoding="utf-8")
        temporary.replace(self.root / "state.json")

    def command(self, arguments, *, timeout=30):
        remaining = self.deadline - time.monotonic()
        if remaining <= 0:
            raise TimeoutError("LOCAL_PROOF_DEADLINE")
        result = subprocess.run(
            arguments,
            capture_output=True,
            text=True,
            timeout=min(timeout, remaining),
            creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0,
        )
        if result.returncode:
            raise RuntimeError("LOCAL_COMMAND_FAILED")
        return result.stdout

    def check(self, name, condition):
        if not condition:
            raise AssertionError(name)
        self.state["checks"].append(name)
        self.publish()

    def connect(self, **kwargs):
        return psycopg.connect(
            host="127.0.0.1",
            hostaddr="127.0.0.1",
            port=self.db_port,
            dbname="suite_bundles",
            user="suite_admin",
            password=self.password,
            connect_timeout=4,
            options="-c statement_timeout=5000 -c lock_timeout=1000",
            **kwargs,
        )

    def postgres(self):
        self.state["stage"] = "postgres17"
        self.publish()
        environment = self.root / "postgres.env"
        environment.write_text(
            "POSTGRES_DB=suite_bundles\nPOSTGRES_USER=suite_admin\nPOSTGRES_PASSWORD="
            + self.password
            + "\n",
            encoding="utf-8",
        )
        self.container = self.command(
            [
                "docker",
                "create",
                "--pull=never",
                "--name",
                self.container_name,
                "--label",
                "codex.java-bundles=" + self.nonce,
                "--restart=no",
                "--memory=512m",
                "--publish",
                "127.0.0.1::5432",
                "--env-file",
                str(environment),
                "postgres:17",
            ]
        ).strip()
        if not re.fullmatch(r"[0-9a-f]{64}", self.container):
            raise RuntimeError("LOCAL_CONTAINER_ID_INVALID")
        self.command(["docker", "start", self.container])
        inspection = json.loads(self.command(["docker", "inspect", self.container]))[0]
        self.db_port = int(
            inspection["NetworkSettings"]["Ports"]["5432/tcp"][0]["HostPort"]
        )
        until = time.monotonic() + 25
        while time.monotonic() < until:
            try:
                with self.connect() as connection:
                    self.check(
                        "postgres-major17",
                        connection.execute("SHOW server_version_num")
                        .fetchone()[0]
                        .startswith("17"),
                    )
                break
            except psycopg.OperationalError:
                time.sleep(0.2)
        else:
            raise TimeoutError("LOCAL_POSTGRES_NOT_READY")
        with self.connect() as connection:
            connection.execute("REVOKE CREATE ON SCHEMA public FROM PUBLIC")
            for app in ("associago", "librepm"):
                owner, runtime, schema = map(
                    sql.Identifier, (app + "_owner", app + "_app", app)
                )
                for role in (app + "_owner", app + "_app"):
                    connection.execute(
                        sql.SQL("CREATE ROLE {} LOGIN PASSWORD {}").format(
                            sql.Identifier(role), sql.Literal(self.roles[role])
                        )
                    )
                connection.execute(
                    sql.SQL("CREATE SCHEMA {} AUTHORIZATION {}").format(schema, owner)
                )
                connection.execute(
                    sql.SQL("REVOKE ALL ON SCHEMA {} FROM PUBLIC").format(schema)
                )
                connection.execute(
                    sql.SQL("GRANT USAGE ON SCHEMA {} TO {}").format(schema, runtime)
                )
                connection.execute(
                    sql.SQL(
                        "ALTER DEFAULT PRIVILEGES FOR ROLE {} IN SCHEMA {} GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO {}"
                    ).format(owner, schema, runtime)
                )
                connection.execute(
                    sql.SQL(
                        "ALTER DEFAULT PRIVILEGES FOR ROLE {} IN SCHEMA {} GRANT USAGE, SELECT ON SEQUENCES TO {}"
                    ).format(owner, schema, runtime)
                )
                connection.execute(
                    sql.SQL(
                        "ALTER DEFAULT PRIVILEGES FOR ROLE {} REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC"
                    ).format(owner)
                )

    def migrate(self, app, image):
        self.state["stage"] = "owner-migration-" + app
        self.publish()
        migrate_owner_once.command = self.command
        data = {
            "jdbcUrl": f"jdbc:postgresql://127.0.0.1:{self.db_port}/suite_bundles?currentSchema={app}",
            "ownerUser": app + "_owner",
            "ownerPassword": self.roles[app + "_owner"],
        }
        result = migrate_owner_once.java_migration(app, data, image, self.root)
        self.check(app + "-owner-migrations-executed", result["migrationsExecuted"] > 0)
        with self.connect() as connection:
            connection.execute(
                sql.SQL("REVOKE ALL ON {}.flyway_schema_history FROM {}").format(
                    sql.Identifier(app), sql.Identifier(app + "_app")
                )
            )
            history = connection.execute(
                sql.SQL(
                    "SELECT version, checksum, success FROM {}.flyway_schema_history ORDER BY installed_rank"
                ).format(sql.Identifier(app))
            ).fetchall()
            self.check(
                app + "-runtime-no-history-read",
                not connection.execute(
                    "SELECT has_table_privilege(%s,%s,'SELECT')",
                    (app + "_app", app + ".flyway_schema_history"),
                ).fetchone()[0],
            )
        return history

    def start(self, app, jar):
        data = self.root / (app + "-data")
        data.mkdir()
        secret = {
            "SPRING_DATASOURCE_URL": f"jdbc:postgresql://127.0.0.1:{self.db_port}/suite_bundles?currentSchema={app}",
            "SPRING_DATASOURCE_USERNAME": app + "_app",
            "SPRING_DATASOURCE_PASSWORD": self.roles[app + "_app"],
            "SUITE_ASSOCIAGO_KEY": self.keys["associago"],
        }
        if app == "librepm":
            secret["SUITE_BANDI_KEY"] = self.keys["bandi"]
        env = {
            **clean_environment(),
            "SPRING_APPLICATION_JSON": json.dumps(secret),
            "SPRING_PROFILES_ACTIVE": "cloud-postgres",
            "SPRING_FLYWAY_ENABLED": "false",
            "SPRING_JPA_HIBERNATE_DDL_AUTO": "validate",
            "DB_POOL_MAX": "3",
            "DB_POOL_MIN": "0",
            "CLERK_ISSUER_URI": self.issuer.url,
            "CLERK_AUDIENCE": "suite-postgres-smoke",
            "CORS_ALLOWED_ORIGINS": "https://fixture.example.invalid",
            "SUITE_ENABLED": "true",
            "SUITE_ORGANIZATION_ID": ORG,
            "SUITE_ASSOCIATION_ID": "1",
            "SUITE_SYNC_INITIAL_DELAY_MS": "3600000",
            "SUITE_SYNC_INTERVAL_MS": "3600000",
            "LIBREPM_DATA_PATH": str(data),
        }
        self.check(app + "-secrets-only-in-json", all(key not in env for key in secret))
        http_port = port()
        log = (self.root / (app + ".log")).open("w", encoding="utf-8")
        self.logs.append(log)
        child = subprocess.Popen(
            [
                shutil.which("java"),
                f"-D{app}.data.path={data}",
                "-jar",
                str(jar),
                "--server.address=127.0.0.1",
                "--server.port=" + str(http_port),
                "--logging.level.root=WARN",
                "--logging.level.org.hibernate.SQL=WARN",
            ],
            cwd=self.root,
            env=env,
            stdout=log,
            stderr=subprocess.STDOUT,
            creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0,
        )
        self.children.append(child)
        self.state["child_pids"].append(child.pid)
        self.publish()
        return "http://127.0.0.1:" + str(http_port), child

    def request(self, origin, path, headers=None):
        opener = build_opener(ProxyHandler({}))
        request = Request(
            origin + path, headers={"Accept": "application/json", **(headers or {})}
        )
        try:
            response = opener.open(request, timeout=3)
        except HTTPError as error:
            response = error
        with response:
            payload = response.read(131073)
            if len(payload) > 131072:
                raise RuntimeError("LOCAL_RESPONSE_TOO_LARGE")
            return response.status, json.loads(payload) if payload else None

    def verify(self, app, origin, child, history):
        self.state["stage"] = "runtime-bundle-" + app
        self.publish()
        until = min(self.deadline, time.monotonic() + 50)
        while time.monotonic() < until:
            if child.poll() is not None:
                raise RuntimeError(app + "-runtime-exited")
            try:
                status, _ = self.request(origin, "/api/me")
                if status == 401:
                    break
            except (URLError, TimeoutError, ConnectionError, json.JSONDecodeError):
                pass
            time.sleep(0.2)
        else:
            raise TimeoutError(app + "-readiness-timeout")
        self.check(app + "-unauthenticated-me401", status == 401)
        status, payload = self.request(origin, "/actuator/health")
        self.check(app + "-health200", status == 200 and payload.get("status") == "UP")
        headers = {
            "X-Suite-Peer": "librepm" if app == "associago" else "associago",
            "X-Suite-Key": self.keys["associago"],
            "X-Suite-Organization": ORG,
        }
        path = "/api/integrations/v1/peer/projects/" + str(uuid.uuid4())
        status, _ = self.request(origin, path, headers)
        self.check(app + "-json-peer-key-authenticated404", status == 404)
        status, _ = self.request(
            origin, path, {**headers, "X-Suite-Key": secrets.token_urlsafe(40)}
        )
        self.check(app + "-wrong-peer-key401", status == 401)
        with self.connect() as connection:
            users = {
                row[0]
                for row in connection.execute(
                    "SELECT usename FROM pg_stat_activity WHERE datname=current_database() AND backend_type='client backend'"
                )
            }
            self.check(app + "-runtime-app-role-connected", app + "_app" in users)
            self.check(
                app + "-owner-not-runtime-connected", app + "_owner" not in users
            )
            current = connection.execute(
                sql.SQL(
                    "SELECT version, checksum, success FROM {}.flyway_schema_history ORDER BY installed_rank"
                ).format(sql.Identifier(app))
            ).fetchall()
            self.check(app + "-flyway-history-unchanged", current == history)

    def close(self):
        with self.lock:
            if self.closed:
                return
            self.closed = True
            for child in reversed(self.children):
                if child.poll() is None:
                    child.terminate()
            for child in reversed(self.children):
                try:
                    child.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    child.kill()
                    child.wait(timeout=5)
            if self.issuer:
                self.issuer.close()
            for log in self.logs:
                log.close()
            candidate = self.container or self.container_name
            result = subprocess.run(
                ["docker", "inspect", candidate],
                capture_output=True,
                text=True,
                timeout=8,
            )
            if result.returncode == 0:
                metadata = json.loads(result.stdout)[0]
                if (
                    metadata["Config"].get("Labels", {}).get("codex.java-bundles")
                    != self.nonce
                ):
                    raise RuntimeError("LOCAL_CLEANUP_ID_MISMATCH")
                removed = subprocess.run(
                    ["docker", "rm", "--force", "--volumes", metadata["Id"]],
                    capture_output=True,
                    timeout=12,
                )
                self.state["container_removed"] = removed.returncode == 0
            if not self.state["container_removed"]:
                self.state.update(
                    status="failed", error_code="LOCAL_CONTAINER_CLEANUP_UNVERIFIED"
                )
            self.state["child_pids"] = []
            if self.state["status"] == "running":
                self.state.update(
                    status="failed", error_code="LOCAL_PROOF_DEADLINE_OR_EXIT"
                )
            self.state["stage"] = "cleaned"
            self.publish()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--directory", type=Path, required=True)
    for app in ("associago", "librepm"):
        parser.add_argument(
            "--" + app + "-build",
            type=Path,
            default=ROOT / "deployment" / ".artifacts" / ("build-" + app + "-20261030"),
            help="Frozen image build directory containing context/app.jar and state.json",
        )
    args = parser.parse_args()
    # No private rollout files: public immutable artifact identities only.
    public = json.loads(
        (ROOT / "deployment" / "suite-public-settings.json").read_text()
    )
    proof = Proof(args.directory)
    try:
        frozen = {}
        for app in ("associago", "librepm"):
            build = getattr(args, app + "_build").resolve()
            state = json.loads((build / "state.json").read_text())
            jar = build / "context" / "app.jar"
            image = public["google"]["preparedImages"][app]
            digest = hashlib.sha256(jar.read_bytes()).hexdigest()
            proof.check(app + "-frozen-jar-sha", digest == state["jar_sha256"])
            proof.check(
                app + "-frozen-image-digest", image in state["repository_digests"]
            )
            proof.state["artifacts"][app] = {"jar_sha256": digest, "image": image}
            target = proof.root / (app + ".jar")
            shutil.copyfile(jar, target)
            frozen[app] = (target, image)
        proof.postgres()
        histories = {
            app: proof.migrate(app, image) for app, (_, image) in frozen.items()
        }
        proof.issuer = Issuer()
        runtimes = {app: proof.start(app, jar) for app, (jar, _) in frozen.items()}
        for app, (origin, child) in runtimes.items():
            proof.verify(app, origin, child, histories[app])
        proof.state.update(
            status="succeeded",
            stage="verified",
            uppercase_json=True,
            runtime_flyway=False,
        )
    except Exception as error:
        safe = (
            str(error)
            if isinstance(error, (AssertionError, TimeoutError))
            else type(error).__name__
        )
        proof.state.update(status="failed", error_code=safe)
    finally:
        proof.timer.cancel()
        proof.close()
    print(
        json.dumps(
            {
                "status": proof.state["status"],
                "checks": len(proof.state["checks"]),
                "evidence": str(proof.root / "state.json"),
                "container_removed": proof.state["container_removed"],
                "error_code": proof.state.get("error_code"),
            }
        )
    )
    return (
        0
        if proof.state["status"] == "succeeded" and proof.state["container_removed"]
        else 1
    )


if __name__ == "__main__":
    raise SystemExit(main())
