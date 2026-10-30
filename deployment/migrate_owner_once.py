"""Explicit one-shot owner migrations from the already tested application images.

Default is validation only, with no database connection. --apply runs Flyway via
its Java API and the gateway's separate migration module, never the business apps.
No owner credentials are uploaded to Google or put in subprocess arguments.
"""

from __future__ import annotations

import argparse
import hashlib
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess
import tempfile
from urllib.parse import parse_qs, parse_qsl, urlencode, urlsplit, urlunsplit

from backup_cloud_schemas import protect_directory

ROOT = Path(__file__).resolve().parent


def validate_target(url, username, schema, project_ref, *, jdbc):
    raw = (
        url.removeprefix("jdbc:")
        if jdbc
        else url.replace("postgresql+psycopg://", "postgresql://", 1)
    )
    parsed = urlsplit(raw)
    if (
        parsed.scheme != "postgresql"
        or parsed.port not in (None, 5432)
        or parsed.path != "/postgres"
    ):
        raise ValueError(
            "Use direct/session PostgreSQL on database postgres, port 5432"
        )
    query = parse_qs(parsed.query, keep_blank_values=True)
    if query.get("sslmode") != ["verify-full"]:
        raise ValueError("Verified TLS is required")
    allowed = (
        {"sslmode", "currentSchema", "connectTimeout", "socketTimeout", "sslfactory"}
        if jdbc
        else {"sslmode", "connect_timeout", "sslrootcert"}
    )
    if any(key not in allowed for key in query):
        raise ValueError("Unexpected connection parameter")
    if jdbc and query.get("sslfactory") != ["org.postgresql.ssl.DefaultJavaSSLFactory"]:
        raise ValueError("The JVM system trust store is required")
    if not jdbc and query.get("sslrootcert") != ["system"]:
        raise ValueError("The libpq system trust store is required")
    host = (parsed.hostname or "").lower()
    expected = schema + "_owner"
    if host == "db." + project_ref + ".supabase.co":
        if username != expected:
            raise ValueError("The schema owner role is required")
    elif host.endswith(".pooler.supabase.com"):
        if username != expected + "." + project_ref:
            raise ValueError("The pooler role must identify the same project")
    else:
        raise ValueError("The connection must identify the selected Supabase project")
    if jdbc and (parsed.username or parsed.password):
        raise ValueError("JDBC credentials must be separate from the URL")
    if not jdbc and parsed.username != username:
        raise ValueError("Owner URL identity mismatch")
    if jdbc and query.get("currentSchema") != [schema]:
        raise ValueError("The JDBC URL must identify its private schema")


def validate(config, public):
    reference = config["projectRef"]
    if not re.fullmatch(r"[a-z0-9]{20}", reference):
        raise ValueError("A real Supabase project reference is required")
    for schema in ("associago", "librepm"):
        data = config[schema]
        validate_target(
            data["jdbcUrl"], data["ownerUser"], schema, reference, jdbc=True
        )
        if not data.get("ownerPassword") or any(
            x in data["ownerPassword"] for x in "\r\n\x00"
        ):
            raise ValueError("A nonempty owner password is required")
    data = config["bandi_portal"]
    validate_target(
        data["ownerUrl"], data["ownerUser"], "bandi_portal", reference, jdbc=False
    )
    if not urlsplit(
        data["ownerUrl"].replace("postgresql+psycopg://", "postgresql://", 1)
    ).password:
        raise ValueError("The gateway owner URL requires a password")
    for key in ("associago", "librepm", "bandiGateway"):
        if not re.fullmatch(
            r"[a-z0-9./_-]+@sha256:[0-9a-f]{64}",
            public["google"]["preparedImages"][key],
        ):
            raise ValueError("Digest-pinned prepared images are required")


def command(arguments, *, timeout=120, env=None):
    result = subprocess.run(arguments, capture_output=True, timeout=timeout, env=env)
    if result.returncode:
        raise RuntimeError("Owner migration subprocess failed (output suppressed)")
    return result.stdout.decode("utf-8", errors="replace")


def property_value(value):
    return (
        value.replace("\\", "\\\\")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
    )


def validate_ca(path, expected_sha256):
    from cryptography import x509

    path = Path(path)
    if not re.fullmatch(r"[0-9a-f]{64}", expected_sha256):
        raise ValueError("An independently verified CA SHA256 pin is required")
    raw = path.read_bytes()
    if (
        hashlib.sha256(raw).hexdigest() != expected_sha256
        or raw.count(b"-----BEGIN CERTIFICATE-----") != 1
    ):
        raise ValueError("Provider CA fingerprint/content mismatch")
    certificate = x509.load_pem_x509_certificate(raw)
    if not certificate.extensions.get_extension_for_class(
        x509.BasicConstraints
    ).value.ca:
        raise ValueError("The pinned certificate is not a CA")
    now = datetime.now(timezone.utc)
    if not certificate.not_valid_before_utc <= now <= certificate.not_valid_after_utc:
        raise ValueError("The provider CA is outside its validity interval")
    return path.resolve()


