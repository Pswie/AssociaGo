"""Review or upload an already prepared frontend to Vercel PREVIEW only.

No production target, promotion, domain assignment, environment pull or secret
decryption is implemented. Public configuration is passed explicitly for source
builds; the primary site retains server secrets configured on its own project.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
from prepare_frontends import inventory as current_inventory


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--state", type=Path, required=True)
    parser.add_argument("--app", choices=["associago", "librepm", "bandi", "primary"], required=True)
    action = parser.add_mutually_exclusive_group()
    action.add_argument("--deploy-preview", action="store_true")
    action.add_argument("--inspect-with-cli", action="store_true")
    args = parser.parse_args()
    state_path = args.state.resolve(strict=True)
    state = json.loads(state_path.read_text(encoding="utf-8"))
    if state.get("status") != "succeeded":
        raise RuntimeError("Frontend preparation has not succeeded")
    project = state["projects"][args.app]
    if project["state"] != "prepared" and args.deploy_preview:
        raise RuntimeError("The gateway URL is missing; prepare the source snapshot again before deployment")
    root = Path(project["path"]).resolve(strict=True)
    if root.parent != state_path.parent:
        raise RuntimeError("Unexpected frontend staging root")
    inventory = json.loads((state_path.parent / f"{args.app}-inventory.json").read_text(encoding="utf-8"))
    actual = current_inventory(root)
    if {item["path"] for item in actual["files"]} != {item["path"] for item in inventory["files"]}:
        raise RuntimeError("Snapshot file set changed since preparation")
    for item in inventory["files"]:
        path = (root / item["path"]).resolve(strict=True)
        if not path.is_relative_to(root) or hashlib.sha256(path.read_bytes()).hexdigest() != item["sha256"]:
            raise RuntimeError("Snapshot changed since preparation; review and prepare it again")
    link = json.loads((root / ".vercel/project.json").read_text(encoding="utf-8"))
    if link["projectId"] != project["projectId"]:
        raise RuntimeError("Project ID differs from the reviewed staging manifest")
    command = ["vercel", "deploy", "--target", "preview", "--yes", "--scope", link["orgId"], "--archive=tgz", "--no-wait", "--meta", f"suiteStage={state_path.parent.name}", "--meta", f"suiteApp={args.app}"]
    if project["kind"] == "prebuilt-static":
        command.append("--prebuilt")
    else:
        for key, value in project["publicEnvironment"].items():
            if value is None and not args.deploy_preview:
                continue
            if not isinstance(value, str) or not re.fullmatch(r"[A-Z][A-Z0-9_]*", key) or re.search(r"[\r\n;&|<>`$]", value):
                raise RuntimeError("Unexpected public configuration value")
            if key in {"CLERK_SECRET_KEY", "DATABASE_URL", "LOCAL_NODE_API_KEY"}:
                raise RuntimeError("Secret configuration is not accepted")
            command.extend(["--build-env", f"{key}={value}", "--env", f"{key}={value}"])
    print(json.dumps({"app": args.app, "projectId": project["projectId"], "target": "preview", "kind": project["kind"], "path": str(root), "files": inventory["count"], "bytes": inventory["bytes"], "mode": "deploy-preview" if args.deploy_preview else "inspect-with-cli" if args.inspect_with_cli else "review-only", "state": project["state"]}, indent=2))
    if args.inspect_with_cli:
        command.remove("--no-wait")
        command.extend(["--dry", "--format=json"])
        launch = ["cmd", "/c", *command] if os.name == "nt" else command
        receipt = state_path.parent / f"{args.app}-cli-inspection.log"
        with receipt.open("w", encoding="utf-8") as log:
            result = subprocess.run(launch, cwd=root, stdout=log, stderr=subprocess.STDOUT, check=False, timeout=90)
        print(f"CLI inspection exit={result.returncode}; receipt {receipt}")
        if result.returncode:
            raise RuntimeError("Read-only Vercel CLI inspection failed; inspect the receipt")
    elif args.deploy_preview:
        launch = ["cmd", "/c", *command] if os.name == "nt" else command
        result = subprocess.run(launch, cwd=root, check=False, timeout=240)
        if result.returncode:
            raise RuntimeError("Vercel preview upload failed; inspect deployment state before retrying")


if __name__ == "__main__":
    main()
