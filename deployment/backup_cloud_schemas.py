"""Bounded PostgreSQL 17 custom backup of the three private cloud schemas.

Archives stay outside repositories. No retention deletion and no remote restore.
Credentials are mounted privately, never passed as a URL, argument or Docker env.
"""

from __future__ import annotations

import argparse
from contextlib import contextmanager
import csv
from dataclasses import dataclass, field
from datetime import UTC, datetime
import hashlib
import ipaddress
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import threading
import time
import uuid

SCHEMAS = ("associago", "librepm", "bandi_portal")
IMAGE = "postgres:17"


class BackupError(Exception):
    pass


def timestamp():
    return datetime.now(UTC).isoformat()


def sha256(path: Path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1048576), b""):
            digest.update(block)
    return digest.hexdigest()


def outside_repository(path: Path) -> Path:
    path = path.resolve()
    if any((parent / ".git").exists() for parent in (path, *path.parents)):
        raise BackupError("BACKUP_OUTSIDE_REPOSITORY_REQUIRED")
    return path


def protect_directory(path: Path):
    path.mkdir(parents=True, exist_ok=True)
    if os.name == "nt":
        result = subprocess.run(
            ["whoami", "/user", "/fo", "csv", "/nh"],
            capture_output=True,
            text=True,
            timeout=8,
            check=True,
        )
        sid = next(csv.reader(result.stdout.splitlines()))[1]
        if not re.fullmatch(r"S-1-[0-9-]+", sid):
            raise BackupError("BACKUP_OWNER_ID_UNVERIFIED")
        completed = subprocess.run(
            [
                "icacls",
                str(path),
                "/inheritance:r",
                "/grant:r",
                f"*{sid}:(OI)(CI)F",
                "*S-1-5-18:(OI)(CI)F",
            ],
            capture_output=True,
            timeout=8,
        )
        if completed.returncode:
            raise BackupError("BACKUP_PRIVATE_ACL_FAILED")
    else:
        path.chmod(0o700)


@dataclass(frozen=True)
class Connection:
    host: str
    port: int
    database: str
    user: str
    password: str = field(repr=False)
    sslmode: str = "verify-full"
    sslrootcert: str | None = None

    def validated(self):
        if (
            not re.fullmatch(r"[A-Za-z0-9.:-]+", self.host)
            or type(self.port) is not int
            or not 1 <= self.port <= 65535
        ):
            raise BackupError("BACKUP_CONNECTION_INVALID")
        if not re.fullmatch(
            r"[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}", self.database
        ) or not re.fullmatch(r"[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}", self.user):
            raise BackupError("BACKUP_CONNECTION_INVALID")
        for value in (self.database, self.user, self.password):
            if not value or any(char in value for char in "\r\n\x00"):
                raise BackupError("BACKUP_CONNECTION_INVALID")
        if self.user.split(".")[0] in {
            "associago_app",
            "librepm_app",
            "bandi_app",
            "bandi_portal_app",
        }:
            raise BackupError("BACKUP_RUNTIME_ROLE_FORBIDDEN")
        try:
            loopback = ipaddress.ip_address(self.host).is_loopback
        except ValueError:
            loopback = self.host == "localhost"
        if not loopback and (self.sslmode != "verify-full" or not self.sslrootcert):
            raise BackupError("BACKUP_VERIFIED_TLS_AND_CA_REQUIRED")
        if self.sslmode not in {"disable", "verify-full"}:
            raise BackupError("BACKUP_SSL_MODE_INVALID")
        if self.sslrootcert and not Path(self.sslrootcert).is_file():
            raise BackupError("BACKUP_CA_FILE_MISSING")
        return self

    @classmethod
    def load(cls, path: Path | None):
        if path:
            data = json.loads(path.read_text(encoding="utf-8"))
            if not isinstance(data, dict) or set(data) - {
                "host",
                "port",
                "database",
                "user",
                "password",
                "sslmode",
                "sslrootcert",
            }:
                raise BackupError("BACKUP_CONFIG_INVALID")
        else:
            data = {
                "host": os.environ.get("PGHOST", ""),
                "port": int(os.environ.get("PGPORT", "5432")),
                "database": os.environ.get("PGDATABASE", "postgres"),
                "user": os.environ.get("PGUSER", ""),
                "password": os.environ.get("PGPASSWORD", ""),
                "sslmode": os.environ.get("PGSSLMODE", "verify-full"),
                "sslrootcert": os.environ.get("PGSSLROOTCERT"),
            }
        return cls(**data).validated()


