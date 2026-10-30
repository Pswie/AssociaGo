"""Read-only PC release check; never starts workers, tasks, jobs or remote requests."""

from __future__ import annotations

import argparse
from datetime import UTC, datetime
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile

from backup_cloud_schemas import Connection, outside_repository, protect_directory

TASKS = ("Bandi Portal Local Node", "Bandi Suite Cloud Backup")
CRON_NAMES = ("COLLAB_CRON_KEY", "CRONJOB_API_KEY")
FILES = (
    "scripts/portal_node.py",
    "scripts/portal_node_windows.ps1",
    "apps/api/collaboration/local_node.py",
    "apps/api/collaboration/local_node_executor.py",
    "apps/api/collaboration/local_node_supervisor.py",
)


def run(command, *, seconds=12):
    """Capture diagnostics privately; callers publish only allowlisted fields/codes."""
    try:
        result = subprocess.run(
            command,
            capture_output=True,
            text=True,
            timeout=seconds,
            creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0,
        )
        return result.returncode, result.stdout
    except (OSError, subprocess.TimeoutExpired):
        return 1, ""


def task_status():
    if os.name != "nt":
        return {"available": False, "reason": "WINDOWS_REQUIRED"}
    # Static command only. Do not inspect task arguments, which can contain secrets.
    command = (
        "$items=@(); foreach($name in @('Bandi Portal Local Node','Bandi Suite Cloud Backup')) {"
        "$task=Get-ScheduledTask -TaskName $name -ErrorAction SilentlyContinue;"
        "if($task){$items += @{name=$name;exists=$true;state=[string]$task.State}}"
        "else{$items += @{name=$name;exists=$false;state='absent'}}};"
        "ConvertTo-Json -InputObject @($items) -Compress"
    )
    code, output = run(
        ["powershell.exe", "-NoProfile", "-NonInteractive", "-Command", command]
    )
    try:
        items = json.loads(output)
        if code or not isinstance(items, list) or len(items) != len(TASKS):
            raise ValueError
        safe = []
        for item in items:
            if item["name"] not in TASKS or type(item["exists"]) is not bool:
                raise ValueError
            if item["state"] not in {
                "absent",
                "Disabled",
                "Ready",
                "Running",
                "Unknown",
                "Queued",
            }:
                raise ValueError
            safe.append({key: item[key] for key in ("name", "exists", "state")})
        if len({item["name"] for item in safe}) != len(TASKS):
            raise ValueError
        return {"available": True, "tasks": safe}
    except (ValueError, TypeError, KeyError):
        return {"available": False, "reason": "TASK_QUERY_FAILED"}


def docker_status():
    code, output = run(
        [
            "docker",
            "inspect",
            "--format",
            "{{.State.Running}} {{if .State.Health}}{{.State.Health.Status}}{{end}}",
            "ets-intelligence-db-1",
        ]
    )
    fields = output.strip().split()
    if code or len(fields) != 2 or fields[0] not in {"true", "false"}:
        return {"available": False, "reason": "ARCHIVE_CONTAINER_UNAVAILABLE"}
    if fields[1] not in {"healthy", "unhealthy", "starting"}:
        return {"available": False, "reason": "ARCHIVE_HEALTH_UNVERIFIED"}
    return {"available": True, "running": fields[0] == "true", "health": fields[1]}


def declared_names(path):
    """Only recognize explicit names; never discover secret stores or expand dotenv code."""
    if path is None or not path.is_file():
        return []
    if path.stat().st_size > 1048576:
        return []
    data = path.read_text(encoding="utf-8-sig")
    names = set(
        re.findall(r"^\s*(?:export\s+)?([A-Z][A-Z0-9_]*)\s*=", data, re.MULTILINE)
    )
    return sorted(names.intersection(CRON_NAMES))


