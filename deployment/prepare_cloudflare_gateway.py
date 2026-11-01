"""Prepare only the existing Bandi gateway's Cloudflare candidate, without cloud writes.

The new secret bundle is written only outside all repositories. Existing secret
versions, files, service revisions and traffic are preserved. The emitted command
creates a candidate with no production traffic; promotion is a separate operation.
"""

from __future__ import annotations

import argparse
import base64
import copy
import hashlib
import json
from pathlib import Path
import re

import deploy_runtime_candidates as runtime

ORIGIN = "https://angeli-del-futuro-archivio.render-keepalive.workers.dev"
SERVICE = "adf-bandi-portale-api"
ARCHIVE_KEY = "COLLAB_CLOUD_CATALOG_READ_KEY"


def decode_secret_payload(encoded: bytes) -> bytes:
    """Decode gcloud get(payload.data) without Windows stdout newline conversion."""
    try:
        if not isinstance(encoded, bytes) or len(encoded) > 90_000:
            raise ValueError
        return base64.b64decode(encoded.strip(), altchars=b"-_", validate=True)
    except (ValueError, TypeError):
        raise runtime.PreparationError(
            "Invalid Secret Manager payload encoding"
        ) from None


def digest(value):
    return hashlib.sha256(
        json.dumps(value, sort_keys=True, separators=(",", ":")).encode()
    ).hexdigest()


def validate_verification(receipt, manifest, manifest_sha256):
    """Fail closed on missing full archive verification; never infer it from upload."""
    result = receipt.get("result", {})
    if not (
        receipt.get("status") == "succeeded"
        and receipt.get("workerUrl") == ORIGIN
        and receipt.get("workerFirstVerified") is True
        and receipt.get("anonymousDenied") is True
        and receipt.get("snapshotId") == manifest["snapshotId"]
        and isinstance(manifest_sha256, str)
        and re.fullmatch(r"[a-f0-9]{64}", manifest_sha256)
        and receipt.get("manifestSha256") == manifest_sha256
        and result.get("packedBytes") == manifest["xzBytes"]
        and result.get("rawBytes") == manifest["databaseBytes"]
        and result.get("packedSha256") == manifest["xzSha256"]
        and result.get("rawSha256") == manifest["databaseSha256"]
        and result.get("chunks")
        == [
            {key: chunk[key] for key in ("index", "bytes", "sha256")}
            for chunk in manifest["externalStorage"]["chunks"]
        ]
    ):
        raise runtime.PreparationError(
            "Complete authenticated Cloudflare verification is required"
        )


