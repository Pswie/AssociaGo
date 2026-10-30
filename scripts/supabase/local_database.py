"""Bounded, supervised PostgreSQL/pgvector sandbox; never uses an existing DB.

Run as an independent process. Write STOP in the run directory to finish early.
The generated credentials and Docker env file stay in the ignored run directory.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import secrets
import subprocess
import time
import uuid


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run-directory", required=True, type=Path)
    parser.add_argument("--minutes", type=int, default=120)
    args = parser.parse_args()
    if not 1 <= args.minutes <= 180:
        parser.error("The sandbox lifetime must be between 1 and 180 minutes")
    directory = args.run_directory.resolve()
    directory.mkdir(parents=True, exist_ok=False)
    name = "codex-supabase-pg-" + uuid.uuid4().hex[:10]
    state = {"status": "running", "stage": "pull", "runner_pid": os.getpid(),
             "container_name": name, "container_id": None, "child_pid": None}
    password = secrets.token_urlsafe(32)
    env_file = directory / "postgres.env"
    env_file.write_text("POSTGRES_USER=suite_admin\nPOSTGRES_DB=suite_validation\nPOSTGRES_PASSWORD=" + password + "\n", encoding="utf-8")
    log = (directory / "supervisor.log").open("a", encoding="utf-8")
    container_id = None
    deadline = time.monotonic() + args.minutes * 60

    def publish():
        state["updated_at"] = datetime.now(timezone.utc).isoformat()
        temporary = directory / "state.json.tmp"
        temporary.write_text(json.dumps(state, indent=2), encoding="utf-8")
        temporary.replace(directory / "state.json")

    def command(arguments, timeout=30, capture=True):
        result = subprocess.run(["docker", *arguments], text=True, timeout=timeout,
                                capture_output=capture, stdout=None if capture else log,
                                stderr=None if capture else subprocess.STDOUT)
        if result.returncode:
            raise RuntimeError("Docker operation failed: " + arguments[0])
        return result.stdout.strip() if capture else ""

    try:
        publish()
        # Fixed major and extension versions; local-only image, not a deployment.
        command(["pull", "pgvector/pgvector:0.8.5-pg17"], timeout=180, capture=False)
        state["stage"] = "starting"
        publish()
        container_id = command(["create", "--name", name, "--label", "codex.task=supabase-conversion",
                                "--restart=no", "--memory=768m", "--publish", "127.0.0.1::5432",
                                "--env-file", str(env_file), "pgvector/pgvector:0.8.5-pg17"])
        state["container_id"] = container_id
        command(["start", container_id])
        ready_by = time.monotonic() + 60
        while True:
            result = subprocess.run(["docker", "exec", container_id, "pg_isready", "-U", "suite_admin", "-d", "suite_validation"], capture_output=True, timeout=10)
            if result.returncode == 0:
                break
            if time.monotonic() >= ready_by:
                raise TimeoutError("PostgreSQL did not become ready")
            publish()
            time.sleep(1)
        inspection = json.loads(command(["inspect", container_id]))[0]
        port = int(inspection["NetworkSettings"]["Ports"]["5432/tcp"][0]["HostPort"])
        credentials = {"host": "127.0.0.1", "port": port, "database": "suite_validation", "user": "suite_admin", "password": password}
        (directory / "connection.json").write_text(json.dumps(credentials, indent=2), encoding="utf-8")
        state.update(stage="ready", child_pid=inspection["State"]["Pid"], port=port,
                     connection_file=str(directory / "connection.json"))
        publish()
        while not (directory / "STOP").exists():
            if time.monotonic() >= deadline:
                raise TimeoutError("Sandbox lifetime reached; stopped automatically")
            status = json.loads(command(["inspect", container_id]))[0]
            if not status["State"]["Running"]:
                raise RuntimeError("The sandbox database exited unexpectedly")
            publish()
            time.sleep(5)
        state.update(status="succeeded", stage="stopped")
    except Exception as error:
        state.update(status="failed", stage="failed", error=type(error).__name__ + ": " + str(error))
    finally:
        if container_id:
            # Only the immutable ID created by this process can be removed.
            try:
                command(["rm", "--force", "--volumes", container_id], timeout=30)
            except Exception as error:
                state.update(status="failed", cleanup_error=type(error).__name__)
        publish()
        log.close()


if __name__ == "__main__":
    main()