def archive_probe(repo: Path, env_file: Path):
    """Child process only: imports native config and executes bounded SELECTs on loopback."""
    sys.path.insert(0, str(repo))
    import logging

    logging.disable(logging.CRITICAL)
    from sqlalchemy.engine import make_url

    from apps.api.collaboration.local_node import safe_gateway
    from apps.api.config import Settings

    settings = Settings(_env_file=env_file)
    root = (repo / settings.data_root).resolve()
    state_root = (
        repo / (settings.collab_node_state_root or root / "private" / "portal-node")
    ).resolve()
    result = {
        "configuration_valid": True,
        "local_mode": settings.bandi_mode == "local",
        "cloud_schema_excluded": settings.database_schema != "bandi_portal",
        "data_root": str(root),
        "data_root_exists": root.is_dir(),
        "state_root": str(state_root),
        "state_root_exists": state_root.is_dir(),
        "generation_disabled": not settings.collab_node_allow_generation,
        "node_id_present": bool(settings.collab_node_id),
        "node_key_present": bool(settings.collab_node_key),
        "gateway_present": bool(settings.collab_gateway_url),
        "gateway_valid": False,
        "database_healthy": False,
        "database_loopback": False,
        "catalog_tables_accessible": False,
    }
    if settings.collab_gateway_url:
        try:
            safe_gateway(settings.collab_gateway_url)
            result["gateway_valid"] = True
        except Exception:
            pass
    result["node_key_valid"] = bool(
        settings.collab_node_key
        and len(settings.collab_node_key.get_secret_value()) >= 32
        and settings.collab_node_id
        and len(settings.collab_node_id) <= 128
        and all(
            33 <= ord(char) <= 126
            for char in settings.collab_node_key.get_secret_value()
        )
        and all(33 <= ord(char) <= 126 for char in settings.collab_node_id)
    )
    if state_root.is_dir():
        try:
            state = json.loads((state_root / "state.json").read_text(encoding="utf-8"))
            # Do not publish job IDs, paths from receipts or arbitrary state strings.
            if state.get("status") in {"running", "succeeded", "failed", "stopped"}:
                result["recorded_status"] = state["status"]
        except (OSError, ValueError, TypeError, AttributeError):
            pass
    if (
        not settings.database_url
        or not result["local_mode"]
        or not result["cloud_schema_excluded"]
    ):
        return result
    url = make_url(settings.database_url)
    host = url.host
    if host not in {"127.0.0.1", "::1", "localhost"} or not url.drivername.startswith(
        "postgresql"
    ):
        result["database_reason"] = "LOOPBACK_POSTGRES_REQUIRED"
        return result
    result["database_loopback"] = True
    import psycopg
    from psycopg import sql

    try:
        with psycopg.connect(
            host=host,
            hostaddr="127.0.0.1" if host == "localhost" else host,
            port=url.port or 5432,
            dbname=url.database,
            user=url.username,
            password=url.password,
            connect_timeout=4,
            options="-c default_transaction_read_only=on -c statement_timeout=2000 -c lock_timeout=1000",
        ) as connection:
            connection.execute("SET TRANSACTION READ ONLY")
            result["database_healthy"] = connection.execute("SELECT 1").fetchone() == (
                1,
            )
            schema = (
                settings.database_schema
                or connection.execute("SELECT current_schema()").fetchone()[0]
            )
            for table in ("funding_calls", "projects", "project_preparations"):
                # LIMIT 0 checks visibility and SELECT permission without loading user rows.
                connection.execute(
                    sql.SQL("SELECT 1 FROM {}.{} LIMIT 0").format(
                        sql.Identifier(schema), sql.Identifier(table)
                    )
                )
            result["catalog_tables_accessible"] = True
    except Exception:
        result["database_reason"] = "ARCHIVE_READ_CHECK_FAILED"
    return result


