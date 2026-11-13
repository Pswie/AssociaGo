"""Build only the allowlisted gateway locally, with an independent OS watchdog."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import sys
import threading

import build_runtime
from release_watchdog import supervise_release, worker_identity
from smoke_association_gateway_image import ROOT, save


def serving_hashes():
    repository = ROOT.parent / "Bandi"
    files = build_runtime.gateway_modules(repository)
    excluded = {"source_worker.py", "source_resolution.py", "reading_openai.py"}
    if any(Path(name).name in excluded or ".env" in name for name in files):
        raise RuntimeError("LOCAL_PROVIDER_OR_SECRET_IN_CONTEXT")
    required = (
        "apps/api/collaboration/migrations/009_association_reading_recoveries.sql"
    )
    if required not in files:
        raise RuntimeError("RECOVERY_MIGRATION_NOT_PACKAGED")
    hashes = {
        name: hashlib.sha256((repository / name).read_bytes()).hexdigest()
        for name in files
    }
    for name in ("Dockerfile.gateway", "requirements-gateway.txt", "build_runtime.py"):
        hashes["operator/" + name] = hashlib.sha256(
            (ROOT / "deployment" / name).read_bytes()
        ).hexdigest()
    return hashes


def run(path):
    before = serving_hashes()
    output = path.parent / "build"
    done = threading.Event()

    def progress():
        while not done.wait(2):
            try:
                state = json.loads((output / "state.json").read_text())
                save(
                    path,
                    stage="image-" + state["stage"],
                    build_pid=state.get("runner_pid"),
                    docker_pid=state.get("child_pid"),
                    log_bytes={p.name: p.stat().st_size for p in output.glob("*.log")},
                )
            except (OSError, ValueError, KeyError):
                pass

    thread = threading.Thread(target=progress, daemon=True)
    thread.start()
    original_arguments = sys.argv
    try:
        sys.argv = [
            "build_runtime.py",
            "--application",
            "bandi-portal",
            "--run-directory",
            str(output),
        ]
        code = build_runtime.main()
    finally:
        sys.argv = original_arguments
        done.set()
        thread.join(timeout=5)
    built = json.loads((output / "state.json").read_text())
    if code != 0 or built["status"] != "succeeded" or serving_hashes() != before:
        raise RuntimeError("BUILD_FAILED_OR_SOURCE_CHANGED")
    context = json.loads((output / "context-manifest.json").read_text())
    if any(
        Path(name).name
        in {"source_worker.py", "source_resolution.py", "reading_openai.py"}
        for name in context
    ):
        raise RuntimeError("LOCAL_PROVIDER_IN_BUILT_CONTEXT")
    receipt = {
        "status": "succeeded",
        "stage": "local-image-built-awaiting-linux-smoke",
        "imageId": built["image_id"],
        "imageTag": built["image"],
        "sourceHashes": before,
        "sourceFilesUnchanged": True,
        "contextManifestSha256": built["context_manifest_sha256"],
        "localProvidersPackaged": False,
        "providerCalls": 0,
        "pushed": False,
        "productionTrafficChanged": False,
        "deletions": [],
    }
    (path.parent / "build-receipt.json").write_text(
        json.dumps(receipt, indent=2), encoding="utf-8"
    )
    save(path, **{k: v for k, v in receipt.items() if k != "sourceHashes"})


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run-directory", type=Path)
    parser.add_argument("--runner", type=Path)
    args = parser.parse_args()
    if args.runner:
        path = args.runner.resolve(strict=True)
        try:
            worker_identity(path)
            run(path)
        except BaseException as error:
            save(
                path,
                status="failed",
                stage="build-failed-no-replay",
                error_type=type(error).__name__,
            )
        return 0 if json.loads(path.read_text())["status"] == "succeeded" else 1
    directory = args.run_directory.resolve()
    allowed = (ROOT / "deployment/.artifacts").resolve()
    if not directory.is_relative_to(allowed) or directory == allowed:
        parser.error("A new child of deployment/.artifacts is required")
    directory.mkdir(parents=True, exist_ok=False)
    path = directory / "state.json"
    save(
        path,
        status="running",
        stage="watchdog-starting",
        runner_pid=None,
        supervisor_pid=os.getpid(),
    )
    print(
        json.dumps({"state": str(path), "log": str(directory / "build/build.log")}),
        flush=True,
    )
    return supervise_release(path, Path(__file__).resolve(), 1500)


if __name__ == "__main__":
    raise SystemExit(main())
