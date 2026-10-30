"""Prepare, then explicitly execute, Secret Manager and Cloud Run candidate steps.

Default mode writes a reviewable plan only. Secret bytes travel through stdin;
the runtime receives version-pinned references, never migration credentials.
"""

from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import time
import uuid
from urllib.parse import parse_qs, unquote, urlsplit

ROOT = Path(__file__).resolve().parent
ACTIVE_STATE = None
STATE_PATH = None
APPS = ("associago", "librepm", "bandiPortal")
REQUIRED = {
    "associago": {
        "SPRING_DATASOURCE_URL",
        "SPRING_DATASOURCE_USERNAME",
        "SPRING_DATASOURCE_PASSWORD",
        "SUITE_ASSOCIAGO_KEY",
    },
    "librepm": {
        "SPRING_DATASOURCE_URL",
        "SPRING_DATASOURCE_USERNAME",
        "SPRING_DATASOURCE_PASSWORD",
        "SUITE_ASSOCIAGO_KEY",
        "SUITE_BANDI_KEY",
    },
    "bandiPortal": {
        "COLLAB_DATABASE_URL",
        "COLLAB_NODE_KEY",
        "COLLAB_CRON_KEY",
        "SUITE_LIBREPM_KEY",
    },
}


class PreparationError(ValueError):
    pass


def load(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))


def identifier(value, pattern, label):
    if not isinstance(value, str) or re.fullmatch(pattern, value) is None:
        raise PreparationError("Invalid " + label)
    return value