def collect(repo, node_env, backup_config, cron_env, cron_key_file):
    python = repo / ".venv" / "Scripts" / "python.exe"
    node_present = node_env.is_file()
    archive_env = node_env if node_present else repo / ".env"
    report = {
        "checked_at": datetime.now(UTC).isoformat(),
        "read_only": True,
        "remote_requests": False,
        "daemon_started": False,
        "tasks_changed": False,
        "files_present": {name: (repo / name).is_file() for name in FILES},
        "python_present": python.is_file(),
        "node_env": str(node_env),
        "node_env_present": node_present,
        "archive_env": str(archive_env),
        "docker_archive": docker_status(),
        "windows_tasks": task_status(),
        "cron": {
            "environment_names_present": [
                name for name in CRON_NAMES if os.environ.get(name)
            ],
            "declared_file": str(cron_env) if cron_env else None,
            "declared_names": declared_names(cron_env),
            "cron_key_file": str(cron_key_file) if cron_key_file else None,
            "cron_key_file_present": bool(cron_key_file and cron_key_file.is_file()),
            "account_checked": False,
        },
        "backup": {
            "config_file": str(backup_config),
            "config_present": backup_config.is_file(),
        },
    }
    if python.is_file() and archive_env.is_file():
        code, output = run(
            [
                str(python),
                "-B",
                str(Path(__file__).resolve()),
                "--_archive-probe",
                str(repo),
                str(archive_env),
            ],
            seconds=20,
        )
        try:
            probe = json.loads(output)
            allowed = {
                "configuration_valid",
                "local_mode",
                "cloud_schema_excluded",
                "data_root",
                "data_root_exists",
                "state_root",
                "state_root_exists",
                "generation_disabled",
                "node_id_present",
                "node_key_present",
                "gateway_present",
                "gateway_valid",
                "database_healthy",
                "database_loopback",
                "catalog_tables_accessible",
                "node_key_valid",
                "recorded_status",
                "database_reason",
            }
            if code or not isinstance(probe, dict) or set(probe) - allowed:
                raise ValueError
            report["archive"] = probe
        except (ValueError, TypeError):
            report["archive"] = {
                "configuration_valid": False,
                "reason": "NATIVE_CONFIG_CHECK_FAILED",
            }
    else:
        report["archive"] = {
            "configuration_valid": False,
            "reason": "ARCHIVE_CONFIG_OR_PYTHON_MISSING",
        }
    if backup_config.is_file():
        try:
            Connection.load(backup_config)
            report["backup"]["config_valid"] = True
        except Exception:
            report["backup"]["config_valid"] = False
    archive = report["archive"]
    report["catalog_node_ready"] = (
        node_present
        and all(
            archive.get(key) is True
            for key in (
                "configuration_valid",
                "local_mode",
                "cloud_schema_excluded",
                "data_root_exists",
                "node_key_valid",
                "gateway_valid",
                "database_healthy",
                "database_loopback",
                "catalog_tables_accessible",
            )
        )
        and all(report["files_present"].values())
    )
    report["activation_verified"] = False
    return report


def main(argv=None):
    if len(sys.argv) == 4 and sys.argv[1] == "--_archive-probe":
        # This child mode is intentionally not exposed as an activation command.
        try:
            result = archive_probe(Path(sys.argv[2]), Path(sys.argv[3]))
            print(json.dumps(result))
            return 0
        except Exception:
            print(
                json.dumps(
                    {
                        "configuration_valid": False,
                        "reason": "NATIVE_CONFIG_CHECK_FAILED",
                    }
                )
            )
            return 1
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--bandi-repo", type=Path, default=Path(__file__).resolve().parents[2] / "Bandi"
    )
    parser.add_argument("--node-env", type=Path)
    parser.add_argument(
        "--backup-config",
        type=Path,
        default=Path(os.environ.get("LOCALAPPDATA", str(Path.home())))
        / "BandiSuite"
        / "private"
        / "backup.local.json",
    )
    parser.add_argument("--cron-env-file", type=Path)
    parser.add_argument("--cron-key-file", type=Path)
    parser.add_argument(
        "--receipt", type=Path, help="Optional sanitized JSON outside Git repositories"
    )
    args = parser.parse_args(argv)
    try:
        repo = args.bandi_repo.resolve()
        report = collect(
            repo,
            (args.node_env or repo / ".env.portal-node.local").resolve(),
            args.backup_config.resolve(),
            args.cron_env_file,
            args.cron_key_file,
        )
        encoded = json.dumps(report, indent=2) + "\n"
        if args.receipt:
            receipt = outside_repository(args.receipt)
            protect_directory(receipt.parent)
            descriptor, temporary = tempfile.mkstemp(
                dir=receipt.parent, prefix="readiness-", suffix=".tmp"
            )
            with os.fdopen(descriptor, "w", encoding="utf-8") as stream:
                stream.write(encoded)
            os.replace(temporary, receipt)
        print(encoded, end="")
        return 0 if report["catalog_node_ready"] else 2
    except Exception:
        print("READINESS_CHECK_FAILED", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