def create_trust_store(ca_file, root):
    keytool = shutil.which("keytool")
    if not keytool:
        raise RuntimeError(
            "JDK keytool is required for the temporary process trust store"
        )
    password = secrets.token_urlsafe(32)
    store = root / "provider-trust.jks"
    command(
        [
            keytool,
            "-importcert",
            "-noprompt",
            "-alias",
            "supabase-provider",
            "-file",
            str(ca_file),
            "-keystore",
            str(store),
            "-storetype",
            "JKS",
            "-storepass:env",
            "ADF_TEMP_TRUSTSTORE_PASSWORD",
        ],
        env={**os.environ, "ADF_TEMP_TRUSTSTORE_PASSWORD": password},
        timeout=30,
    )
    return store, password


def java_migration(schema, data, image, root, *, trust_store=None):
    identifier = None
    target = root / schema
    target.mkdir()
    try:
        identifier = command(
            ["docker", "create", "--pull=never", "--entrypoint", "/bin/true", image]
        ).strip()
        if not re.fullmatch(r"[0-9a-f]{64}", identifier):
            raise RuntimeError("Unverified extraction container id")
        command(
            ["docker", "cp", identifier + ":/app/BOOT-INF/lib", str(target / "lib")]
        )
        command(
            [
                "docker",
                "cp",
                identifier + ":/app/BOOT-INF/classes/db/migration_postgresql",
                str(target / "sql"),
            ]
        )
    finally:
        if identifier and re.fullmatch(r"[0-9a-f]{64}", identifier):
            command(["docker", "rm", "--volumes", identifier], timeout=30)
    javac, java = shutil.which("javac"), shutil.which("java")
    if not javac or not java:
        raise RuntimeError("JDK21 is required for the standalone migration runner")
    classes = target / "runner"
    classes.mkdir()
    libraries = str(target / "lib" / "*")
    command(
        [
            javac,
            "--release",
            "21",
            "-cp",
            libraries,
            "-d",
            str(classes),
            str(ROOT / "OwnerMigration.java"),
        ]
    )
    private = target / "owner.properties"
    properties = {
        "schema": schema,
        "url": data["jdbcUrl"],
        "user": data["ownerUser"],
        "password": data["ownerPassword"],
    }
    tls_args = []
    if trust_store:
        properties["trustStorePassword"] = trust_store[1]
        tls_args = [
            "-Djavax.net.ssl.trustStore=" + str(trust_store[0]),
            "-Djavax.net.ssl.trustStoreType=JKS",
        ]
    private.write_text(
        "\n".join(
            key + "=" + property_value(value) for key, value in properties.items()
        ),
        encoding="utf-8",
    )
    try:
        output = command(
            [
                java,
                *tls_args,
                "-cp",
                str(classes) + os.pathsep + libraries,
                "OwnerMigration",
                str(private),
                str(target / "sql"),
            ],
            timeout=180,
        )
        match = re.search(r"OWNER_MIGRATION_OK:([0-9]+)", output)
        if match is None:
            raise RuntimeError("Migration did not publish its completion receipt")
        return {
            "schema": schema,
            "migrationsExecuted": int(match.group(1)),
            "image": image,
        }
    finally:
        private.unlink(missing_ok=True)


