"""Promote only the reviewed shared-requirements gateway candidate, exactly once."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import time

from deploy_requirements_gateway_candidate import (
    OLD,
    PROJECT,
    REGION,
    REVISION,
    SERVICE,
    config,
    describe,
    digest,
    gcloud,
    http,
    traffic,
)
from smoke_association_gateway_image import ROOT, birth, save


def run(path):
    data = json.loads(path.read_text())
    candidate_path = Path(data["candidateReceipt"])
    raw = candidate_path.read_bytes()
    if hashlib.sha256(raw).hexdigest() != data["candidateReceiptSha256"]:
        raise RuntimeError("CANDIDATE_RECEIPT_CHANGED")
    candidate = json.loads(raw)
    if (
        candidate.get("status") != "succeeded"
        or candidate.get("candidateRevision") != REVISION
        or candidate.get("previousRevision") != OLD
    ):
        raise RuntimeError("CANDIDATE_NOT_VERIFIED")
    before = describe(path, "before-promotion")
    traffic(before, OLD)
    if before["status"]["latestReadyRevisionName"] != REVISION:
        raise RuntimeError("READY_REVISION_DRIFT")
    if (
        before["spec"]["template"]["spec"]["containers"][0]["image"]
        != candidate["image"]
    ):
        raise RuntimeError("CANDIDATE_IMAGE_DRIFT")
    if digest(config(before)) != candidate["configSha256"]:
        raise RuntimeError("CONFIGURATION_DRIFT")
    iam = json.loads(
        gcloud(
            path,
            ["run", "services", "get-iam-policy", SERVICE, "--format=json"],
            "iam-before-promotion",
        )
    )
    if digest(iam.get("bindings", [])) != candidate["iamBindingsSha256"]:
        raise RuntimeError("IAM_DRIFT")
    if describe(path, "final-promotion-preflight")["spec"] != before["spec"]:
        raise RuntimeError("SERVICE_CHANGED_DURING_PREFLIGHT")
    claim = {
        "previousRevision": OLD,
        "revision": REVISION,
        "image": candidate["image"],
        "configSha256": candidate["configSha256"],
        "candidateReceiptSha256": data["candidateReceiptSha256"],
    }
    with (path.parent / "promotion.claim").open("x") as stream:
        stream.write(json.dumps(claim, sort_keys=True))
    command = [
        "run",
        "services",
        "update-traffic",
        SERVICE,
        "--to-revisions=" + REVISION + "=100",
        "--quiet",
    ]
    try:
        gcloud(path, command, "moving-production-traffic", timeout=120)
    except BaseException:
        # A failed response does not establish whether the remote write occurred.
        # Observe once, preserve evidence, stop: never replay the mutation.
        try:
            actual = describe(path, "uncertain-promotion-inspection")
            save(
                path,
                actualTraffic=[
                    {
                        k: item[k]
                        for k in ("revisionName", "percent", "tag")
                        if k in item
                    }
                    for item in actual["status"]["traffic"]
                ],
            )
        except BaseException:
            pass
        raise
    after = describe(path, "production-traffic-check")
    traffic(after, REVISION)
    if config(after) != config(before):
        raise RuntimeError("CONFIGURATION_CHANGED_ON_PROMOTION")
    if (
        after["spec"]["template"]["spec"]["containers"][0]["image"]
        != candidate["image"]
    ):
        raise RuntimeError("PROMOTED_IMAGE_MISMATCH")
    old_tags = {
        item["tag"]: item["revisionName"]
        for item in before["status"]["traffic"]
        if item.get("tag")
    }
    new_tags = {
        item["tag"]: item["revisionName"]
        for item in after["status"]["traffic"]
        if item.get("tag")
    }
    if any(new_tags.get(key) != value for key, value in old_tags.items()):
        raise RuntimeError("PREVIOUS_TAG_CHANGED")
    iam_after = json.loads(
        gcloud(
            path,
            ["run", "services", "get-iam-policy", SERVICE, "--format=json"],
            "iam-after-promotion",
        )
    )
    if iam_after.get("bindings", []) != iam.get("bindings", []):
        raise RuntimeError("IAM_CHANGED_ON_PROMOTION")
    old = json.loads(
        gcloud(
            path,
            ["run", "revisions", "describe", OLD, "--format=json"],
            "rollback-revision-check",
        )
    )
    if old["metadata"]["name"] != OLD or not any(
        x.get("type") == "Ready" and x.get("status") == "True"
        for x in old["status"]["conditions"]
    ):
        raise RuntimeError("ROLLBACK_REVISION_NOT_RETAINED_READY")
    checks = []
    origin = after["status"]["url"]
    for endpoint, status in [
        ("/health", 200),
        ("/api/collaboration/v1/me", 401),
        ("/api/collaboration/v1/associations", 401),
        ("/docs", 404),
    ]:
        save(path, stage="production-http-checks", checking=endpoint)
        checks.append(http(origin, endpoint, status))
    final = describe(path, "final-production-postflight")
    traffic(final, REVISION)
    if config(final) != config(after):
        raise RuntimeError("FINAL_CONFIGURATION_DRIFT")
    receipt = {
        "status": "succeeded",
        "stage": "published",
        "project": PROJECT,
        "region": REGION,
        "service": SERVICE,
        "productionRevision": REVISION,
        "trafficPercent": 100,
        "image": candidate["image"],
        "previousRevisionRetained": OLD,
        "rollbackRevisionReady": True,
        "origin": origin,
        "checks": checks,
        "configSha256": candidate["configSha256"],
        "iamBindingsSha256": candidate["iamBindingsSha256"],
        "configurationUnchanged": True,
        "iamUnchanged": True,
        "previousTagsRetained": True,
        "businessRecordsWritten": False,
        "ownerClerkSessionVerified": False,
        "secretWrites": 0,
        "deletions": [],
        "candidateReceiptSha256": data["candidateReceiptSha256"],
        "sourceSha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
    }
    (path.parent / "publication-receipt.json").write_text(
        json.dumps(receipt, indent=2), encoding="utf-8"
    )
    save(path, **receipt)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--candidate-receipt", type=Path)
    parser.add_argument("--receipt-sha256")
    parser.add_argument("--run-directory", type=Path)
    parser.add_argument("--runner", type=Path)
    args = parser.parse_args()
    if args.runner:
        path = args.runner.resolve(strict=True)
        save(path, runner_pid=os.getpid(), runner_birth=birth(os.getpid()))
        try:
            run(path)
        except BaseException as error:
            save(
                path,
                status="failed",
                stage="failed",
                error=type(error).__name__ + ":" + str(error),
            )
        return 0 if json.loads(path.read_text())["status"] == "succeeded" else 1
    directory = args.run_directory.resolve()
    if not directory.is_relative_to((ROOT / "deployment/.artifacts").resolve()):
        parser.error("New artifact directory required")
    directory.mkdir(parents=True, exist_ok=False)
    path = directory / "state.json"
    save(
        path,
        status="running",
        stage="watchdog-starting",
        runner_pid=None,
        supervisor_pid=os.getpid(),
        candidateReceipt=str(args.candidate_receipt.resolve(strict=True)),
        candidateReceiptSha256=args.receipt_sha256.lower(),
    )
    print(
        json.dumps({"state": str(path), "log": str(directory / "watchdog.json")}),
        flush=True,
    )
    child = subprocess.Popen(
        [sys.executable, __file__, "--runner", str(path)],
        creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0,
    )
    identity = birth(child.pid)
    deadline = time.monotonic() + 300
    while child.poll() is None and time.monotonic() < deadline:
        save(
            directory / "watchdog.json",
            status="running",
            stage="monitoring",
            runner_pid=child.pid,
            runner_birth=identity,
        )
        time.sleep(1)
    if child.poll() is None and birth(child.pid) == identity:
        if os.name == "nt":
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
            )
        else:
            child.kill()
        child.wait(timeout=15)
        save(path, status="failed", stage="watchdog-timeout")
    result = json.loads(path.read_text())
    if result["status"] == "running":
        save(path, status="failed", stage="missing-terminal-state")
    result = json.loads(path.read_text())
    save(
        directory / "watchdog.json",
        status=result["status"],
        stage="complete",
        runner_pid=child.pid,
        exit_code=child.returncode,
    )
    print(json.dumps({"status": result["status"], "state": str(path)}))
    return 0 if result["status"] == "succeeded" else 1


if __name__ == "__main__":
    raise SystemExit(main())
