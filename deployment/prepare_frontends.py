"""Prepare reviewed Vercel frontend snapshots, without deploying or reading secrets.

The two SPAs are compiled locally into Build Output API static artifacts. Bandi
and the primary Astro site are copied from explicit source allowlists; remote
preview builds inherit their project's existing server settings, never env pull.
"""
from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
from urllib.parse import urlsplit
import uuid


ROOT = Path(__file__).resolve().parents[1]
GITHUB = ROOT.parent


def write_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    temporary.replace(path)


def public_origin(value: str) -> str:
    parsed = urlsplit(value)
    if parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password or parsed.query or parsed.fragment or parsed.path not in ("", "/"):
        raise ValueError("Expected a public HTTPS origin without credentials or path")
    return value.rstrip("/")


def copy_tree(source: Path, target: Path) -> None:
    def excluded(_directory: str, names: list[str]) -> list[str]:
        return [name for name in names if name in {"node_modules", ".next", ".vercel", ".git", ".artifacts", "__pycache__"} or name.startswith(".env") or ".test." in name]
    shutil.copytree(source, target, ignore=excluded)


def source_snapshot(source: Path, target: Path, files: list[str], directories: list[str]) -> None:
    for relative in files:
        destination = target / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source / relative, destination)
    for relative in directories:
        copy_tree(source / relative, target / relative)


def project_link(target: Path, project_id: str, team_id: str) -> None:
    write_json(target / ".vercel/project.json", {"projectId": project_id, "orgId": team_id})


def static_output(source: Path, target: Path) -> None:
    output = target / ".vercel/output"
    copy_tree(source, output / "static")
    if not (output / "static/index.web.html").is_file():
        raise RuntimeError("Missing SPA entrypoint")
    headers = {"X-Content-Type-Options": "nosniff", "X-Frame-Options": "DENY", "Referrer-Policy": "strict-origin-when-cross-origin", "Permissions-Policy": "camera=(), microphone=(), geolocation=(), payment=()", "X-Robots-Tag": "noindex, nofollow"}
    write_json(output / "config.json", {"version": 3, "routes": [
        {"src": "/(.*)", "headers": headers, "continue": True},
        {"src": "/assets/(.*)", "headers": {"Cache-Control": "public, max-age=31536000, immutable"}, "continue": True},
        {"handle": "filesystem"}, {"src": "/(.*)", "dest": "/index.web.html"},
    ]})


def spa_environment(api: str, settings: dict) -> dict:
    # Both Java clients append /me or /v1/... to their base. apiOrigins stores
    # only origins, so the deployment must supply the shared /api prefix.
    return {
        "VITE_API_BASE_URL": public_origin(api) + "/api",
        "VITE_CLERK_PUBLISHABLE_KEY": settings["clerk"]["publishableKey"],
        "VITE_CLERK_PRIMARY_URL": public_origin(settings["origins"]["primary"]),
        "VITE_BASE_PATH": "/",
    }


def build_spa(name: str, repository: str, project_id: str, api: str, settings: dict, output: Path) -> dict:
    desktop = GITHUB / repository / "desktop"
    environment = spa_environment(api, settings)
    target = output / name
    target.mkdir()
    write_json(target / "public-env.json", environment)
    with (target / "build.log").open("w", encoding="utf-8") as log:
        command = ["cmd", "/c", "npm", "run", "build:web"] if os.name == "nt" else ["npm", "run", "build:web"]
        result = subprocess.run(command, cwd=desktop, env={**os.environ, **environment}, stdout=log, stderr=subprocess.STDOUT, timeout=180, check=False)
    if result.returncode:
        raise RuntimeError(f"{name}: build failed; inspect its build.log")
    built = desktop / "dist-web"
    entry = (built / "index.web.html").read_text(encoding="utf-8")
    if api not in entry or settings["origins"]["primary"] not in entry or "%PRIMARY_ORIGIN%" in entry or "%API_ORIGIN%" in entry or "%CLERK_HOSTS%" in entry:
        raise RuntimeError(f"{name}: CSP does not match the configured public origins")
    static_output(built, target)
    project_link(target, project_id, settings["vercel"]["teamId"])
    if name == "associago":
        # CLI validates the existing monorepo root even for a prebuilt upload.
        marker = target / "gestionale/desktop/deployment-root.txt"
        marker.parent.mkdir(parents=True)
        marker.write_text("Existing Vercel rootDirectory; deploy the reviewed prebuilt output.\n", encoding="utf-8")
        # CLI57 also supports repo-linked output resolution under rootDirectory.
        copy_tree(target / ".vercel/output", marker.parent / ".vercel/output")
    return {"kind": "prebuilt-static", "projectId": project_id, "path": str(target), "publicEnvironment": environment, "state": "prepared", "nodeVersion": "22.x"}


