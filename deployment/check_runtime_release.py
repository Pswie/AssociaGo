"""Read-only Cloud Run release audit. Never persists raw provider configuration."""

from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
import json
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import uuid

import deploy_runtime_candidates as deploy


def cloud_read(arguments):
    """Only two metadata operations are permitted; diagnostics remain private."""
    if arguments[:3] not in (
        ["run", "services", "describe"],
        ["run", "revisions", "list"],
    ):
        raise ValueError("Only read-only Cloud Run metadata operations are allowed")
    executable = shutil.which("gcloud.cmd") or shutil.which("gcloud")
    if not executable:
        raise ValueError("Google Cloud SDK is required")
    result = subprocess.run(
        [executable, *arguments], capture_output=True, timeout=60, check=False
    )
    if result.returncode:
        raise ValueError("Cloud metadata query failed; provider output suppressed")
    return json.loads(result.stdout)


def revision_summary(document, *, template=False):
    """Allowlist fields: never serialize env values, labels, annotations or arguments."""
    source = document.get("spec", {}).get("template", {}) if template else document
    spec = source.get("spec", {})
    containers = spec.get("containers", [])
    container = containers[0] if containers else {}
    env = container.get("env", [])
    references = []
    for item in env:
        secret = item.get("valueFrom", {}).get("secretKeyRef")
        if secret:
            references.append(
                {
                    "binding": item.get("name"),
                    "name": secret.get("name"),
                    "version": secret.get("key"),
                    "kind": "environment",
                }
            )
    for volume in spec.get("volumes", []):
        secret = volume.get("secret")
        if secret:
            for item in secret.get("items", []):
                references.append(
                    {
                        "binding": volume.get("name"),
                        "name": secret.get("secretName"),
                        "version": item.get("key"),
                        "kind": "volume",
                    }
                )
    annotations = source.get("metadata", {}).get("annotations", {})
    return {
        "name": source.get("metadata", {}).get("name"),
        "image": container.get("image"),
        "resolvedImage": document.get("status", {}).get("imageDigest"),
        "serviceAccount": spec.get("serviceAccountName"),
        "environmentNames": sorted(item["name"] for item in env),
        "secretReferences": references,
        "containerCount": len(containers),
        "maxInstances": annotations.get("autoscaling.knative.dev/maxScale"),
        "concurrency": spec.get("containerConcurrency"),
        "memory": container.get("resources", {}).get("limits", {}).get("memory"),
    }


def audit_service(service, revisions, app, expected, public, config, expected_env):
    current = revision_summary(service, template=True)
    status = service.get("status", {})
    traffic = [
        {
            key: row[key]
            for key in ("revisionName", "percent", "tag", "url")
            if key in row
        }
        for row in status.get("traffic", [])
    ]
    ready = status.get("latestReadyRevisionName")
    live = {row.get("revisionName") for row in traffic if row.get("percent", 0) > 0}
    tagged = {row.get("revisionName") for row in traffic if row.get("tag")}
    raw_env = service.get("spec", {}).get("template", {}).get("spec", {})
    containers = raw_env.get("containers", [])
    env = (
        {item["name"]: item for item in containers[0].get("env", [])}
        if containers
        else {}
    )
    expected_env = dict(expected_env)
    optional_absent = []
    # First gateway creation precedes discovery of its own URL. This setting
    # describes a peer for native backends; the gateway never calls itself.
    if app == "bandiPortal" and "SUITE_BANDI_API_URL" not in env:
        expected_env.pop("SUITE_BANDI_API_URL", None)
        optional_absent.append("SUITE_BANDI_API_URL")
    mismatches = sorted(
        name
        for name, value in expected_env.items()
        if name not in env or env[name].get("value", "") != value
    )
    wanted_names = set(expected_env)
    if app != "bandiPortal":
        wanted_names.add("SPRING_APPLICATION_JSON")
    reference = config["runtimeBundles"][app]
    refs = current["secretReferences"]
    checks = {
        "readyRevisionMatchesTemplate": ready == current["name"] and bool(ready),
        "allTrafficOnReadyRevision": live == {ready}
        and sum(row.get("percent", 0) for row in traffic) == 100,
        "expectedImage": current["image"] == expected["image"],
        "isolatedServiceAccount": current["serviceAccount"] == expected["account"],
        "exactEnvironmentNames": set(env) == wanted_names,
        "exactPublicConfiguration": not mismatches,
        "onePinnedRuntimeBundle": len(refs) == 1
        and refs[0]["name"] == reference["name"]
        and str(refs[0]["version"]) == str(reference["version"])
        and refs[0]["kind"] == ("volume" if app == "bandiPortal" else "environment"),
        "oneContainerMaxOneInstance": current["containerCount"] == 1
        and current["maxInstances"] == "1"
        and service.get("metadata", {})
        .get("annotations", {})
        .get("run.googleapis.com/maxScale")
        == "1",
        "expectedStableOrigin": status.get("url") == public["apiOrigins"][app],
    }
    inventory = []
    for revision in revisions:
        summary = revision_summary(revision)
        name = summary["name"]
        summary["servesTraffic"] = name in live
        summary["hasTag"] = name in tagged
        summary["cleanupDisposition"] = (
            "retain"
            if name in live or name in tagged or name == ready
            else "review-after-frontend-release"
        )
        inventory.append(summary)
    return {
        "application": app,
        "service": expected["service"],
        "stableUrl": status.get("url"),
        "latestReadyRevision": ready,
        "traffic": traffic,
        "current": current,
        "checks": checks,
        "mismatchedPublicEnvironmentNames": mismatches,
        "optionalAbsentEnvironmentNames": optional_absent,
        "revisionInventory": inventory,
    }


