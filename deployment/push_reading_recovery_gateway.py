"""Push the exact locally built and Linux-smoked image once; never rebuild it."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import time

from release_watchdog import supervise_release, worker_identity
from smoke_association_gateway_image import ROOT, birth, save

PREFIX = "europe-west8-docker.pkg.dev/flawless-star-503422-q3/cloud-run-source-deploy/adf-bandi-portale-api"


def inspect(image):
    result = subprocess.run(
        ["docker", "image", "inspect", image],
        check=True,
        capture_output=True,
        text=True,
        timeout=20,
        creationflags=subprocess.CREATE_NO_WINDOW,
    )
    return json.loads(result.stdout)[0]


def run(path):
    source = (
        ROOT
        / "deployment/.artifacts/reading-recovery-gateway-build-20261113-a/build-receipt.json"
    )
    smoke = (
        ROOT
        / "deployment/.artifacts/reading-recovery-gateway-linux-smoke-20261113-a/state.json"
    )
    if (
        hashlib.sha256(source.read_bytes()).hexdigest()
        != "0f0b7104a6a945961e673a43c01f1b0322846e375562a108a1cb0b5e7b734635"
    ):
        raise RuntimeError("BUILD_RECEIPT_CHANGED")
    if (
        hashlib.sha256(smoke.read_bytes()).hexdigest()
        != "233de181bafc71d399ccb91bc505b5bc693fccf56c6e77e4a98c189afe8ed105"
    ):
        raise RuntimeError("LINUX_SMOKE_RECEIPT_CHANGED")
    built, checked = json.loads(source.read_text()), json.loads(smoke.read_text())
    if (
        built["status"] != "succeeded"
        or checked["status"] != "succeeded"
        or not checked["cleanup_complete"]
    ):
        raise RuntimeError("BUILD_OR_SMOKE_NOT_VERIFIED")
    tag, image_id = built["imageTag"], built["imageId"]
    if not re.fullmatch(re.escape(PREFIX) + r":suite-[A-Za-z0-9-]+", tag):
        raise RuntimeError("UNEXPECTED_REGISTRY_TARGET")
    if checked["image_id"] != image_id or inspect(tag)["Id"] != image_id:
        raise RuntimeError("LOCAL_IMAGE_DRIFT")
    with (path.parent / "push.claim.json").open("x", encoding="utf-8") as output:
        json.dump({"imageId": image_id, "tag": tag, "retry": False}, output)
        output.flush()
        os.fsync(output.fileno())
    log = path.parent / "push.log"
    with log.open("xb", buffering=0) as output:
        child = subprocess.Popen(
            ["docker", "push", tag],
            stdout=output,
            stderr=subprocess.STDOUT,
            creationflags=subprocess.CREATE_NO_WINDOW,
        )
        deadline = time.monotonic() + 600
        while child.poll() is None:
            save(
                path,
                stage="pushing-exact-image",
                child_pid=child.pid,
                child_birth=birth(child.pid),
                log_bytes=log.stat().st_size,
            )
            if time.monotonic() > deadline:
                child.terminate()
                child.wait(timeout=15)
                raise RuntimeError("PUSH_DEADLINE_NO_REPLAY")
            time.sleep(1)
        if child.returncode != 0:
            raise RuntimeError("PUSH_FAILED_NO_REPLAY")
    after = inspect(tag)
    digests = [
        value
        for value in after.get("RepoDigests", [])
        if re.fullmatch(re.escape(PREFIX) + r"@sha256:[a-f0-9]{64}", value)
    ]
    if after["Id"] != image_id or len(digests) != 1:
        raise RuntimeError("PUSHED_IMAGE_DIGEST_UNCONFIRMED")
    receipt = {
        "status": "succeeded",
        "stage": "exact-image-pushed-awaiting-candidate",
        "imageId": image_id,
        "image": digests[0],
        "tag": tag,
        "buildReceiptSha256": hashlib.sha256(source.read_bytes()).hexdigest(),
        "linuxSmokeReceiptSha256": hashlib.sha256(smoke.read_bytes()).hexdigest(),
        "rebuilds": 0,
        "productionTrafficChanged": False,
        "providerCalls": 0,
        "secretWrites": 0,
        "deletions": [],
    }
    (path.parent / "push-receipt.json").write_text(
        json.dumps(receipt, indent=2), encoding="utf-8"
    )
    save(path, **receipt)


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
                stage="push-failed-no-replay",
                error_type=type(error).__name__,
            )
        return 0 if json.loads(path.read_text())["status"] == "succeeded" else 1
    directory = args.run_directory.resolve()
    allowed = (ROOT / "deployment/.artifacts").resolve()
    if not directory.is_relative_to(allowed) or directory == allowed:
        parser.error("A new artifact directory is required")
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
        json.dumps({"state": str(path), "log": str(directory / "push.log")}), flush=True
    )
    return supervise_release(path, Path(__file__).resolve(), 720)


if __name__ == "__main__":
    raise SystemExit(main())
