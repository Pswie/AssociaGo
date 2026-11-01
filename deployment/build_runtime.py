"""Build one tested application with a persistent, bounded supervisor.

The Docker context is an explicit allowlist: a bootJar or the lightweight
gateway modules. No local data or credentials are sent to the builder.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import time
import uuid


def gateway_modules(repository: Path) -> list[str]:
    """Explicit serving modules only; no exporter, publisher, operator or AI runtime."""
    modules = ["apps/__init__.py", "apps/api/__init__.py", "apps/api/main.py", "apps/api/config.py",
               "apps/api/suite/__init__.py", "apps/api/suite/schemas.py"]
    modules += ["apps/api/collaboration/" + name + ".py" for name in (
        "__init__", "gateway", "router", "security", "store", "models", "results",
        "schemas", "errors", "maintenance", "migrate", "suite_adapter",
        "native_contract", "native_router", "native_store", "native_binding",
    )]
    modules.append("apps/api/collaboration/native_catalog.json")
    modules += ["apps/api/official_sources/" + name + ".py" for name in (
        "__init__", "schemas", "http", "service", "router", "runts", "catalogues",
    )]
    modules += ["apps/api/discovery/" + name + ".py" for name in (
        "__init__", "service", "router",
    )]
    modules += ["apps/api/cloud_catalog/" + name for name in (
        "__init__.py", "manager.py", "store.py", "router.py", "reader.py", "duckdb_backend.py",
        "formats.py", "remote_store.py",
        "read_operations.json", "adapters/__init__.py", "adapters/public_sources.py",
    )]
    modules += ["apps/api/" + name + ".py" for name in (
        "database", "models", "schemas", "auth", "domain_schemas", "domain_service",
        "project_catalog_schemas", "project_catalog_service", "awards_schemas", "awards_service",
        "organisation_identifiers_schemas", "organisation_identifiers_service",
    )]
    modules += ["apps/api/routes/" + name + ".py" for name in (
        "__init__", "auth", "domain", "project_catalog", "runts_profiles", "awards",
        "money_map", "organisation_identifiers",
    )]
    for package, names in (
        ("money_map", ("__init__", "schemas", "service")),
        ("runts_profiles", ("__init__", "schemas", "service")),
        ("organisation_financial_observations", ("__init__", "router", "schemas", "service")),
        ("opportunity_radar", ("__init__", "models", "source_registry")),
        ("search", ("__init__", "derived_triggers")),
    ):
        modules += [f"apps/api/{package}/{name}.py" for name in names]
    modules += [str(p.relative_to(repository)).replace("\\", "/") for p in
                (repository / "apps/api/collaboration/migrations").glob("*.sql")]
    return sorted(set(modules))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--application", required=True, choices=("associago", "librepm", "bandi-portal"))
    parser.add_argument("--run-directory", required=True, type=Path)
    parser.add_argument("--push", action="store_true")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    app = args.application
    directory = args.run_directory.resolve()
    directory.mkdir(parents=True, exist_ok=False)
    context = directory / "context"
    context.mkdir()
    certificate = Path(__file__).with_name("certs") / "supabase-root-2021.crt"
    if hashlib.sha256(certificate.read_bytes()).hexdigest() != "700723581420dd1ac98fd7e9ac529f0ef210eadcaf87fc868a3ad7d114c2f3b7":
        raise RuntimeError("Supabase CA differs from the reviewed official certificate")
    shutil.copyfile(certificate, context / "supabase-root-2021.crt")
    if app == "bandi-portal":
        repository = root.parent / "Bandi"
        for relative in gateway_modules(repository):
            target = context / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(repository / relative, target)
        shutil.copyfile(Path(__file__).with_name("requirements-gateway.txt"), context / "requirements-gateway.txt")
        shutil.copyfile(Path(__file__).with_name("Dockerfile.gateway"), context / "Dockerfile")
    else:
        repository = root if app == "associago" else root.parent / "LibrePM"
        jars = [p for p in (repository / "build/libs").glob("*.jar") if "-plain" not in p.name]
        if len(jars) != 1:
            raise RuntimeError("Build one current bootJar before packaging")
        shutil.copyfile(jars[0], context / "app.jar")
        shutil.copyfile(Path(__file__).with_name("Dockerfile.runtime"), context / "Dockerfile")
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S") + "-" + uuid.uuid4().hex[:6]
    service = {"associago": "adf-gestionale-collaudo", "librepm": "adf-bandi-collaudo",
               "bandi-portal": "adf-bandi-portale-api"}[app]
    image = "europe-west8-docker.pkg.dev/flawless-star-503422-q3/cloud-run-source-deploy/" + service + ":suite-" + stamp
    manifest = {str(p.relative_to(context)).replace("\\", "/"): hashlib.sha256(p.read_bytes()).hexdigest()
                for p in sorted(context.rglob("*")) if p.is_file()}
    (directory / "context-manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    state = {"status": "running", "stage": "building", "runner_pid": os.getpid(), "child_pid": None,
             "image": image, "context_manifest_sha256": hashlib.sha256(json.dumps(manifest, sort_keys=True).encode()).hexdigest()}
    if "app.jar" in manifest:
        state["jar_sha256"] = manifest["app.jar"]

    def publish():
        state["updated_at"] = datetime.now(timezone.utc).isoformat()
        pending = directory / "state.json.tmp"
        for attempt in range(6):
            try:
                pending.write_text(json.dumps(state, indent=2), encoding="utf-8")
                pending.replace(directory / "state.json")
                return
            except OSError:
                if attempt == 5:
                    # A transient heartbeat/log failure must not stop an active build.
                    # The independent supervisor still owns the real process handle.
                    try:
                        with (directory / "state-fallback.log").open("a", encoding="utf-8") as output:
                            output.write(datetime.now(timezone.utc).isoformat() + " STATE_WRITE_FAILED\n")
                    except OSError:
                        pass
                    return
                time.sleep(0.1 * (attempt + 1))

    def run(command, stage, limit):
        state["stage"] = stage
        with (directory / (stage + ".log")).open("ab", buffering=0) as output:
            child = subprocess.Popen(command, cwd=context, stdout=output, stderr=subprocess.STDOUT)
            state["child_pid"] = child.pid
            deadline = time.monotonic() + limit
            try:
                while child.poll() is None:
                    publish()
                    if time.monotonic() > deadline:
                        raise TimeoutError(stage + " exceeded its deadline")
                    time.sleep(3)
                if child.returncode:
                    raise RuntimeError(stage + " failed; see its bounded log")
            finally:
                if child.poll() is None:
                    child.terminate()
                    try:
                        child.wait(timeout=10)
                    except subprocess.TimeoutExpired:
                        child.kill()
                        child.wait(timeout=10)
                state["child_pid"] = None

    try:
        publish()
        run(["docker", "build", "--label", "codex.task=suite-publication", "-t", image, "."], "build", 1200)
        if args.push:
            run(["docker", "push", image], "push", 600)
        inspection = subprocess.run(["docker", "image", "inspect", image], check=True, capture_output=True, text=True, timeout=20)
        metadata = json.loads(inspection.stdout)[0]
        state.update(status="succeeded", stage="pushed" if args.push else "built", image_id=metadata["Id"],
                     repository_digests=metadata.get("RepoDigests", []), child_pid=None)
    except Exception as error:
        state.update(status="failed", stage="failed", error=type(error).__name__ + ": " + str(error))
    finally:
        publish()
    return 0 if state["status"] == "succeeded" else 1


if __name__ == "__main__":
    raise SystemExit(main())