@contextmanager
def credentials(source: Connection):
    """Windows bind-mount modes are normalized inside a memory-only container path."""
    with tempfile.TemporaryDirectory(prefix="suite-backup-credentials-") as directory:
        root = Path(directory)
        protect_directory(root)

        def escaped(value):
            return str(value).replace("\\", "\\\\").replace(":", "\\:")

        (root / "pgpass").write_text(
            ":".join(
                escaped(value)
                for value in (
                    source.host,
                    source.port,
                    source.database,
                    source.user,
                    source.password,
                )
            )
            + "\n",
            encoding="utf-8",
        )
        (root / "pgpass").chmod(0o600)
        lines = {
            "PGHOST": source.host,
            "PGPORT": str(source.port),
            "PGDATABASE": source.database,
            "PGUSER": source.user,
            "PGSSLMODE": source.sslmode,
            "PGCONNECT_TIMEOUT": "8",
            "PGPASSFILE": "/run/secure/pgpass",
        }
        if source.sslrootcert:
            lines["PGSSLROOTCERT"] = "/run/ca.crt"
        (root / "connection.env").write_text(
            "".join(key + "=" + value + "\n" for key, value in lines.items()),
            encoding="utf-8",
        )
        yield root


def state_write(root: Path, state: dict):
    state["updated_at"] = timestamp()
    temporary = root / "state.json.tmp"
    for attempt in range(6):
        try:
            temporary.write_text(json.dumps(state, indent=2), encoding="utf-8")
            temporary.replace(root / "state.json")
            return
        except OSError:
            if attempt == 5:
                raise BackupError("BACKUP_STATE_WRITE_FAILED") from None
            time.sleep(0.05 * (attempt + 1))


def remove_owned_container(name: str, nonce: str):
    inspected = subprocess.run(
        ["docker", "inspect", name], capture_output=True, text=True, timeout=8
    )
    if inspected.returncode:
        return
    value = json.loads(inspected.stdout)[0]
    if value["Config"].get("Labels", {}).get("codex.backup.run") != nonce:
        raise BackupError("BACKUP_CONTAINER_ID_MISMATCH")
    subprocess.run(
        ["docker", "rm", "--force", value["Id"]],
        capture_output=True,
        timeout=10,
        check=True,
    )


def docker_command(
    root: Path,
    args: list[str],
    state: dict,
    *,
    seconds: int,
    source: Connection | None = None,
    network: str | None = None,
    output_name: str | None = None,
    deadline=None,
):
    nonce = uuid.uuid4().hex
    name = "codex-suite-backup-" + nonce
    command = [
        "docker",
        "run",
        "--rm",
        "--name",
        name,
        "--label",
        "codex.backup.run=" + nonce,
        "--mount",
        f"type=bind,source={root},target=/out",
        "--entrypoint",
        "sh",
    ]
    if network:
        if not re.fullmatch(r"container:[0-9a-f]{64}", network):
            raise BackupError("BACKUP_TEST_NETWORK_INVALID")
        command += ["--network", network]
    with (
        credentials(source)
        if source
        else tempfile.TemporaryDirectory(prefix="suite-backup-empty-") as private
    ):
        if source:
            private = Path(private)
            command += [
                "--mount",
                f"type=bind,source={private},target=/run/input,readonly",
                "--tmpfs",
                "/run/secure:rw,noexec,nosuid,size=1m",
                "--env-file",
                str(private / "connection.env"),
            ]
            if source.sslrootcert:
                command += [
                    "--mount",
                    f"type=bind,source={Path(source.sslrootcert).resolve()},target=/run/ca.crt,readonly",
                ]
        setup = "set -eu; umask 077; " + (
            "cp /run/input/pgpass /run/secure/pgpass; chmod 600 /run/secure/pgpass; "
            if source
            else ""
        )
        command += [
            IMAGE,
            "-c",
            setup + f'exec timeout --signal=TERM --kill-after=5s {seconds}s "$@"',
            "backup-client",
            *args,
        ]
        output_path = root / (output_name or "command.stdout")
        buffer = bytearray()

        def drain(stream):
            while block := stream.read(4096):
                if len(buffer) < 65536:
                    buffer.extend(block[: 65536 - len(buffer)])

        child = None
        try:
            with output_path.open("wb") as out:
                child = subprocess.Popen(
                    command,
                    stdout=out,
                    stderr=subprocess.PIPE,
                    creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0,
                )
                reader = threading.Thread(
                    target=drain, args=(child.stderr,), daemon=True
                )
                reader.start()
                state.update(child_pid=child.pid, container_name=name)
                until = min(time.monotonic() + seconds + 15, deadline or float("inf"))
                while child.poll() is None:
                    state_write(root, state)
                    if time.monotonic() >= until:
                        raise BackupError("BACKUP_CLIENT_TIMEOUT")
                    time.sleep(0.5)
                reader.join(timeout=5)
                if child.returncode:
                    state["diagnostic_sha256"] = hashlib.sha256(buffer).hexdigest()
                    raise BackupError("BACKUP_POSTGRES_CLIENT_FAILED")
        finally:
            if child and child.poll() is None:
                remove_owned_container(name, nonce)
                child.terminate()
                try:
                    child.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    child.kill()
                    child.wait(timeout=5)
            state.pop("child_pid", None)
            state.pop("container_name", None)
    return output_path