def http_checks(app, origin, config):
    """GET only. Never call cron/maintenance: their GET endpoint updates state."""
    import httpx

    if not re.fullmatch(r"https://[a-z0-9-]+\.a\.run\.app", origin):
        raise ValueError("HTTP audit accepts exact Cloud Run HTTPS origins only")
    source = config["runtimeBundles"][app]["sourceFile"]
    values = json.loads(Path(source).read_text(encoding="utf-8"))
    key = values["SUITE_LIBREPM_KEY" if app == "bandiPortal" else "SUITE_ASSOCIAGO_KEY"]
    peer = "associago" if app == "librepm" else "librepm"
    headers = {
        "X-Suite-Peer": peer,
        "X-Suite-Key": key,
        "X-Suite-Organization": config["organizationId"],
    }
    me = (
        "/api/collaboration/v1/me"
        if app == "bandiPortal"
        else ("/api/v1/me" if app == "associago" else "/api/me")
    )
    path = "/api/integrations/v1/peer/projects/" + str(uuid.uuid4())
    code = {
        "associago": "PROJECT_NOT_LINKED",
        "librepm": "SUITE_NOT_FOUND",
        "bandiPortal": "suite_project_not_found",
    }[app]
    probes = [
        (
            "health",
            "/health" if app == "bandiPortal" else "/actuator/health",
            {},
            200,
            None,
        ),
        ("anonymousIdentityRejected", me, {}, 401, None),
        ("peerMissingKeyRejected", path, {}, 401, None),
        (
            "peerForgedKeyRejected",
            path,
            {**headers, "X-Suite-Key": "forged-audit-key"},
            401,
            None,
        ),
        (
            "peerWrongOrganizationRejected",
            path,
            {**headers, "X-Suite-Organization": str(uuid.uuid4())},
            403,
            None,
        ),
        ("peerDatabaseMirrorReached", path, headers, 404, code),
    ]
    result = []
    with httpx.Client(trust_env=False, follow_redirects=False, timeout=25) as client:
        for name, endpoint, request_headers, expected_status, expected_code in probes:
            response = client.get(origin + endpoint, headers=request_headers)
            actual_code = None
            if expected_code:
                try:
                    data = response.json()
                    actual_code = data.get("code") or data.get("detail", {}).get("code")
                except (ValueError, AttributeError):
                    pass
            result.append(
                {
                    "check": name,
                    "expectedStatus": expected_status,
                    "actualStatus": response.status_code,
                    "passed": response.status_code == expected_status
                    and (expected_code is None or actual_code == expected_code),
                }
            )
    return result


def collect(public, config, *, check_http=False):
    with tempfile.TemporaryDirectory(prefix="cloud-release-audit-") as directory:
        plan = deploy.prepare(config, public, Path(directory))
        expected_env = {
            app: deploy.load(Path(directory) / (app + "-runtime-env.json"))
            for app in deploy.APPS
        }
    flags = [
        "--project=" + plan["project"],
        "--region=" + plan["region"],
        "--format=json",
    ]

    def inspect(expected):
        app = expected["application"]
        document = cloud_read(
            ["run", "services", "describe", expected["service"], *flags]
        )
        revisions = cloud_read(
            ["run", "revisions", "list", "--service=" + expected["service"], *flags]
        )
        image_key = "bandiGateway" if app == "bandiPortal" else app
        expected = {**expected, "image": public["google"]["preparedImages"][image_key]}
        result = audit_service(
            document, revisions, app, expected, public, config, expected_env[app]
        )
        if check_http:
            result["httpChecks"] = http_checks(app, public["apiOrigins"][app], config)
        return result

    with ThreadPoolExecutor(max_workers=3) as workers:
        services = list(workers.map(inspect, plan["services"]))
    passed = all(
        all(row["checks"].values())
        and all(item["passed"] for item in row.get("httpChecks", []))
        for row in services
    )
    return {
        "status": "passed" if passed else "failed",
        "recordedAt": datetime.now(timezone.utc).isoformat(),
        "readOnly": True,
        "googleProject": plan["project"],
        "googleRegion": plan["region"],
        "configuredSupabaseProjectRef": public["supabaseProjectRef"],
        "services": services,
        "limits": [
            "HTTP probes verify authentication boundaries and peer database access; they do not certify a real Clerk browser session or owner action.",
            "Database credentials and TLS settings are inside version-pinned bundles; image trust-store and owner/runtime TLS proofs are separate release evidence.",
            "Cleanup inventory is advisory. Retain serving/tagged revisions; review zero-traffic revisions only after frontend validation and rollback decision.",
            "Image deletion requires checking references across every service, job and region, beyond this three-service inventory.",
        ],
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument(
        "--http",
        action="store_true",
        help="Add GET-only live authentication and peer checks",
    )
    args = parser.parse_args()
    if args.output.exists():
        parser.error("Choose a new receipt path; existing evidence is not overwritten")
    try:
        result = collect(
            deploy.load(deploy.ROOT / "suite-public-settings.json"),
            deploy.load(args.config),
            check_http=args.http,
        )
    except Exception:
        result = {
            "status": "failed",
            "readOnly": True,
            "error": "RELEASE_AUDIT_FAILED_PROVIDER_DETAILS_SUPPRESSED",
        }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("x", encoding="utf-8") as handle:
        json.dump(result, handle, indent=2)
        handle.write("\n")
    print(json.dumps({"status": result["status"], "receipt": str(args.output)}))
    return 0 if result["status"] == "passed" else 1


if __name__ == "__main__":
    raise SystemExit(main())