def inventory(directory: Path) -> dict:
    files = []
    for path in sorted(directory.rglob("*")):
        if not path.is_file() or path.name == "build.log":
            continue
        relative = path.relative_to(directory).as_posix()
        if ".vercel" in path.relative_to(directory).parts and path.name.startswith("source.tgz"):
            continue
        if any(part.startswith(".env") or part in {"node_modules", ".next"} for part in Path(relative).parts) or relative.startswith("data/"):
            raise RuntimeError("Unexpected private/build directory in frontend snapshot")
        files.append({"path": relative, "size": path.stat().st_size, "sha256": hashlib.sha256(path.read_bytes()).hexdigest()})
    return {"count": len(files), "bytes": sum(item["size"] for item in files), "files": files}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--settings", type=Path, default=ROOT / "deployment/suite-public-settings.json")
    parser.add_argument("--api-associago", required=True)
    parser.add_argument("--api-librepm", required=True)
    parser.add_argument("--gateway-url")
    parser.add_argument("--bandi-project-id", required=True)
    args = parser.parse_args()
    settings = json.loads(args.settings.read_text(encoding="utf-8-sig"))
    run_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + uuid.uuid4().hex[:8]
    output = ROOT / "deployment/.artifacts" / ("frontends-" + run_id)
    output.mkdir(parents=True)
    state = {"status": "preparing", "stage": "spa-builds", "updated_at": datetime.now(timezone.utc).isoformat(), "runner_pid": os.getpid(), "root": str(output), "projects": {}}
    write_json(output / "state.json", state)
    try:
        with ThreadPoolExecutor(max_workers=2) as executor:
            tasks = {
                "associago": executor.submit(build_spa, "associago", "AssociaGo", settings["vercel"]["associagoProjectId"], args.api_associago, settings, output),
                "librepm": executor.submit(build_spa, "librepm", "LibrePM", settings["vercel"]["librepmProjectId"], args.api_librepm, settings, output),
            }
            for name, task in tasks.items():
                state["projects"][name] = task.result()
        state["stage"] = "source-snapshots"
        write_json(output / "state.json", state)
        bandi = output / "bandi"
        source_snapshot(GITHUB / "Bandi", bandi, ["package.json", "package-lock.json", "apps/web/package.json", "apps/web/next.config.ts", "apps/web/next-env.d.ts", "apps/web/tsconfig.json"], ["apps/web/src", "apps/web/public/fonts"])
        shutil.copy2(GITHUB / "Bandi/apps/web/public/robots.txt", bandi / "apps/web/public/robots.txt")
        project_link(bandi, args.bandi_project_id, settings["vercel"]["teamId"])
        bandi_environment = {"BANDI_MODE": "gateway", "COLLABORATION_API_URL": public_origin(args.gateway_url) if args.gateway_url else None, "NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY": settings["clerk"]["publishableKey"], "NEXT_PUBLIC_CLERK_PRIMARY_URL": settings["origins"]["primary"], "NEXT_PUBLIC_CLERK_DOMAIN": urlsplit(settings["origins"]["bandi"]).netloc, "NEXT_PUBLIC_CLERK_PRIMARY_SIGN_IN_PATH": "/admin", "NEXT_PUBLIC_ASSOCIAGO_URL": settings["origins"]["associago"], "NEXT_PUBLIC_LIBREPM_URL": settings["origins"]["librepm"]}
        write_json(bandi / "public-env.json", bandi_environment)
        state["projects"]["bandi"] = {"kind": "source-nextjs", "projectId": args.bandi_project_id, "path": str(bandi), "publicEnvironment": bandi_environment, "state": "prepared" if args.gateway_url else "awaiting-gateway", "nodeVersion": "22.x"}
        primary = output / "primary"
        source_snapshot(GITHUB / "terzo-settore", primary, ["vercel.json", "sito/package.json", "sito/package-lock.json", "sito/astro.config.ts", "sito/tsconfig.json"], ["sito/src", "sito/public", "sito/scripts"])
        write_json(primary / "package.json", {"name": "adf-primary-deployment", "private": True, "engines": {"node": "22.x"}})
        project_link(primary, settings["vercel"]["mainSiteProjectId"], settings["vercel"]["teamId"])
        primary_environment = {"PUBLIC_CLERK_PUBLISHABLE_KEY": settings["clerk"]["publishableKey"], "PUBLIC_BANDI_URL": settings["origins"]["bandi"] + "/collabora", "PUBLIC_PROGETTI_URL": settings["origins"]["librepm"], "PUBLIC_GESTIONALE_URL": settings["origins"]["associago"], "PUBLIC_CLERK_REDIRECT_ORIGINS": "https://adf-gestionale-collaudo.vercel.app,https://adf-bandi-collaudo.vercel.app,https://adf-bandi-portale.vercel.app"}
        write_json(primary / "public-env.json", primary_environment)
        state["projects"]["primary"] = {"kind": "source-astro", "projectId": settings["vercel"]["mainSiteProjectId"], "path": str(primary), "publicEnvironment": primary_environment, "state": "prepared", "nodeVersion": "22.x"}
        for source_target in (bandi, primary):
            (source_target / ".vercelignore").write_text("public-env.json\n*.log\n.env*\nnode_modules\n.next\n.git\n", encoding="utf-8")
        for name, project in state["projects"].items():
            write_json(output / f"{name}-inventory.json", inventory(Path(project["path"])))
        state.update(status="succeeded", stage="prepared-no-deployment", updated_at=datetime.now(timezone.utc).isoformat())
        write_json(output / "state.json", state)
        print(json.dumps({"root": str(output), "status": state["status"], "projects": {name: value["state"] for name, value in state["projects"].items()}}, indent=2))
    except BaseException:
        state.update(status="failed", stage="preparation-error", updated_at=datetime.now(timezone.utc).isoformat())
        write_json(output / "state.json", state)
        raise


if __name__ == "__main__":
    main()
