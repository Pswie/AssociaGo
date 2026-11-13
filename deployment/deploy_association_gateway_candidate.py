"""One image-only Cloud Run candidate; production traffic is never promoted here."""

from __future__ import annotations

import argparse
import copy
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
from urllib.error import HTTPError
from urllib.request import HTTPRedirectHandler, Request, build_opener

from smoke_association_gateway_image import ROOT, birth, save

PROJECT = "flawless-star-503422-q3"
REGION = "europe-west8"
SERVICE = "adf-bandi-portale-api"
OLD = SERVICE + "-calendar-20261104-a"
REVISION = SERVICE + "-entities-20261113-a"
TAG = "entities-20261113"
PREFIX = (
    "europe-west8-docker.pkg.dev/" + PROJECT + "/cloud-run-source-deploy/" + SERVICE
)
OLD_IMAGE = (
    PREFIX + "@sha256:03662a54a868d7eca65b4118e94ea5f0b562a4f99ff912f46c0fc1184477133f"
)


def digest(value):
    return hashlib.sha256(
        json.dumps(value, sort_keys=True, separators=(",", ":")).encode()
    ).hexdigest()


def gcloud(path, args, stage, timeout=40):
    executable = shutil.which("gcloud.cmd") or shutil.which("gcloud")
    child = subprocess.Popen(
        [executable, *args, "--project=" + PROJECT, "--region=" + REGION],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0,
    )
    save(path, stage=stage, child_pid=child.pid, child_birth=birth(child.pid))
    deadline = time.monotonic() + timeout
    while True:
        try:
            out, err = child.communicate(timeout=2)
            break
        except subprocess.TimeoutExpired:
            save(path, stage=stage)
            if time.monotonic() > deadline:
                child.terminate()
                child.wait(timeout=10)
                raise RuntimeError("GCLOUD_DEADLINE_" + stage)
    # Service responses may contain configuration: record hashes, not raw values.
    save(
        path,
        last_command={
            "stage": stage,
            "exit_code": child.returncode,
            "stdout_bytes": len(out),
            "stdout_sha256": hashlib.sha256(out).hexdigest(),
            "stderr_bytes": len(err),
            "stderr_sha256": hashlib.sha256(err).hexdigest(),
        },
    )
    if child.returncode:
        raise RuntimeError("GCLOUD_FAILED_" + stage)
    return out.decode("utf-8")


def describe(path, stage):
    return json.loads(
        gcloud(path, ["run", "services", "describe", SERVICE, "--format=json"], stage)
    )


def config(service):
    spec = copy.deepcopy(service["spec"]["template"]["spec"])
    spec["containers"][0].pop("image", None)
    ignored = {
        "run.googleapis.com/client-name",
        "run.googleapis.com/client-version",
        "run.googleapis.com/operation-id",
        "run.googleapis.com/urls",
        "serving.knative.dev/creator",
        "serving.knative.dev/lastModifier",
    }
    annotations = {
        k: v
        for k, v in service.get("metadata", {}).get("annotations", {}).items()
        if k not in ignored
    }
    template_annotations = {
        k: v
        for k, v in service["spec"]["template"]
        .get("metadata", {})
        .get("annotations", {})
        .items()
        if k not in ignored
    }
    return {
        "templateSpecExceptImage": spec,
        "serviceAnnotations": annotations,
        "templateAnnotations": template_annotations,
    }


def traffic(service, expected):
    positive = [item for item in service["status"]["traffic"] if item.get("percent", 0)]
    if (
        len(positive) != 1
        or positive[0]["revisionName"] != expected
        or positive[0]["percent"] != 100
    ):
        raise RuntimeError("PRODUCTION_TRAFFIC_DRIFT")


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None


def http(url, path, expected):
    opener = build_opener(NoRedirect())
    started = time.monotonic()
    try:
        with opener.open(
            Request(url + path, headers={"Accept": "application/json"}), timeout=30
        ) as response:
            code = response.status
            body = response.read(131073)
            ctype = response.headers.get("Content-Type", "")
    except HTTPError as response:
        code = response.code
        body = response.read(131073)
        ctype = response.headers.get("Content-Type", "")
    if code != expected or len(body) > 131072:
        raise RuntimeError("HTTP_CHECK_FAILED_" + str(expected))
    if path == "/health" and (
        "application/json" not in ctype or json.loads(body).get("status") != "ok"
    ):
        raise RuntimeError("HEALTH_BODY_INVALID")
    return {
        "path": path,
        "status": code,
        "bytes": len(body),
        "sha256": hashlib.sha256(body).hexdigest(),
        "ms": round((time.monotonic() - started) * 1000),
    }