def gateway_migration(
    data, image, root, public, *, local_test_network=None, ca_file=None
):
    private = root / "gateway-owner.json"
    owner_url = data["ownerUrl"]
    ca_mount = []
    if ca_file:
        parsed = urlsplit(owner_url)
        query = dict(parse_qsl(parsed.query))
        query["sslrootcert"] = "/run/private/provider-ca.crt"
        owner_url = urlunsplit(parsed._replace(query=urlencode(query)))
        ca_mount = [
            "--mount",
            "type=bind,src="
            + str(ca_file)
            + ",dst=/run/private/provider-ca.crt,readonly",
        ]
    private.write_text(
        json.dumps(
            {
                "app_env": "production",
                "bandi_mode": "gateway",
                "agno_enabled": False,
                "collab_database_url": owner_url,
                "collab_migration_database_url": owner_url,
                "collab_clerk_issuer": public["clerk"]["issuer"],
                "collab_owner_subject": public["clerk"]["ownerSubject"],
                # Required configuration only: this process never constructs the HTTP app or a node row.
                "collab_node_id": "initial-schema-migration",
                "collab_node_key": secrets.token_urlsafe(40),
            }
        ),
        encoding="utf-8",
    )
    if os.name != "nt":
        # Host parent remains 0700; only this read-only file is mounted for UID1001.
        private.chmod(0o444)
    script = (
        "import json; from apps.api.config import Settings; "
        "from apps.api.collaboration.migrate import migrate_portal; "
        "migrate_portal(Settings(_env_file=None,**json.load(open('/run/private/gateway-owner.json')))); "
        "print('PORTAL_MIGRATION_OK')"
    )
    name = "adf-portal-migration-" + secrets.token_hex(6)
    cid = root / "portal-container.cid"
    network = []
    if local_test_network is not None:
        if not re.fullmatch(r"container:[0-9a-f]{64}", local_test_network):
            raise ValueError("Only an explicit test container network is accepted")
        network = ["--network", local_test_network]
    try:
        output = command(
            [
                "docker",
                "run",
                "--name",
                name,
                "--cidfile",
                str(cid),
                "--rm",
                "--pull=never",
                "--read-only",
                "--cap-drop=ALL",
                "--security-opt=no-new-privileges",
                *network,
                *ca_mount,
                "--mount",
                "type=bind,src="
                + str(private)
                + ",dst=/run/private/gateway-owner.json,readonly",
                "--entrypoint=python",
                image,
                "-c",
                script,
            ],
            timeout=180,
        )
        if "PORTAL_MIGRATION_OK" not in output:
            raise RuntimeError("Portal migration did not publish completion")
        return {"schema": "bandi_portal", "image": image}
    finally:
        # Only the immutable id emitted by our own invocation can be stopped.
        identifier = cid.read_text().strip() if cid.is_file() else ""
        if re.fullmatch(r"[0-9a-f]{64}", identifier):
            subprocess.run(
                ["docker", "rm", "--force", "--volumes", identifier],
                capture_output=True,
                timeout=30,
            )
        private.unlink(missing_ok=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", type=Path, required=True)
    parser.add_argument(
        "--public-settings", type=Path, default=ROOT / "suite-public-settings.json"
    )
    parser.add_argument("--receipt", type=Path, required=True)
    parser.add_argument("--apply", action="store_true")
    parser.add_argument(
        "--schema",
        choices=("associago", "librepm", "bandi_portal"),
        action="append",
        help="Migrate only the selected schemas; default: all three",
    )
    parser.add_argument("--ca-file", type=Path)
    parser.add_argument("--ca-sha256")
    args = parser.parse_args()
    selected = args.schema or ["associago", "librepm", "bandi_portal"]
    if len(selected) != len(set(selected)):
        parser.error("Select each schema only once")
    config, public = json.loads(args.config.read_text()), json.loads(
        args.public_settings.read_text()
    )
    state = {
        "status": "running",
        "stage": "validation",
        "schemas": [],
        "runner_pid": os.getpid(),
        "selectedSchemas": selected,
    }
    try:
        validate(config, public)
        ca_file = None
        if (
            args.ca_file
            or args.ca_sha256
            or config.get("caFile")
            or config.get("caSha256")
            or args.apply
        ):
            ca_file = validate_ca(
                args.ca_file or config.get("caFile", ""),
                args.ca_sha256 or config.get("caSha256", ""),
            )
        state["providerCAValidated"] = ca_file is not None
        if args.apply:
            with tempfile.TemporaryDirectory(prefix="adf-owner-migrations-") as path:
                root = Path(path)
                protect_directory(root)
                trust_store = create_trust_store(ca_file, root)
                for schema in ("associago", "librepm"):
                    if schema not in selected:
                        continue
                    state["stage"] = schema
                    state["schemas"].append(
                        java_migration(
                            schema,
                            config[schema],
                            public["google"]["preparedImages"][schema],
                            root,
                            trust_store=trust_store,
                        )
                    )
                if "bandi_portal" in selected:
                    state["stage"] = "bandi_portal"
                    state["schemas"].append(
                        gateway_migration(
                            config["bandi_portal"],
                            public["google"]["preparedImages"]["bandiGateway"],
                            root,
                            public,
                            ca_file=ca_file,
                        )
                    )
        state.update(
            status="succeeded",
            stage="migrated" if args.apply else "validated-no-database-connection",
        )
        print("PASS " + state["stage"])
    except Exception as error:
        state.update(status="failed", error=type(error).__name__)
        raise SystemExit(
            "Owner migration failed; credentials and subprocess output are suppressed"
        ) from None
    finally:
        state["updated_at"] = datetime.now(timezone.utc).isoformat()
        args.receipt.parent.mkdir(parents=True, exist_ok=True)
        args.receipt.write_text(json.dumps(state, indent=2) + "\n")


if __name__ == "__main__":
    main()