def prepare(config, public, directory):
    if (
        public.get("supabasePlan") != "free"
        or public.get("allowPaidSupabase") is not False
    ):
        raise PreparationError(
            "The prepared rollout must preserve the authorized Free plan"
        )
    project = identifier(
        public["google"]["project"], r"[a-z][a-z0-9-]{4,62}", "Google project"
    )
    region = identifier(public["google"]["region"], r"[a-z]+-[a-z]+[0-9]", "region")
    suffix = identifier(
        config["revisionSuffix"], r"[a-z][a-z0-9-]{0,30}", "revision suffix"
    )
    identifier(config["projectRef"], r"[a-z0-9]{20}", "Supabase project reference")
    org = str(uuid.UUID(config["organizationId"]))
    owner = identifier(
        public["clerk"]["ownerSubject"], r"user_[A-Za-z0-9]+", "verified Clerk subject"
    )
    issuer = public["clerk"]["issuer"]
    if issuer != "https://distinct-oryx-35.clerk.accounts.dev":
        raise PreparationError("Unexpected Clerk issuer; review the shared settings")
    origins = list(public["origins"].values())
    if any(not re.fullmatch(r"https://[a-z0-9.-]+", value) for value in origins):
        raise PreparationError("Explicit HTTPS origins are required")
    urls = {**public.get("apiOrigins", {}), **config.get("backendOrigins", {})}
    if "bandiGateway" in urls and "bandiPortal" not in urls:
        urls["bandiPortal"] = urls["bandiGateway"]
    for name in ("associago", "librepm"):
        if not re.fullmatch(r"https://[a-z0-9.-]+", urls.get(name, "")):
            raise PreparationError("Explicit HTTPS backend origins are required")
    if urls.get("bandiPortal"):
        if not re.fullmatch(r"https://[a-z0-9.-]+", urls["bandiPortal"]):
            raise PreparationError("The verified gateway origin must be HTTPS")
    elif not config.get("initialServices", {}).get("bandiPortal", False):
        raise PreparationError(
            "A verified gateway origin is required after first creation"
        )
    if not str(config["associationId"]).isdigit() or int(config["associationId"]) < 1:
        raise PreparationError("Use the actual bootstrap association id")
    str(uuid.UUID(config["librepmOwnerId"]))
    service_fields = {
        "associago": "associagoService",
        "librepm": "librepmService",
        "bandiPortal": "bandiGatewayService",
    }
    image_fields = {
        "associago": "associago",
        "librepm": "librepm",
        "bandiPortal": "bandiGateway",
    }
    accounts = config["runtimeServiceAccounts"]
    if len(set(accounts.values())) != 3:
        raise PreparationError(
            "Each application needs a separate runtime service account"
        )
    secrets, services = {}, []
    for app in APPS:
        account = identifier(
            accounts[app],
            r"[a-z][a-z0-9-]{4,28}@"
            + re.escape(project)
            + r"\.iam\.gserviceaccount\.com",
            "runtime service account",
        )
        reference = config["runtimeBundles"][app]
        name = identifier(reference["name"], r"[a-z][a-z0-9_-]{2,100}", "bundle name")
        if any(
            word in name.lower() for word in ("owner", "migration", "admin-password")
        ):
            raise PreparationError("Owner/migration bundles cannot be bound to runtime")
        if str(reference["version"]) != "1":
            raise PreparationError(
                "Initial rollout requires explicitly pinned version 1"
            )
        if name in secrets:
            raise PreparationError(
                "Each application must have a separate runtime bundle"
            )
        secrets[name] = {
            "name": name,
            "application": app,
            "sourceFile": str(Path(reference["sourceFile"]).resolve()),
            "format": "dotenv" if app == "bandiPortal" else "spring-json",
        }
        env = {
            "CLERK_ISSUER_URI": issuer,
            "CORS_ALLOWED_ORIGINS": ",".join(origins),
            "SUITE_ENABLED": "true",
            "SUITE_ORGANIZATION_ID": org,
            "SUITE_ASSOCIAGO_API_URL": urls["associago"],
            "SUITE_LIBREPM_API_URL": urls["librepm"],
            "SUITE_ASSOCIAGO_PUBLIC_URL": public["origins"]["associago"],
            "SUITE_LIBREPM_PUBLIC_URL": public["origins"]["librepm"],
            "SUITE_BANDI_PUBLIC_URL": public["origins"]["bandi"],
        }
        if urls.get("bandiPortal"):
            env["SUITE_BANDI_API_URL"] = urls["bandiPortal"]
        if app == "bandiPortal":
            env.update(
                BANDI_MODE="gateway",
                APP_ENV="production",
                AGNO_ENABLED="false",
                CORS_ORIGINS=",".join(origins),
                COLLAB_CLERK_ISSUER=issuer,
                COLLAB_OWNER_SUBJECT=owner,
                COLLAB_NODE_ID=config["nodeId"],
                COLLAB_ALLOWED_SUBJECTS=config.get("allowedSubjects", ""),
                COLLAB_DATABASE_SCHEMA="bandi_portal",
                SSL_CERT_FILE="/etc/ssl/certs/ca-certificates.crt",
                SSL_CERT_DIR="/etc/ssl/certs",
            )
            if config.get("allowedOrganizationId"):
                env["COLLAB_ALLOWED_ORGANIZATION_ID"] = config["allowedOrganizationId"]
        else:
            env.update(
                SPRING_PROFILES_ACTIVE="cloud-postgres",
                SPRING_FLYWAY_ENABLED="false",
                SPRING_JPA_HIBERNATE_DDL_AUTO="validate",
                DB_POOL_MAX="3",
                DB_POOL_MIN="0",
                SUITE_IMPORT_OWNER_ID=config["librepmOwnerId"],
                SUITE_ASSOCIATION_ID=str(config["associationId"]),
            )
        if any(
            "MIGRATION" in key or ("FLYWAY" in key and key != "SPRING_FLYWAY_ENABLED")
            for key in env
        ):
            raise PreparationError("Migration configuration must not reach runtime")
        image = public["google"]["preparedImages"][image_fields[app]]
        if not re.fullmatch(
            re.escape(region)
            + r"-docker\.pkg\.dev/"
            + re.escape(project)
            + r"/[a-z0-9_/-]+@sha256:[0-9a-f]{64}",
            image,
        ):
            raise PreparationError(
                "Use a prepared, digest-pinned image from the shared project"
            )
        service = identifier(
            public["google"][service_fields[app]], r"[a-z][a-z0-9-]{1,45}", "service"
        )
        path = directory / (app + "-runtime-env.json")
        path.write_text(json.dumps(env, indent=2) + "\n", encoding="utf-8")
        command = [
            "run",
            "deploy",
            service,
            "--project=" + project,
            "--region=" + region,
            "--image=" + image,
            "--service-account=" + account,
            "--env-vars-file=" + str(path),
            "--set-secrets="
            + (
                "/run/secrets/gateway.env="
                if app == "bandiPortal"
                else "SPRING_APPLICATION_JSON="
            )
            + name
            + ":1",
            "--max=1",
            "--max-instances=1",
            "--min=0",
            "--min-instances=0",
            "--cpu=1",
            "--memory=" + ("512Mi" if app == "bandiPortal" else "1Gi"),
            "--concurrency=" + ("8" if app == "bandiPortal" else "20"),
            "--port=8080",
            "--timeout=300s",
            "--revision-suffix=" + suffix,
            "--tag=pg-candidate",
            "--quiet",
        ]
        if app == "bandiPortal":
            command.extend(
                [
                    "--command=uvicorn",
                    "--args=apps.api.main:app,--host,0.0.0.0,--port,8080,--no-access-log,--env-file,/run/secrets/gateway.env",
                ]
            )
        else:
            command.extend(["--command=", "--args="])
        initial = config.get("initialServices", {}).get(app, False)
        if type(initial) is not bool:
            raise PreparationError("initialServices values must be booleans")
        if not initial:
            command.append("--no-traffic")
        else:
            # Existing service IAM applies to all its revisions: leave it untouched.
            # A first gateway service still enforces JWT/node/cron auth in the app.
            command.append("--allow-unauthenticated")
        services.append(
            {
                "application": app,
                "service": service,
                "account": account,
                "initialService": initial,
                "peerOriginsReady": app == "bandiPortal"
                or bool(urls.get("bandiPortal")),
                "command": command,
                "secretNames": [name],
            }
        )
    return {
        "project": project,
        "projectRef": config["projectRef"],
        "region": region,
        "organizationId": org,
        "secrets": list(secrets.values()),
        "services": services,
        "status": "prepared-not-executed",
    }


