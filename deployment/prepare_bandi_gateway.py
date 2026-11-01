"""Seal one additive runtime3 plan against the exact current gateway, without cloud writes."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import re
from typing import Callable

import deploy_runtime_candidates as runtime

ORIGIN = "https://angeli-del-futuro-bandi-quotidiani.render-keepalive.workers.dev"
SERVICE = "adf-bandi-portale-api"
READ_KEY = "COLLAB_BANDI_CATALOG_READ_KEY"


def digest(value: object) -> str:
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(",", ":")).encode()).hexdigest()


def prepare(
    service: dict,
    previous: dict[str, str],
    previous_payload: bytes,
    key_document: dict[str, str],
    private_directory: Path,
    public_directory: Path,
    validate_settings: Callable[[dict[str, str]], None],
) -> dict:
    """No publisher credentials, mutations, cleanup, environment pull or secret output."""
    if service.get("metadata", {}).get("name") != SERVICE:
        raise runtime.PreparationError("Unexpected service")
    spec = service["spec"]["template"]["spec"]
    if len(spec.get("containers", [])) != 1 or len(spec.get("volumes", [])) != 1:
        raise runtime.PreparationError("Expected one existing gateway and secret mount")
    container, volume = spec["containers"][0], spec["volumes"][0]
    binding = volume.get("secret", {})
    if binding.get("items") != [{"key": "2", "path": "gateway.env"}]:
        raise runtime.PreparationError("Expected retained runtime2 binding")
    runtime.gateway_volume_cleanup(service, {"name": binding["secretName"], "version": "2"})
    env_list = container.get("env", [])
    if any("valueFrom" in item for item in env_list):
        raise runtime.PreparationError("Unexpected environment secret")
    env = {item["name"]: item.get("value", "") for item in env_list}
    if len(env) != len(env_list) or env.get("BANDI_MODE") != "gateway":
        raise runtime.PreparationError("Unexpected current environment")
    if not previous or not all(isinstance(k, str) and isinstance(v, str) and v and not any(c in v for c in "\r\n\x00") for k, v in previous.items()):
        raise runtime.PreparationError("Invalid previous bundle")
    if runtime.bundle_payload("bandiPortal", previous) != previous_payload:
        raise runtime.PreparationError("Previous bundle bytes do not match its retained source")
    if set(key_document) != {"BANDI_READ_KEY"} or re.fullmatch(r"[A-Za-z0-9_-]{43,128}", key_document["BANDI_READ_KEY"]) is None:
        raise runtime.PreparationError("One distinct read-only key is required")
    if key_document["BANDI_READ_KEY"] in previous.values() or READ_KEY in previous:
        raise runtime.PreparationError("The new index key must be distinct")
    if any("PUBLISH" in k for k in previous) or set(previous) & {"COLLAB_BANDI_CATALOG_ORIGIN", "COLLAB_NODE_ONLINE_SECONDS"}:
        raise runtime.PreparationError("Unexpected additions in the previous bundle")
    values = {**previous, "COLLAB_BANDI_CATALOG_ORIGIN": ORIGIN, READ_KEY: key_document["BANDI_READ_KEY"], "COLLAB_NODE_ONLINE_SECONDS": "180"}
    try:
        validate_settings({**values, **env})
    except Exception:
        raise runtime.PreparationError("New runtime settings rejected") from None
    for repo in (runtime.ROOT.parent, runtime.ROOT.parent.parent / "Bandi", runtime.ROOT.parent.parent / "LibrePM"):
        if private_directory.resolve().is_relative_to(repo.resolve()):
            raise runtime.PreparationError("Private payload must stay outside repositories")
    target = public_directory / "runtime3-plan.json"
    if target.exists():
        raise runtime.PreparationError("An existing plan must never be overwritten")
    previous_revision = service["status"]["latestReadyRevisionName"]
    traffic = service["status"]["traffic"]
    if sum(t.get("percent", 0) for t in traffic if t.get("revisionName") == previous_revision) != 100 or any(t.get("percent", 0) and t.get("revisionName") != previous_revision for t in traffic):
        raise runtime.PreparationError("Expected one unchanged production revision")
    private_directory.mkdir(parents=True, exist_ok=False)
    payload = runtime.bundle_payload("bandiPortal", values)
    private_json = private_directory / "runtime3.private.json"
    private_file = private_directory / "runtime3.private.env"
    private_json.write_text(json.dumps(values, indent=2) + "\n", encoding="utf-8")
    private_file.write_bytes(payload)
    plan = {
        "status": "prepared", "service": SERVICE, "serviceBeforeSha256": digest(service),
        "previousRevision": previous_revision, "previousImage": container["image"],
        "secretName": binding["secretName"], "previousSecretVersion": "2", "newSecretVersion": "3",
        "previousPayloadSha256": hashlib.sha256(previous_payload).hexdigest(),
        "payloadSha256": hashlib.sha256(payload).hexdigest(), "payloadBytes": len(payload),
        "payloadFile": str(private_file), "privateJsonFile": str(private_json),
        "operationalOrigin": ORIGIN, "nodeOnlineSeconds": 180,
        "addedKeys": ["COLLAB_BANDI_CATALOG_ORIGIN", READ_KEY, "COLLAB_NODE_ONLINE_SECONDS"],
        "allPreviousValuesPreserved": True, "publisherKeyIncluded": False,
        "secretWrites": 0, "productionTrafficChanged": False, "deletions": [],
    }
    target.write_text(json.dumps(plan, indent=2) + "\n", encoding="utf-8")
    return plan
