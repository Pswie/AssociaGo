"""One-shot command runner with a separate Windows supervisor and retained receipts.

Configuration contains command arrays, directories and public build variables only.
Credentials must remain in existing protected files, referenced by path. No retry,
process killing, cleanup, deletion, or automatic replay is performed.
"""
from __future__ import annotations

import ctypes
from ctypes import wintypes
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import subprocess
import sys
import time

KERNEL = ctypes.WinDLL("kernel32", use_last_error=True)
KERNEL.OpenProcess.argtypes = [wintypes.DWORD, wintypes.BOOL, wintypes.DWORD]
KERNEL.OpenProcess.restype = wintypes.HANDLE
KERNEL.GetProcessTimes.argtypes = [wintypes.HANDLE] + [ctypes.POINTER(wintypes.FILETIME)] * 4
KERNEL.CloseHandle.argtypes = [wintypes.HANDLE]
KERNEL.WaitForSingleObject.argtypes = [wintypes.HANDLE, wintypes.DWORD]


def now():
    return datetime.now(timezone.utc).isoformat()


def identity(pid):
    handle = KERNEL.OpenProcess(0x100000 | 0x1000, False, pid)
    if not handle:
        raise RuntimeError("PROCESS_IDENTITY_UNAVAILABLE")
    a, b, c, d = [wintypes.FILETIME() for _ in range(4)]
    if not KERNEL.GetProcessTimes(handle, ctypes.byref(a), ctypes.byref(b), ctypes.byref(c), ctypes.byref(d)):
        KERNEL.CloseHandle(handle)
        raise RuntimeError("PROCESS_BIRTH_UNAVAILABLE")
    return handle, (((a.dwHighDateTime << 32) | a.dwLowDateTime) - 116444736000000000) // 10000


def save(directory, name, value):
    """A heartbeat failure must not interrupt a running domain command."""
    for attempt in range(6):
        try:
            target = directory / name
            pending = target.with_name(target.name + ".pending-" + str(os.getpid()))
            pending.write_text(json.dumps(value, indent=2) + "\n", encoding="utf8")
            os.replace(pending, target)
            return True
        except OSError:
            time.sleep(0.1 * (attempt + 1))
    try:
        with (directory / "heartbeat-fallback.log").open("a", encoding="utf8") as output:
            output.write(now() + " HEARTBEAT_WRITE_FAILED " + name + "\n")
    except OSError:
        pass
    return False


def worker(directory, config):
    held, birth = identity(os.getpid())
    KERNEL.CloseHandle(held)
    state = {"status": "running", "stage": "starting", "runnerPid": os.getpid(), "runnerBirth": birth, "startedAt": now(), "retry": False}
    for step in config["steps"]:
        label = step["stage"]
        if not label.replace("-", "").isalnum():
            raise ValueError("INVALID_STAGE")
        state.update(stage=label, progressAt=now())
        save(directory, "state.json", state)
        with (directory / (label + ".stdout.log")).open("xb", buffering=0) as stdout, (directory / (label + ".stderr.log")).open("xb", buffering=0) as stderr:
            child = subprocess.Popen(step["command"], cwd=step["cwd"], env={**os.environ, **step.get("publicEnvironment", {})}, stdout=stdout, stderr=stderr, creationflags=subprocess.CREATE_NO_WINDOW)
            held, birth = identity(child.pid)
            state.update(commandPid=child.pid, commandBirth=birth)
            save(directory, "state.json", state)
            started = time.monotonic()
            try:
                while child.poll() is None:
                    state["checkedAt"] = now()
                    if time.monotonic() - started > step.get("deadlineSeconds", 900):
                        state["deadlineExceeded"] = True
                        state["operatorReviewRequired"] = True
                    save(directory, "state.json", state)
                    time.sleep(2)
                assert KERNEL.WaitForSingleObject(held, 0) == 0
            finally:
                KERNEL.CloseHandle(held)
        state.update(lastExitCode=child.returncode, progressAt=now())
        if child.returncode:
            state.update(status="failed", finishedAt=now())
            save(directory, "state.json", state)
            save(directory, "receipt.json", state)
            return child.returncode
    state.update(status="succeeded", stage="complete", finishedAt=now())
    save(directory, "state.json", state)
    save(directory, "receipt.json", state)
    return 0


def supervise(directory, config):
    if (directory / "launch.json").exists():
        raise RuntimeError("ONE_SHOT_ALREADY_STARTED")
    held, birth = identity(os.getpid())
    KERNEL.CloseHandle(held)
    state = {"status": "running", "supervisorPid": os.getpid(), "supervisorBirth": birth, "startedAt": now(), "retry": False}
    if not save(directory, "launch.json", state):
        raise RuntimeError("LAUNCH_RECEIPT_UNAVAILABLE")
    KERNEL.SetThreadExecutionState(0x80000001)
    try:
        with (directory / "runner.stdout.log").open("xb", buffering=0) as stdout, (directory / "runner.stderr.log").open("xb", buffering=0) as stderr:
            child = subprocess.Popen([sys.executable, str(Path(__file__).resolve()), str(directory), "--worker"], cwd=directory, stdout=stdout, stderr=stderr, creationflags=subprocess.CREATE_NO_WINDOW)
            held, birth = identity(child.pid)
            state.update(runnerPid=child.pid, runnerBirth=birth)
            try:
                while child.poll() is None:
                    state.update(checkedAt=now(), logBytes={p.name: p.stat().st_size for p in directory.glob("*.log")})
                    save(directory, "supervisor.json", state)
                    time.sleep(2)
                assert KERNEL.WaitForSingleObject(held, 0) == 0
                state.update(status="succeeded" if child.returncode == 0 else "failed", exitCode=child.returncode, finishedAt=now())
                if not (directory / "receipt.json").exists():
                    state.update(status="failed", reason="WORKER_EXITED_WITHOUT_RECEIPT")
                    save(directory, "receipt.json", state)
                save(directory, "supervisor.json", state)
                return child.returncode
            finally:
                KERNEL.CloseHandle(held)
    finally:
        KERNEL.SetThreadExecutionState(0x80000000)


if __name__ == "__main__":
    run = Path(sys.argv[1]).resolve(strict=True)
    configuration = json.loads((run / "command.json").read_text(encoding="utf8"))
    raise SystemExit(worker(run, configuration) if "--worker" in sys.argv else supervise(run, configuration))