def run_gcloud(arguments, *, payload=None, accepted=(0,)):
    executable = shutil.which("gcloud.cmd") or shutil.which("gcloud")
    if not executable:
        raise PreparationError("Google Cloud SDK is required")
    child = subprocess.Popen(
        [executable, *arguments],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    if ACTIVE_STATE is not None:
        ACTIVE_STATE.update(
            child_pid=child.pid, operation="gcloud:" + "/".join(arguments[:2])
        )
    deadline, first = time.monotonic() + 600, True
    try:
        while True:
            publish_state()
            try:
                stdout, stderr = child.communicate(
                    input=payload if first else None, timeout=2
                )
                break
            except subprocess.TimeoutExpired:
                first = False
                if time.monotonic() >= deadline:
                    if os.name == "nt":
                        subprocess.run(
                            ["taskkill", "/PID", str(child.pid), "/T", "/F"],
                            capture_output=True,
                            timeout=10,
                        )
                    else:
                        child.kill()
                    child.wait(timeout=10)
                    raise PreparationError(
                        "Google Cloud client timed out; inspect remote state before retrying"
                    ) from None
        result = subprocess.CompletedProcess(
            arguments, child.returncode, stdout, stderr
        )
    finally:
        if ACTIVE_STATE is not None:
            ACTIVE_STATE["child_pid"] = None
        publish_state()
    if result.returncode not in accepted:
        # Never echo stdout/stderr: provider errors can include configuration.
        raise PreparationError("Google Cloud operation failed: " + arguments[0])
    return result


def publish_state():
    if ACTIVE_STATE is None or STATE_PATH is None:
        return
    ACTIVE_STATE["updated_at"] = datetime.now(timezone.utc).isoformat()
    try:
        pending = STATE_PATH.with_suffix(".tmp")
        pending.write_text(json.dumps(ACTIVE_STATE, indent=2) + "\n", encoding="utf-8")
        pending.replace(STATE_PATH)
    except OSError:
        # A watcher/file sharing failure must not terminate an in-flight API operation.
        pass


def validate_runtime_payloads(config):
    values = {}
    if set(config["runtimeBundles"]) != set(APPS):
        raise PreparationError("Exactly three application bundles are required")
    for app, reference in config["runtimeBundles"].items():
        values[app] = json.loads(
            Path(reference["sourceFile"]).read_text(encoding="utf-8")
        )
        if not isinstance(values[app], dict) or set(values[app]) != REQUIRED[app]:
            raise PreparationError("Runtime bundle allowlist mismatch for " + app)
        for value in values[app].values():
            if (
                not isinstance(value, str)
                or not value
                or value != value.strip()
                or any(c in value for c in "\r\n\x00")
                or "${" in value
            ):
                raise PreparationError(
                    "Runtime values must be exact, nonempty single-line values"
                )
        if app == "bandiPortal":
            parsed = urlsplit(
                values[app]["COLLAB_DATABASE_URL"].replace(
                    "postgresql+psycopg://", "postgresql://", 1
                )
            )
            role = unquote(parsed.username or "")
            expected = "bandi_portal_app"
            allowed_query = {"sslmode", "connect_timeout", "sslrootcert"}
            if parse_qs(parsed.query).get("sslrootcert") != ["system"]:
                raise PreparationError(
                    "Gateway PostgreSQL requires the system trust store"
                )
        else:
            raw = values[app]["SPRING_DATASOURCE_URL"]
            if not raw.startswith("jdbc:postgresql://"):
                raise PreparationError("PostgreSQL JDBC is required")
            parsed = urlsplit(raw[5:])
            if parsed.username or parsed.password:
                raise PreparationError("JDBC credentials must be separate secrets")
            role = values[app]["SPRING_DATASOURCE_USERNAME"]
            expected = app + "_app"
            allowed_query = {
                "sslmode",
                "currentSchema",
                "connectTimeout",
                "socketTimeout",
                "sslfactory",
            }
            if parse_qs(parsed.query).get("sslfactory") != [
                "org.postgresql.ssl.DefaultJavaSSLFactory"
            ]:
                raise PreparationError("JDBC requires the JVM system trust store")
            if parse_qs(parsed.query).get("currentSchema") != [app]:
                raise PreparationError("Runtime JDBC must identify its private schema")
        host = (parsed.hostname or "").lower()
        if any(
            key not in allowed_query
            for key in parse_qs(parsed.query, keep_blank_values=True)
        ):
            raise PreparationError("Unexpected runtime connection parameter")
        direct = host == "db." + config["projectRef"] + ".supabase.co"
        pooler = host.endswith(".pooler.supabase.com")
        allowed_role = expected if direct else expected + "." + config["projectRef"]
        if (
            not (direct or pooler)
            or role != allowed_role
            or parsed.port not in (None, 5432)
        ):
            raise PreparationError(
                "Runtime must use its application role on the selected project"
            )
        if parsed.path != "/postgres" or parse_qs(parsed.query).get("sslmode") != [
            "verify-full"
        ]:
            raise PreparationError("Direct/session TLS PostgreSQL is required")
    node, cron = (
        values["bandiPortal"]["COLLAB_NODE_KEY"],
        values["bandiPortal"]["COLLAB_CRON_KEY"],
    )
    if len(node) < 32 or len(cron) < 32 or node == cron:
        raise PreparationError("Node and cron keys must be long and distinct")
    for app, key in (
        ("associago", "SUITE_ASSOCIAGO_KEY"),
        ("librepm", "SUITE_BANDI_KEY"),
    ):
        if len(values[app][key]) < 32:
            raise PreparationError("Suite peer keys require at least 32 characters")
    if (
        values["associago"]["SUITE_ASSOCIAGO_KEY"]
        != values["librepm"]["SUITE_ASSOCIAGO_KEY"]
        or values["librepm"]["SUITE_BANDI_KEY"]
        != values["bandiPortal"]["SUITE_LIBREPM_KEY"]
    ):
        raise PreparationError(
            "Peer keys must match across their two application bundles"
        )
    return values


def bundle_payload(app, values):
    if app == "bandiPortal":
        return (
            "\n".join(
                key + "=" + json.dumps(value, ensure_ascii=False)
                for key, value in sorted(values.items())
            )
            + "\n"
        ).encode("utf-8")
    return json.dumps(values, ensure_ascii=False, separators=(",", ":")).encode("utf-8")


def execute(plan, phase, allow_initial, config, *, applications=None):
    project = "--project=" + plan["project"]
    if phase == "secrets":
        bundles = validate_runtime_payloads(config)
        # Refuse updates: existing versions must never silently replace an old deployment.
        values = []
        for entry in plan["secrets"]:
            value = bundle_payload(entry["application"], bundles[entry["application"]])
            exists = run_gcloud(
                ["secrets", "describe", entry["name"], project, "--format=value(name)"],
                accepted=(0, 1),
            )
            if exists.returncode == 0:
                raise PreparationError(
                    "Secret already exists; review its pinned version explicitly"
                )
            values.append((entry["name"], value))
        for name, value in values:
            run_gcloud(
                [
                    "secrets",
                    "create",
                    name,
                    project,
                    "--replication-policy=automatic",
                    "--data-file=-",
                    "--quiet",
                ],
                payload=value,
            )
    elif phase == "iam":
        for item in plan["services"]:
            found = (
                run_gcloud(
                    [
                        "iam",
                        "service-accounts",
                        "list",
                        project,
                        "--filter=email=" + item["account"],
                        "--format=value(email)",
                    ]
                )
                .stdout.decode()
                .strip()
            )
            if found not in ("", item["account"]):
                raise PreparationError("Unexpected service account lookup result")
            if not found:
                run_gcloud(
                    [
                        "iam",
                        "service-accounts",
                        "create",
                        item["account"].split("@", 1)[0],
                        project,
                        "--quiet",
                    ]
                )
            for name in item["secretNames"]:
                run_gcloud(
                    [
                        "secrets",
                        "add-iam-policy-binding",
                        name,
                        project,
                        "--member=serviceAccount:" + item["account"],
                        "--role=roles/secretmanager.secretAccessor",
                        "--quiet",
                    ]
                )
    elif phase == "runtime":
        validate_runtime_payloads(config)
        selected = [
            item
            for item in plan["services"]
            if applications is None or item["application"] in applications
        ]
        if not selected or any(not item["peerOriginsReady"] for item in selected):
            raise PreparationError(
                "Native runtime requires the gateway origin verified after first creation"
            )
        if any(item["initialService"] for item in selected) and not allow_initial:
            raise PreparationError(
                "Explicit --allow-initial-service is required for a first service URL"
            )
        for item in selected:
            result = run_gcloud(
                [
                    "run",
                    "services",
                    "list",
                    project,
                    "--region=" + plan["region"],
                    "--filter=metadata.name=" + item["service"],
                    "--format=value(metadata.name)",
                ]
            )
            name = result.stdout.decode().strip()
            if (name not in ("", item["service"])) or bool(name) == item[
                "initialService"
            ]:
                raise PreparationError(
                    "Actual service existence does not match the explicit initial-service plan"
                )
        for item in selected:
            run_gcloud(item["command"])


def main():
    global ACTIVE_STATE, STATE_PATH
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", type=Path, required=True)
    parser.add_argument(
        "--public-settings", type=Path, default=ROOT / "suite-public-settings.json"
    )
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--execute", choices=("secrets", "iam", "runtime"))
    parser.add_argument("--allow-initial-service", action="store_true")
    parser.add_argument("--application", choices=APPS, action="append")
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=False)
    state = {
        "status": "running",
        "stage": "preparing",
        "runner_pid": os.getpid(),
    }
    ACTIVE_STATE, STATE_PATH = state, args.output / "state.json"
    publish_state()
    try:
        config = load(args.config)
        plan = prepare(config, load(args.public_settings), args.output.resolve())
        (args.output / "candidate-plan.json").write_text(
            json.dumps(plan, indent=2) + "\n", encoding="utf-8"
        )
        if args.execute:
            state["stage"] = args.execute
            if args.application and args.execute != "runtime":
                raise PreparationError(
                    "Application selection applies only to runtime deployment"
                )
            execute(
                plan,
                args.execute,
                args.allow_initial_service,
                config,
                applications=args.application,
            )
        state.update(status="succeeded", stage=args.execute or "prepared-not-executed")
        print("PASS " + state["stage"] + "; review output at " + str(args.output))
    except Exception as error:
        state.update(status="failed", error=type(error).__name__)
        raise SystemExit(
            "Preparation failed; no credentials are included in diagnostics"
        ) from None
    finally:
        publish_state()


if __name__ == "__main__":
    main()