def run_backup(
    source: Connection,
    output: Path,
    *,
    schemas=SCHEMAS,
    snapshot=None,
    network=None,
    deadline=None,
):
    deadline = min(deadline or float("inf"), time.monotonic() + 210)
    source.validated()
    if (
        not schemas
        or len(set(schemas)) != len(schemas)
        or any(value not in SCHEMAS for value in schemas)
    ):
        raise BackupError("BACKUP_SCHEMA_NOT_ALLOWED")
    root = outside_repository(output) / (
        "backup-" + datetime.now(UTC).strftime("%Y%m%dT%H%M%SZ-") + uuid.uuid4().hex
    )
    root.mkdir(parents=True, exist_ok=False)
    protect_directory(root)
    state = {
        "status": "running",
        "stage": "dump",
        "runner_pid": os.getpid(),
        "schemas": list(schemas),
        "started_at": timestamp(),
    }
    try:
        state_write(root, state)
        args = [
            "pg_dump",
            "--format=custom",
            "--no-owner",
            "--no-acl",
            "--strict-names",
            "--lock-wait-timeout=8s",
            "--file=/out/archive.dump",
        ]
        args += ["--schema=" + value for value in schemas]
        if snapshot:
            if not re.fullmatch(r"[0-9A-Fa-f-]+", snapshot):
                raise BackupError("BACKUP_SNAPSHOT_INVALID")
            args.append("--snapshot=" + snapshot)
        docker_command(
            root,
            args,
            state,
            seconds=140,
            source=source,
            network=network,
            deadline=deadline,
        )
        archive = root / "archive.dump"
        if not archive.is_file() or archive.stat().st_size < 16:
            raise BackupError("BACKUP_ARCHIVE_MISSING")
        state.update(stage="verify_list")
        listing = docker_command(
            root,
            ["pg_restore", "--list", "/out/archive.dump"],
            state,
            seconds=20,
            output_name="archive.list",
            deadline=deadline,
        )
        manifest = {
            "status": "succeeded",
            "created_at": timestamp(),
            "schemas": list(schemas),
            "format": "postgresql-custom",
            "client_major": 17,
            "image": IMAGE,
            "archive": archive.name,
            "size_bytes": archive.stat().st_size,
            "sha256": sha256(archive),
            "list_sha256": sha256(listing),
            "restore_tested": False,
        }
        (root / "manifest.json").write_text(
            json.dumps(manifest, indent=2) + "\n", encoding="utf-8"
        )
        state.update(
            status="succeeded", stage="complete", archive_sha256=manifest["sha256"]
        )
        state_write(root, state)
        return root
    except BaseException as error:
        state.update(
            status="failed",
            stage="failed",
            error_code=str(error)
            if isinstance(error, BackupError)
            else "BACKUP_FAILED",
        )
        state_write(root, state)
        raise


def verified_backup_today(output: Path, schemas=SCHEMAS):
    """Scheduled catch-up may reuse only a complete, intact backup from this local day."""
    output = outside_repository(output)
    if not output.is_dir():
        return None
    today = datetime.now().astimezone().date()
    candidates = sorted(
        (
            path
            for path in output.iterdir()
            if path.is_dir()
            and not path.is_symlink()
            and re.fullmatch(r"backup-[0-9]{8}T[0-9]{6}Z-[0-9a-f]{32}", path.name)
        ),
        reverse=True,
    )[:50]
    for path in candidates:
        try:
            manifest = json.loads((path / "manifest.json").read_text(encoding="utf-8"))
            state = json.loads((path / "state.json").read_text(encoding="utf-8"))
            created = datetime.fromisoformat(manifest["created_at"])
            if (
                created.tzinfo is not None
                and created.astimezone().date() == today
                and manifest["status"] == "succeeded"
                and state["status"] == "succeeded"
                and manifest["schemas"] == list(schemas)
                and manifest["archive"] == "archive.dump"
                and manifest["sha256"] == sha256(path / "archive.dump")
                and manifest["list_sha256"] == sha256(path / "archive.list")
            ):
                return path
        except (OSError, ValueError, KeyError, TypeError):
            continue
    return None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--config",
        type=Path,
        help="Private JSON connection, otherwise standard PG* environment",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=Path(os.environ.get("LOCALAPPDATA", str(Path.home())))
        / "BandiSuite"
        / "backups",
    )
    parser.add_argument(
        "--schema",
        choices=SCHEMAS,
        action="append",
        help="Default: all three cloud schemas",
    )
    parser.add_argument(
        "--skip-if-today",
        action="store_true",
        help="Scheduled catch-up: skip only when today's archive and index pass digest checks",
    )
    args = parser.parse_args()
    try:
        source = Connection.load(args.config)
        schemas = args.schema or SCHEMAS
        if args.skip_if_today:
            existing = verified_backup_today(args.output_dir, schemas)
            if existing:
                print("Backup odierno già verificato: " + str(existing))
                return 0
        root = run_backup(
            source,
            args.output_dir,
            schemas=schemas,
        )
        print("Backup verificato con pg_restore --list: " + str(root))
        return 0
    except Exception as error:
        print(
            str(error) if isinstance(error, BackupError) else "BACKUP_FAILED",
            file=sys.stderr,
        )
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