def run(path):
    data = json.loads(path.read_text())
    image = data["image"]
    before = describe(path, "baseline")
    traffic(before, OLD)
    if (
        before["metadata"]["name"] != SERVICE
        or before["status"]["latestReadyRevisionName"] != OLD
    ):
        raise RuntimeError("BASELINE_REVISION_DRIFT")
    spec = before["spec"]["template"]["spec"]
    if len(spec["containers"]) != 1 or spec["containers"][0]["image"] != OLD_IMAGE:
        raise RuntimeError("BASELINE_IMAGE_DRIFT")
    if (
        spec["serviceAccountName"]
        != "adf-bandi-runtime@" + PROJECT + ".iam.gserviceaccount.com"
    ):
        raise RuntimeError("BASELINE_ACCOUNT_DRIFT")
    if len(spec["volumes"]) != 1 or spec["volumes"][0]["secret"] != {
        "secretName": "adf-bandi-portal-runtime-20261030",
        "items": [{"key": "3", "path": "gateway.env"}],
    }:
        raise RuntimeError("BASELINE_SECRET_DRIFT")
    iam_before = json.loads(
        gcloud(
            path,
            ["run", "services", "get-iam-policy", SERVICE, "--format=json"],
            "iam-before",
        )
    )
    names = gcloud(
        path,
        [
            "run",
            "revisions",
            "list",
            "--service=" + SERVICE,
            "--format=value(metadata.name)",
        ],
        "revision-preflight",
    )
    if REVISION in names.splitlines():
        raise RuntimeError("CANDIDATE_ALREADY_EXISTS_NO_REPLAY")
    if describe(path, "baseline-recheck")["spec"] != before["spec"]:
        raise RuntimeError("BASELINE_CHANGED")
    command = [
        "run",
        "deploy",
        SERVICE,
        "--image=" + image,
        "--no-traffic",
        "--revision-suffix=entities-20261113-a",
        "--tag=" + TAG,
        "--quiet",
    ]
    plan = {
        "project": PROJECT,
        "region": REGION,
        "service": SERVICE,
        "previousRevision": OLD,
        "candidateRevision": REVISION,
        "image": image,
        "configSha256": digest(config(before)),
        "iamBindingsSha256": digest(iam_before.get("bindings", [])),
        "command": command,
        "productionTrafficChanged": False,
        "secretWrites": 0,
        "deletions": [],
    }
    (path.parent / "plan.json").write_text(json.dumps(plan, indent=2), encoding="utf-8")
    with (path.parent / "candidate.claim").open("x") as claim:
        claim.write(digest(plan))
    gcloud(path, command, "deploying-no-traffic", timeout=360)
    after = describe(path, "candidate-inspection")
    traffic(after, OLD)
    if (
        after["status"]["latestReadyRevisionName"] != REVISION
        or after["spec"]["template"]["spec"]["containers"][0]["image"] != image
    ):
        raise RuntimeError("CANDIDATE_IMAGE_OR_REVISION_MISMATCH")
    if config(after) != config(before):
        raise RuntimeError("CONFIGURATION_CHANGED_BEYOND_IMAGE")
    old_tags = {
        x["tag"]: x["revisionName"] for x in before["status"]["traffic"] if x.get("tag")
    }
    new_tags = {
        x["tag"]: x["revisionName"] for x in after["status"]["traffic"] if x.get("tag")
    }
    if any(new_tags.get(k) != v for k, v in old_tags.items()):
        raise RuntimeError("PREVIOUS_TAG_CHANGED")
    iam_after = json.loads(
        gcloud(
            path,
            ["run", "services", "get-iam-policy", SERVICE, "--format=json"],
            "iam-after",
        )
    )
    if iam_after.get("bindings", []) != iam_before.get("bindings", []):
        raise RuntimeError("IAM_BINDINGS_CHANGED")
    tag = next((x for x in after["status"]["traffic"] if x.get("tag") == TAG), None)
    expected_url = (
        "https://" + TAG + "---" + before["status"]["url"].removeprefix("https://")
    )
    if not tag or tag.get("revisionName") != REVISION or tag.get("url") != expected_url:
        raise RuntimeError("CANDIDATE_TAG_URL_MISMATCH")
    checks = []
    for endpoint, status in [
        ("/health", 200),
        ("/api/collaboration/v1/me", 401),
        ("/api/collaboration/v1/associations", 401),
        ("/docs", 404),
    ]:
        save(path, stage="candidate-http-checks", checking=endpoint)
        checks.append(http(expected_url, endpoint, status))
    final = describe(path, "final-traffic-check")
    traffic(final, OLD)
    if config(final) != config(after):
        raise RuntimeError("FINAL_CONFIGURATION_DRIFT")
    receipt = {
        **plan,
        "status": "succeeded",
        "stage": "candidate-ready-awaiting-promotion",
        "candidateUrl": expected_url,
        "checks": checks,
        "configUnchangedExceptImage": True,
        "iamUnchanged": True,
        "previousTagsRetained": True,
        "previousTrafficRetained": True,
        "ownerClerkSessionVerified": False,
        "businessRecordsWritten": False,
        "sourceSha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
    }
    (path.parent / "candidate-receipt.json").write_text(
        json.dumps(receipt, indent=2), encoding="utf-8"
    )
    save(path, **receipt)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--image")
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
    if not args.image or not re.fullmatch(
        re.escape(PREFIX) + r"@sha256:[a-f0-9]{64}", args.image
    ):
        parser.error("Exact registry digest required")
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
        image=args.image,
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
    deadline = time.monotonic() + 600
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
