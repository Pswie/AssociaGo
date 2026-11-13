"""One-shot Windows release supervision bound to the actual Python PID/birth.

This operator helper never enters a Docker context. It reuses the PC's tested
held-process utility; only its own helper tree can be stopped at the deadline.
Remote writes are never replayed, including an ambiguous gcloud timeout.
"""

from __future__ import annotations

import json
import os
from pathlib import Path
import subprocess
import sys
import time
from uuid import uuid4

from smoke_association_gateway_image import birth, save


def process_owner(pid):
    repository = Path(__file__).resolve().parents[2] / "Bandi"
    if str(repository) not in sys.path:
        sys.path.insert(0, str(repository))
    from apps.api.collaboration.process_activity import ProcessOwner

    return ProcessOwner(pid)


def worker_identity(path):
    launch = os.environ.get("ADF_GATEWAY_RELEASE_LAUNCH_ID")
    supervisor = os.environ.get("ADF_GATEWAY_RELEASE_SUPERVISOR_PID")
    state = json.loads(path.read_text())
    if not launch or not supervisor or state.get("launch_id") != launch:
        raise RuntimeError("SUPERVISED_RELEASE_REQUIRED")
    owner = process_owner(os.getpid())
    try:
        save(
            path,
            runner_pid=os.getpid(),
            runner_birth=birth(os.getpid()),
            runner_process_start_ms=owner.birth_ms,
            parent_pid=os.getppid(),
            supervisor_pid=int(supervisor),
            launch_id=launch,
        )
    finally:
        owner.close()


def supervise_release(path, script, limit_seconds):
    if os.name != "nt":
        raise RuntimeError("WINDOWS_RELEASE_SUPERVISOR_REQUIRED")
    launch = uuid4().hex
    with (path.parent / "supervisor.claim.json").open("x", encoding="utf-8") as stream:
        json.dump(
            {"launch_id": launch, "supervisor_pid": os.getpid(), "retry": False}, stream
        )
        stream.flush()
        os.fsync(stream.fileno())
    save(path, launch_id=launch, supervisor_pid=os.getpid())
    environment = {
        **os.environ,
        "ADF_GATEWAY_RELEASE_LAUNCH_ID": launch,
        "ADF_GATEWAY_RELEASE_SUPERVISOR_PID": str(os.getpid()),
    }
    child = subprocess.Popen(
        [sys.executable, str(script), "--runner", str(path)],
        env=environment,
        creationflags=subprocess.CREATE_NO_WINDOW,
    )
    launcher_birth = birth(child.pid)
    owner = None
    actual_pid = None
    deadline = time.monotonic() + limit_seconds
    timed_out = False
    try:
        while child.poll() is None or (owner is not None and owner.alive()):
            try:
                state = json.loads(path.read_text())
            except (OSError, ValueError):
                state = {}
            candidate_pid = state.get("runner_pid")
            bound = (
                state.get("launch_id") == launch
                and state.get("supervisor_pid") == os.getpid()
                and type(candidate_pid) is int
                and (candidate_pid == child.pid or state.get("parent_pid") == child.pid)
            )
            if owner is None and bound:
                candidate = process_owner(candidate_pid)
                if candidate.birth_ms != state.get("runner_process_start_ms"):
                    candidate.close()
                    raise RuntimeError("RELEASE_RUNNER_IDENTITY_CHANGED")
                owner, actual_pid = candidate, candidate_pid
            save(
                path.parent / "watchdog.json",
                status="running",
                stage="monitoring-actual-runner",
                launch_id=launch,
                launcher_pid=child.pid,
                launcher_birth=launcher_birth,
                runner_pid=actual_pid,
                runner_process_start_ms=owner.birth_ms if owner else None,
                runner_bound=owner is not None,
                supervisor_pid=os.getpid(),
            )
            if time.monotonic() > deadline:
                timed_out = True
                break
            time.sleep(1)
        if timed_out:
            # The launcher and its descendants belong only to this invocation.
            if child.poll() is None and birth(child.pid) == launcher_birth:
                subprocess.run(
                    [
                        str(Path(os.environ["SYSTEMROOT"]) / "System32/taskkill.exe"),
                        "/PID",
                        str(child.pid),
                        "/T",
                        "/F",
                    ],
                    capture_output=True,
                    timeout=15,
                    creationflags=subprocess.CREATE_NO_WINDOW,
                )
            if owner is not None and owner.alive():
                owner.terminate_owned()
            if child.poll() is None:
                child.wait(timeout=15)
            save(path, status="failed", stage="watchdog-timeout-no-replay")
        result = json.loads(path.read_text())
        valid_terminal = (
            not timed_out
            and owner is not None
            and not owner.alive()
            and child.returncode == (0 if result.get("status") == "succeeded" else 1)
            and result.get("launch_id") == launch
            and result.get("runner_pid") == actual_pid
            and result.get("runner_process_start_ms") == owner.birth_ms
            and result.get("status") in {"succeeded", "failed"}
        )
        if not valid_terminal and result.get("status") != "failed":
            save(path, status="failed", stage="missing-or-invalid-terminal-state")
        result = json.loads(path.read_text())
        save(
            path.parent / "watchdog.json",
            status=result["status"],
            stage=(
                "verified-actual-runner-exit"
                if valid_terminal
                else "review-required-no-replay"
            ),
            launch_id=launch,
            runner_pid=actual_pid,
            runner_process_start_ms=owner.birth_ms if owner else None,
            launcher_pid=child.pid,
            exit_code=child.returncode,
            supervisor_pid=os.getpid(),
            actual_process_exited=owner is not None and not owner.alive(),
        )
        return 0 if valid_terminal and result["status"] == "succeeded" else 1
    except BaseException as error:
        # Stop only this held helper tree. Any remote outcome remains ambiguous
        # and must be inspected read-only before another operator action.
        if child.poll() is None and birth(child.pid) == launcher_birth:
            subprocess.run(
                [
                    str(Path(os.environ["SYSTEMROOT"]) / "System32/taskkill.exe"),
                    "/PID",
                    str(child.pid),
                    "/T",
                    "/F",
                ],
                capture_output=True,
                timeout=15,
                creationflags=subprocess.CREATE_NO_WINDOW,
            )
        if owner is not None and owner.alive():
            owner.terminate_owned()
        if child.poll() is None:
            child.wait(timeout=15)
        save(
            path,
            status="failed",
            stage="supervisor-failed-no-replay",
            error_type=type(error).__name__,
        )
        save(
            path.parent / "watchdog.json",
            status="failed",
            stage="review-required-no-replay",
            launch_id=launch,
            launcher_pid=child.pid,
            runner_pid=actual_pid,
            runner_process_start_ms=owner.birth_ms if owner else None,
            actual_process_exited=owner is not None and not owner.alive(),
        )
        return 1
    finally:
        if owner is not None:
            owner.close()