def prepare(
    config,
    public,
    service,
    key,
    image,
    revision,
    next_version,
    directory,
    *,
    discovery=None,
):
    """Create an inert, digest-pinned plan against an exact reviewed service snapshot."""
    for flag in (
        "bandiNativeEnabled",
        "bandiOfficialSourcesEnabled",
        "bandiCloudCatalogEnabled",
    ):
        if config.get(flag) is not True:
            raise runtime.PreparationError(
                "The existing catalogue must already be enabled"
            )
    if set(key) != {"ARCHIVE_READ_KEY"} or not isinstance(key["ARCHIVE_READ_KEY"], str):
        raise runtime.PreparationError("An archive-only key document is required")
    archive_key = key["ARCHIVE_READ_KEY"]
    if re.fullmatch(r"[A-Za-z0-9_-]{43,128}", archive_key) is None:
        raise runtime.PreparationError("Invalid archive read key")
    bundles = runtime.validate_runtime_payloads(config)
    old = config["runtimeBundles"]["bandiPortal"]
    old_version = str(old["version"])
    if old_version != "1" or type(next_version) is not int or next_version != 2:
        raise runtime.PreparationError(
            "This reviewed update requires the retained version 1 and new version 2"
        )
    if archive_key in bundles["bandiPortal"].values():
        raise runtime.PreparationError(
            "The archive key must be distinct from every runtime credential"
        )
    prepared_config = copy.deepcopy(config)
    prepared_config["revisionSuffix"] = revision
    prepared_public = copy.deepcopy(public)
    prepared_public["google"]["preparedImages"]["bandiGateway"] = image
    plan = runtime.prepare(prepared_config, prepared_public, directory)
    candidate = next(
        item for item in plan["services"] if item["application"] == "bandiPortal"
    )
    if candidate["service"] != SERVICE or candidate["initialService"]:
        raise runtime.PreparationError("Only the existing Bandi gateway can be updated")
    if service.get("metadata", {}).get("name") != SERVICE:
        raise runtime.PreparationError("Unexpected service document")
    spec = service["spec"]["template"]["spec"]
    if spec.get("serviceAccountName") != candidate["account"]:
        raise runtime.PreparationError(
            "The current runtime service account must be preserved"
        )
    changes = runtime.gateway_volume_cleanup(service, old)
    container = spec["containers"][0]
    env_items = container.get("env", [])
    actual_env = {item["name"]: item.get("value", "") for item in env_items}
    env_path = directory / "bandiPortal-runtime-env.json"
    expected_env = runtime.load(env_path)
    if len(actual_env) != len(env_items) or actual_env != expected_env:
        raise runtime.PreparationError(
            "Current environment differs from the reviewed base rollout"
        )
    actual_env.update(
        COLLAB_CLOUD_CATALOG_STORE="cloudflare", COLLAB_CLOUD_CATALOG_ORIGIN=ORIGIN
    )
    env_path.write_text(json.dumps(actual_env, indent=2) + "\n", encoding="utf-8")
    values = dict(bundles["bandiPortal"], **{ARCHIVE_KEY: archive_key})
    if discovery is not None:
        if not isinstance(discovery, dict) or set(discovery) != {"origin", "key"}:
            raise runtime.PreparationError(
                "An explicit discovery origin and read key are required"
            )
        origin, read_key = discovery["origin"], discovery["key"]
        if (
            not isinstance(origin, str)
            or re.fullmatch(r"https://[a-z0-9-]+\.[a-z0-9-]+\.workers\.dev", origin)
            is None
        ):
            raise runtime.PreparationError(
                "Discovery requires a reviewed Workers HTTPS origin"
            )
        if (
            not isinstance(read_key, str)
            or re.fullmatch(r"[A-Za-z0-9_-]{43,128}", read_key) is None
        ):
            raise runtime.PreparationError("Invalid discovery read key")
        if read_key in values.values():
            raise runtime.PreparationError("Discovery credentials must be distinct")
        values["COLLAB_DISCOVERY_READ_KEY"] = read_key
        actual_env["COLLAB_DISCOVERY_ORIGIN"] = origin
        env_path.write_text(json.dumps(actual_env, indent=2) + "\n", encoding="utf-8")
    private_path = directory / "bandiPortal-cloudflare-runtime.private.json"
    private_path.write_text(json.dumps(values, indent=2) + "\n", encoding="utf-8")
    dotenv_path = directory / "bandiPortal-cloudflare-runtime.private.env"
    dotenv_path.write_bytes(runtime.bundle_payload("bandiPortal", values))
    command = candidate["command"]
    old_binding = "--update-secrets=/run/secrets/gateway.env=" + old["name"] + ":1"
    if command.count(old_binding) != 1 or "--no-traffic" not in command:
        raise runtime.PreparationError("Expected explicit candidate secret binding")
    command[command.index(old_binding)] = old_binding[:-1] + str(next_version)
    command[command.index("--tag=pg-candidate")] = "--tag=cf-candidate"
    command.extend(changes)
    previous = service.get("status", {}).get("latestReadyRevisionName")
    if not isinstance(previous, str) or not previous.startswith(SERVICE + "-"):
        raise runtime.PreparationError("The previous ready revision must be retained")
    output = {
        "status": "prepared-awaiting-archive-verification",
        "project": plan["project"],
        "region": plan["region"],
        "service": SERVICE,
        "serviceDocumentSha256": digest(service),
        "previousRevision": previous,
        "previousTraffic": service["status"]["traffic"],
        "previousSecretVersion": old_version,
        "newSecretVersion": str(next_version),
        "secretName": old["name"],
        "secretPayloadFile": str(dotenv_path.resolve()),
        "secretPayloadSha256": hashlib.sha256(dotenv_path.read_bytes()).hexdigest(),
        "newRuntimeBundleFile": str(private_path.resolve()),
        "command": command,
        "archiveOrigin": ORIGIN,
        "deletions": [],
        "productionTrafficChanged": False,
        "requiredBeforeExecution": [
            "Re-read service and compare serviceDocumentSha256 before any cloud write",
            "Verify all authenticated archive chunks, full packed/raw digests and anonymous denial",
            "Re-check version 2 is absent and add the new version exactly once using stdin",
            "Read back version 2 in memory and compare exact payload before candidate deployment",
            "Re-check account-wide storage/secret allowances and preserve every existing version",
            "Run the no-traffic candidate command once, verify candidate data before separate promotion",
        ],
    }
    (directory / "cloudflare-gateway-plan.json").write_text(
        json.dumps(output, indent=2) + "\n", encoding="utf-8"
    )
    return output


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", required=True, type=Path)
    parser.add_argument(
        "--public-settings",
        default=runtime.ROOT / "suite-public-settings.json",
        type=Path,
    )
    parser.add_argument("--service-document", required=True, type=Path)
    parser.add_argument("--archive-key-file", required=True, type=Path)
    parser.add_argument("--image", required=True)
    parser.add_argument("--revision-suffix", required=True)
    parser.add_argument("--secret-version", type=int, default=2)
    parser.add_argument("--discovery-origin")
    parser.add_argument("--discovery-key-file", type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    directory = args.output.resolve()
    repositories = [
        runtime.ROOT.parent,
        runtime.ROOT.parent.parent / "Bandi",
        runtime.ROOT.parent.parent / "LibrePM",
    ]
    if any(
        directory == repo or directory.is_relative_to(repo) for repo in repositories
    ):
        raise SystemExit("Private rollout output must be outside every repository")
    directory.mkdir(parents=True, exist_ok=False)
    try:
        if bool(args.discovery_origin) != bool(args.discovery_key_file):
            raise runtime.PreparationError(
                "Discovery origin and key file must be provided together"
            )
        discovery = None
        if args.discovery_key_file:
            keys = runtime.load(args.discovery_key_file)
            if set(keys) != {"DISCOVERY_READ_KEY"}:
                raise runtime.PreparationError(
                    "An archive-independent discovery key document is required"
                )
            discovery = {
                "origin": args.discovery_origin,
                "key": keys["DISCOVERY_READ_KEY"],
            }
        prepare(
            runtime.load(args.config),
            runtime.load(args.public_settings),
            runtime.load(args.service_document),
            runtime.load(args.archive_key_file),
            args.image,
            args.revision_suffix,
            args.secret_version,
            directory,
            discovery=discovery,
        )
    except Exception:
        raise SystemExit(
            "Preparation failed; retained private output contains no cloud changes"
        ) from None
    print("PASS prepared-only; no cloud operation was executed")


if __name__ == "__main__":
    main()
