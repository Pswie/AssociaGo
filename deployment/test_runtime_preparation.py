"""Offline guards: isolated bundles, owner-only migrations, verified bootstrap."""

from __future__ import annotations

import copy
from contextlib import redirect_stderr, redirect_stdout
from datetime import datetime, timedelta, timezone
import hashlib
import io
import json
from pathlib import Path
from types import SimpleNamespace
import tempfile
import sys
import unittest
from unittest.mock import patch

from dotenv import dotenv_values

import deploy_runtime_candidates as deploy
import migrate_owner_once as migrate
import prepare_initial_bootstrap as bootstrap
import prepare_runtime_bundles as generator
import prepare_frontends as frontends
import render_initial_bootstrap as renderer


class PreparationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="runtime-preparation-test-")
        self.root = Path(self.temp.name)
        self.addCleanup(self.temp.cleanup)
        self.public = json.loads(
            (deploy.ROOT / "suite-public-settings.json").read_text()
        )
        self.reference = "abcdefghijklmnopqrst"
        self.config = {
            "projectRef": self.reference,
            "revisionSuffix": "pg-ci-synthetic",
            "organizationId": "9b8bd5f6-e4d9-4a1a-9f3d-44a3f969f401",
            "associationId": 1,
            "librepmOwnerId": "3adf2ef0-5372-40ea-ac1b-7985c02c61e6",
            "nodeId": "operator-pc",
            "backendOrigins": {
                key: "https://" + key.lower() + ".example.org" for key in deploy.APPS
            },
            "runtimeServiceAccounts": {
                key: name
                + "@"
                + self.public["google"]["project"]
                + ".iam.gserviceaccount.com"
                for key, name in (
                    ("associago", "adf-as-pg-runtime"),
                    ("librepm", "adf-lp-pg-runtime"),
                    ("bandiPortal", "adf-portal-pg-runtime"),
                )
            },
            "runtimeBundles": {},
        }
        self.values = {}
        host = "db." + self.reference + ".supabase.co"
        for app in deploy.APPS:
            data = {}
            for key in deploy.REQUIRED[app]:
                value = "synthetic-" + key + "-long-key-value-only"
                if key == "SUITE_LIBREPM_KEY":
                    value = "synthetic-SUITE_BANDI_KEY-long-key-value-only"
                if key == "SPRING_DATASOURCE_URL":
                    value = (
                        "jdbc:postgresql://"
                        + host
                        + ":5432/postgres?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory&currentSchema="
                        + app
                    )
                if key == "SPRING_DATASOURCE_USERNAME":
                    value = app + "_app"
                if key == "COLLAB_DATABASE_URL":
                    value = (
                        "postgresql+psycopg://bandi_portal_app:synthetic-long-database-password-only@"
                        + host
                        + ":5432/postgres?sslmode=verify-full&sslrootcert=system"
                    )
                data[key] = value
            self.values[app] = data
            self.config["runtimeBundles"][app] = {
                "name": "adf-" + app.lower() + "-runtime-v1",
                "version": 1,
                "sourceFile": str(self.root / (app + ".private.json")),
            }
        self.save()

    def save(self):
        for app, data in self.values.items():
            Path(self.config["runtimeBundles"][app]["sourceFile"]).write_text(
                json.dumps(data)
            )

    def test_three_bundles_pinned_isolated_owner_free_and_no_traffic(self):
        with patch.object(
            deploy, "run_gcloud", side_effect=AssertionError("No remote calls")
        ):
            plan = deploy.prepare(self.config, self.public, self.root)
        self.assertEqual(len(plan["secrets"]), 3)
        for service in plan["services"]:
            self.assertIn("--no-traffic", service["command"])
            self.assertIn("--max=1", service["command"])
            self.assertEqual(len(service["secretNames"]), 1)
            binding = next(
                value
                for value in service["command"]
                if value.startswith(("--set-secrets=", "--update-secrets="))
            )
            if service["application"] == "bandiPortal":
                self.assertTrue(binding.startswith("--update-secrets="))
                self.assertIn("/run/secrets/gateway.env=", binding)
                self.assertTrue(
                    any(
                        "--env-file,/run/secrets/gateway.env" in value
                        for value in service["command"]
                    )
                )
            else:
                self.assertIn("SPRING_APPLICATION_JSON=", binding)
                env = json.loads(
                    (
                        self.root / (service["application"] + "-runtime-env.json")
                    ).read_text()
                )
                self.assertEqual(env["SPRING_FLYWAY_ENABLED"], "false")
                self.assertEqual(env["DB_POOL_MAX"], "3")
        self.assertNotIn("synthetic-long", json.dumps(plan))
        deploy.validate_runtime_payloads(self.config)

    def gateway_service(self):
        def volume(name):
            return {
                "name": name,
                "secret": {
                    "secretName": self.config["runtimeBundles"]["bandiPortal"]["name"],
                    "items": [{"key": "1", "path": "gateway.env"}],
                },
            }

        return {
            "spec": {
                "template": {
                    "spec": {
                        "containers": [
                            {
                                "volumeMounts": [
                                    {"name": "mounted", "mountPath": "/run/secrets"}
                                ]
                            }
                        ],
                        "volumes": [volume("unused"), volume("mounted")],
                    }
                }
            }
        }

    def test_gateway_redeployment_rebuilds_exact_mount_atomically(self):
        plan = deploy.prepare(self.config, self.public, self.root)
        planned = copy.deepcopy(plan)
        service = self.gateway_service()
        spec = service["spec"]["template"]["spec"]
        gateway = next(
            item for item in plan["services"] if item["application"] == "bandiPortal"
        )
        deployed = []

        def fake(args, **kwargs):
            if args[:3] == ["run", "services", "list"]:
                return SimpleNamespace(stdout=gateway["service"].encode())
            if args[:3] == ["run", "services", "describe"]:
                return SimpleNamespace(stdout=json.dumps(service).encode())
            self.assertEqual(args[:2], ["run", "deploy"])
            deployed.append(args)
            self.assertIn("--no-traffic", args)
            self.assertTrue(any(arg.startswith("--update-secrets=") for arg in args))
            self.assertFalse(any(arg.startswith("--set-secrets=") for arg in args))
            self.assertIn("--remove-volume-mount=/run/secrets", args)
            spec["containers"][0]["volumeMounts"] = []
            for argument in args:
                if argument.startswith("--remove-volume="):
                    names = argument.split("=", 1)[1].split(",")
                    spec["volumes"] = [
                        item for item in spec["volumes"] if item["name"] not in names
                    ]
            self.assertEqual(spec["volumes"], [])
            generated = "generated-" + str(len(deployed))
            volume = self.gateway_service()["spec"]["template"]["spec"]["volumes"][0]
            volume["name"] = generated
            spec["volumes"] = [volume]
            spec["containers"][0]["volumeMounts"] = [
                {"name": generated, "mountPath": "/run/secrets"}
            ]
            return SimpleNamespace(stdout=b"")

        with patch.object(deploy, "run_gcloud", side_effect=fake):
            for _ in range(2):
                deploy.execute(
                    plan, "runtime", False, self.config, applications=["bandiPortal"]
                )
        self.assertIn("--remove-volume=mounted,unused", deployed[0])
        self.assertIn("--remove-volume=generated-1", deployed[1])
        self.assertEqual([item["name"] for item in spec["volumes"]], ["generated-2"])
        self.assertEqual(plan, planned)

    def test_gateway_cleanup_fails_before_deploy_for_unexpected_bindings(self):
        plan = deploy.prepare(self.config, self.public, self.root)
        gateway = next(
            item for item in plan["services"] if item["application"] == "bandiPortal"
        )
        changes = [
            lambda s: s["volumes"][0]["secret"].update(secretName="another-secret"),
            lambda s: s["volumes"][0]["secret"]["items"][0].update(key="latest"),
            lambda s: s["volumes"][0]["secret"]["items"][0].update(path="another.env"),
            lambda s: s["volumes"][0].update(emptyDir={}),
            lambda s: s["volumes"][0].update(name="mounted"),
            lambda s: s["containers"].append({}),
            lambda s: s["containers"][0].update(
                env=[{"name": "EXTRA_SECRET", "valueFrom": {"secretKeyRef": {}}}]
            ),
            lambda s: s["containers"][0]["volumeMounts"][0].update(name="missing"),
            lambda s: s["containers"][0]["volumeMounts"][0].update(mountPath="/other"),
            lambda s: s["containers"][0]["volumeMounts"][0].update(subPath="hidden"),
        ]
        for index, change in enumerate(changes):
            with self.subTest(configuration=index):
                service = self.gateway_service()
                change(service["spec"]["template"]["spec"])
                with patch.object(
                    deploy,
                    "run_gcloud",
                    side_effect=[
                        SimpleNamespace(stdout=gateway["service"].encode()),
                        SimpleNamespace(stdout=json.dumps(service).encode()),
                    ],
                ) as command:
                    with self.assertRaises(deploy.PreparationError):
                        deploy.execute(
                            plan,
                            "runtime",
                            False,
                            self.config,
                            applications=["bandiPortal"],
                        )
                    self.assertTrue(
                        all(
                            call.args[0][:2] != ["run", "deploy"]
                            for call in command.call_args_list
                        )
                    )

    def test_bundle_serialization_round_trips_exactly(self):
        for app, values in self.values.items():
            payload = deploy.bundle_payload(app, values)
            result = (
                dotenv_values(stream=io.StringIO(payload.decode()), interpolate=False)
                if app == "bandiPortal"
                else json.loads(payload)
            )
            self.assertEqual(result, values)

    def test_native_gateway_is_enabled_only_by_an_explicit_boolean(self):
        def environment():
            deploy.prepare(self.config, self.public, self.root)
            return json.loads((self.root / "bandiPortal-runtime-env.json").read_text())

        self.assertNotIn("COLLAB_NATIVE_ENABLED", environment())
        self.config["bandiNativeEnabled"] = True
        self.assertEqual(environment()["COLLAB_NATIVE_ENABLED"], "true")
        self.config["bandiNativeEnabled"] = "false"
        with self.assertRaises(deploy.PreparationError):
            environment()

    def test_cloud_catalog_explicit_flag_scopes_memory_and_environment(self):
        config = copy.deepcopy(self.config)
        config.update(bandiNativeEnabled=True, bandiOfficialSourcesEnabled=True)
        for enabled in (False, True):
            config["bandiCloudCatalogEnabled"] = enabled
            plan = deploy.prepare(config, self.public, self.root)
            for app in deploy.APPS:
                environment = json.loads((self.root / (app + "-runtime-env.json")).read_text())
                service = next(item for item in plan["services"] if item["application"] == app)
                self.assertEqual(
                    environment.get("COLLAB_CLOUD_CATALOG_ENABLED"),
                    "true" if app == "bandiPortal" and enabled else None,
                )
                expected = "2Gi" if app == "bandiPortal" and enabled else (
                    "512Mi" if app == "bandiPortal" else "1Gi"
                )
                self.assertIn("--memory=" + expected, service["command"])
                self.assertIn("--min=0", service["command"])
                self.assertIn("--max=1", service["command"])

    def test_cloud_catalog_flag_rejects_missing_prerequisites_and_non_booleans(self):
        for native, official, enabled in (
            (False, False, True), (True, False, True),
            (True, True, "true"), (True, True, 1),
        ):
            config = copy.deepcopy(self.config)
            config.update(bandiNativeEnabled=native, bandiOfficialSourcesEnabled=official,
                          bandiCloudCatalogEnabled=enabled)
            with self.assertRaises(deploy.PreparationError):
                deploy.prepare(config, self.public, self.root)

    def test_official_sources_flag_is_opt_in_and_only_reaches_the_gateway(self):
        for enabled in (None, False, True):
            with self.subTest(enabled=enabled):
                config = copy.deepcopy(self.config)
                config["bandiNativeEnabled"] = True
                if enabled is not None:
                    config["bandiOfficialSourcesEnabled"] = enabled
                with patch.object(
                    deploy, "run_gcloud", side_effect=AssertionError("No remote calls")
                ):
                    deploy.prepare(config, self.public, self.root)
                for app in deploy.APPS:
                    environment = json.loads(
                        (self.root / (app + "-runtime-env.json")).read_text()
                    )
                    if app == "bandiPortal" and enabled is True:
                        self.assertEqual(
                            environment["COLLAB_OFFICIAL_SOURCES_ENABLED"], "true"
                        )
                        self.assertEqual(environment["COLLAB_NATIVE_ENABLED"], "true")
                    else:
                        self.assertNotIn("COLLAB_OFFICIAL_SOURCES_ENABLED", environment)

    def test_official_sources_requires_strict_booleans_and_native_runtime(self):
        for enabled in ("true", "false", 0, 1, None, [], {}):
            with self.subTest(enabled=enabled):
                config = copy.deepcopy(self.config)
                config.update(
                    bandiNativeEnabled=True, bandiOfficialSourcesEnabled=enabled
                )
                with self.assertRaises(deploy.PreparationError):
                    deploy.prepare(config, self.public, self.root)
        config = copy.deepcopy(self.config)
        config.update(bandiNativeEnabled=False, bandiOfficialSourcesEnabled=True)
        with self.assertRaises(deploy.PreparationError):
            deploy.prepare(config, self.public, self.root)

    def test_frontend_official_flag_rejects_missing_prerequisites_before_build(self):
        base = [
            "prepare-frontends",
            "--api-associago",
            "https://associago.example",
            "--api-librepm",
            "https://librepm.example",
            "--bandi-project-id",
            "prj_synthetic",
            "--settings",
            str(self.root / "must-not-be-read.json"),
        ]
        for flags in (["--official-sources"], ["--official-sources", "--bandi-native"]):
            with self.subTest(flags=flags), patch.object(
                sys, "argv", base + flags
            ), patch.object(
                frontends,
                "ThreadPoolExecutor",
                side_effect=AssertionError("No build may start"),
            ) as executor, redirect_stderr(
                io.StringIO()
            ):
                with self.assertRaises(SystemExit) as error:
                    frontends.main()
                self.assertEqual(error.exception.code, 2)
                executor.assert_not_called()

    def test_frontend_flags_preserve_other_apps_and_select_the_expected_bandi_entry(
        self,
    ):
        settings = self.root / "frontend-settings.json"
        settings.write_text(json.dumps(self.public))
        github = self.root / "synthetic-github"
        robots = github / "Bandi/apps/web/public/robots.txt"
        robots.parent.mkdir(parents=True)
        robots.write_text("User-agent: *\nDisallow: /\n")

        def snapshot(_source, target, _files, _directories):
            target.mkdir(parents=True)
            if target.name == "bandi":
                logo = target / "apps/web/public/brand/logo-ufficiale.png"
                logo.parent.mkdir(parents=True)
                logo.write_bytes(b"synthetic-reviewed-logo")

        def build(name, _repository, project_id, api, public, output):
            target = output / name
            target.mkdir()
            return {
                "kind": "prebuilt-static",
                "projectId": project_id,
                "path": str(target),
                "state": "prepared",
                "publicEnvironment": frontends.spa_environment(api, public),
            }

        # Snapshot contents and logo approval have separate tests/release pins.
        # Keep this CLI contract test independent of sibling repositories/builds.
        reviewed_hash = (
            "24bfb608601d5a3127dc9ee59b09917de11e1857c1b42c9d9560d1c3a662fa5d"
        )
        synthetic_hashing = SimpleNamespace(
            sha256=lambda _value: SimpleNamespace(hexdigest=lambda: reviewed_hash)
        )
        for flags, suffix in (
            ([], "/collabora"),
            (["--bandi-native"], ""),
            (["--bandi-native", "--official-sources"], "/fonti"),
        ):
            with self.subTest(flags=flags):
                command = [
                    "prepare-frontends",
                    "--settings",
                    str(settings),
                    "--api-associago",
                    "https://associago.example",
                    "--api-librepm",
                    "https://librepm.example",
                    "--gateway-url",
                    "https://gateway.example",
                    "--bandi-project-id",
                    "prj_synthetic",
                    *flags,
                ]
                output = io.StringIO()
                with (
                    patch.object(sys, "argv", command),
                    patch.object(frontends, "ROOT", self.root / "frontend-root"),
                    patch.object(frontends, "GITHUB", github),
                    patch.object(frontends, "source_snapshot", side_effect=snapshot),
                    patch.object(frontends, "build_spa", side_effect=build),
                    patch.object(frontends, "hashlib", synthetic_hashing),
                    patch.object(
                        frontends, "inventory", return_value={"count": 0, "files": []}
                    ),
                    patch.object(
                        frontends.subprocess,
                        "run",
                        side_effect=AssertionError("No CLI"),
                    ),
                    redirect_stdout(output),
                ):
                    frontends.main()
                root = Path(json.loads(output.getvalue())["root"])
                state = json.loads((root / "state.json").read_text())
                self.assertEqual(state["status"], "succeeded")
                projects = state["projects"]
                self.assertEqual(
                    projects["primary"]["publicEnvironment"]["PUBLIC_BANDI_URL"],
                    self.public["origins"]["bandi"] + suffix,
                )
                for name, project in projects.items():
                    environment = project["publicEnvironment"]
                    self.assertNotIn("COLLAB_OFFICIAL_SOURCES_ENABLED", environment)
                    if name == "bandi" and "--official-sources" in flags:
                        self.assertEqual(
                            environment["NEXT_PUBLIC_OFFICIAL_SOURCES"], "1"
                        )
                        self.assertEqual(environment["BANDI_NATIVE_ENABLED"], "1")
                    else:
                        self.assertNotIn("NEXT_PUBLIC_OFFICIAL_SOURCES", environment)
                    if name in ("associago", "librepm"):
                        self.assertEqual(
                            environment,
                            frontends.spa_environment(
                                "https://" + name + ".example", self.public
                            ),
                        )

    def test_unreviewed_bundle_name_version_image_and_shared_accounts_are_rejected(
        self,
    ):
        changes = [
            lambda c: c["runtimeBundles"]["associago"].update(
                name="database-owner-bundle"
            ),
            lambda c: c["runtimeBundles"]["associago"].update(version="latest"),
            lambda c: c["runtimeServiceAccounts"].update(
                librepm=c["runtimeServiceAccounts"]["associago"]
            ),
        ]
        for change in changes:
            value = copy.deepcopy(self.config)
            change(value)
            with self.assertRaises(deploy.PreparationError):
                deploy.prepare(value, self.public, self.root)
        public = copy.deepcopy(self.public)
        public["google"]["preparedImages"]["associago"] = "unreviewed:latest"
        with self.assertRaises(deploy.PreparationError):
            deploy.prepare(self.config, public, self.root)

    def test_owner_keys_role_overrides_and_peer_mismatch_cannot_reach_runtime(self):
        changes = [
            lambda: self.values["associago"].update(SPRING_FLYWAY_PASSWORD="forbidden"),
            lambda: self.values["associago"].update(
                SPRING_DATASOURCE_USERNAME="associago_owner"
            ),
            lambda: self.values["associago"].update(
                SPRING_DATASOURCE_URL=self.values["associago"]["SPRING_DATASOURCE_URL"]
                + "&user=postgres"
            ),
            lambda: self.values["bandiPortal"].update(
                SUITE_LIBREPM_KEY="another-long-peer-key-value-not-the-same"
            ),
        ]
        baseline = copy.deepcopy(self.values)
        for change in changes:
            self.values = copy.deepcopy(baseline)
            change()
            self.save()
            with self.assertRaises(deploy.PreparationError):
                deploy.validate_runtime_payloads(self.config)

    def test_initial_service_requires_explicit_permission_and_matching_remote_existence(
        self,
    ):
        self.config["initialServices"] = {"bandiPortal": True}
        plan = deploy.prepare(self.config, self.public, self.root)
        with patch.object(deploy, "run_gcloud") as command:
            with self.assertRaises(deploy.PreparationError):
                deploy.execute(plan, "runtime", False, self.config)
            command.assert_not_called()
        with patch.object(
            deploy, "run_gcloud", return_value=SimpleNamespace(stdout=b"")
        ) as command:
            with self.assertRaises(deploy.PreparationError):
                deploy.execute(plan, "runtime", True, self.config)
            self.assertTrue(
                all(
                    call.args[0][1:3] == ["services", "list"]
                    for call in command.call_args_list
                )
            )

    def test_secret_payload_only_goes_to_stdin_and_iam_is_per_bundle(self):
        plan = deploy.prepare(self.config, self.public, self.root)

        def fake(args, **kwargs):
            if args[:2] == ["secrets", "describe"]:
                return SimpleNamespace(returncode=1, stdout=b"")
            return SimpleNamespace(returncode=0, stdout=b"")

        with patch.object(deploy, "run_gcloud", side_effect=fake) as command:
            deploy.execute(plan, "secrets", False, self.config)
            created = [
                call
                for call in command.call_args_list
                if call.args[0][:2] == ["secrets", "create"]
            ]
            self.assertEqual(len(created), 3)
            self.assertTrue(all(call.kwargs.get("payload") for call in created))
            self.assertTrue(
                all(
                    "synthetic-long" not in " ".join(call.args[0])
                    for call in command.call_args_list
                )
            )
        with patch.object(deploy, "run_gcloud", side_effect=fake) as command:
            deploy.execute(plan, "iam", False, self.config)
            grants = [
                call.args[0]
                for call in command.call_args_list
                if call.args[0][:2] == ["secrets", "add-iam-policy-binding"]
            ]
            self.assertEqual(len(grants), 3)

    def test_owner_migration_target_requires_verified_tls_project_owner_and_schema(
        self,
    ):
        base = (
            "jdbc:postgresql://db."
            + self.reference
            + ".supabase.co:5432/postgres?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory&currentSchema=associago"
        )
        migrate.validate_target(
            base, "associago_owner", "associago", self.reference, jdbc=True
        )
        for value, user in (
            (base.replace("verify-full", "require"), "associago_owner"),
            (base.replace(":5432/", ":6543/"), "associago_owner"),
            (base, "associago_app"),
            (base + "&password=forged", "associago_owner"),
        ):
            with self.assertRaises(ValueError):
                migrate.validate_target(
                    value, user, "associago", self.reference, jdbc=True
                )

    def test_bootstrap_requires_exact_verified_subject_real_name_and_no_fiscal_invention(
        self,
    ):
        identity = {
            "userId": self.public["clerk"]["ownerSubject"],
            "firstName": "Actual",
            "lastName": "Person",
            "primaryEmail": "synthetic@example.invalid",
            "emailVerified": True,
        }
        self.assertEqual(
            bootstrap.parameters(identity, self.public)["ADF_OWNER_SUBJECT"],
            identity["userId"],
        )
        for changes in (
            {"userId": "user_not_the_owner"},
            {"emailVerified": False},
            {"lastName": ""},
        ):
            with self.assertRaises(ValueError):
                bootstrap.parameters({**identity, **changes}, self.public)
        sql = (deploy.ROOT / "initial_owner_bootstrap.psql").read_text()
        self.assertIn("requires empty identity tables", sql)
        self.assertNotIn("tax_code", sql)
        self.assertNotIn("'PRESIDENT'", sql)

    def test_generator_keeps_owner_and_backup_credentials_out_of_runtime(self):
        roles = {
            "projectRef": self.reference,
            "roles": {
                schema + suffix: "synthetic-password-" + schema + suffix
                for schema in ("associago", "librepm", "bandi_portal")
                for suffix in ("_owner", "_app")
            },
        }
        roles["roles"]["backup_readonly"] = "not-for-runtime"
        keys = {
            "associagoPeerKey": "long-associago-peer-key-for-synthetic-use",
            "bandiPeerKey": "long-bandi-peer-key-for-synthetic-use",
            "nodeKey": "long-local-node-key-for-synthetic-use",
            "cronKey": "long-cron-key-distinct-for-synthetic-use",
        }
        bundles, owners = generator.generate(
            roles, keys, "provided-host.pooler.supabase.com"
        )
        self.assertEqual(set(bundles), set(deploy.APPS))
        for app, data in bundles.items():
            Path(self.config["runtimeBundles"][app]["sourceFile"]).write_text(
                json.dumps(data)
            )
            self.assertNotIn("backup_readonly", json.dumps(data))
            self.assertNotIn("_owner", json.dumps(data))
        deploy.validate_runtime_payloads(self.config)
        migrate.validate(owners, self.public)
        with self.assertRaises(ValueError):
            generator.generate(roles, keys, "guessed.example.org")

    def test_ca_requires_exact_pin_and_uses_only_a_temporary_process_truststore(self):
        from cryptography import x509
        from cryptography.hazmat.primitives import hashes, serialization
        from cryptography.hazmat.primitives.asymmetric import rsa
        from cryptography.x509.oid import NameOID

        key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        name = x509.Name(
            [x509.NameAttribute(NameOID.COMMON_NAME, "Synthetic local CA test")]
        )
        now = datetime.now(timezone.utc)
        cert = (
            x509.CertificateBuilder()
            .subject_name(name)
            .issuer_name(name)
            .public_key(key.public_key())
            .serial_number(x509.random_serial_number())
            .not_valid_before(now - timedelta(days=1))
            .not_valid_after(now + timedelta(days=1))
            .add_extension(
                x509.BasicConstraints(ca=True, path_length=None), critical=True
            )
            .sign(key, hashes.SHA256())
        )
        ca = self.root / "synthetic-ca.crt"
        ca.write_bytes(cert.public_bytes(serialization.Encoding.PEM))
        pin = hashlib.sha256(ca.read_bytes()).hexdigest()
        self.assertEqual(migrate.validate_ca(ca, pin), ca.resolve())
        with self.assertRaises(ValueError):
            migrate.validate_ca(ca, "0" * 64)
        with patch.object(migrate, "command", return_value="") as call:
            store, password = migrate.create_trust_store(ca, self.root)
            self.assertNotIn(password, call.call_args.args[0])
            self.assertEqual(
                call.call_args.kwargs["env"]["ADF_TEMP_TRUSTSTORE_PASSWORD"], password
            )
            self.assertEqual(store.name, "provider-trust.jks")

    def test_mcp_renderer_quotes_names_and_never_regenerates_application_ids(self):
        identity = {
            "userId": self.public["clerk"]["ownerSubject"],
            "firstName": "D'Actual",
            "lastName": "Person $adf_bootstrap$",
            "primaryEmail": "synthetic@example.invalid",
            "emailVerified": True,
        }
        parameters = bootstrap.parameters(identity, self.public)
        sql = renderer.render(identity, parameters, self.public)
        self.assertIn("D''Actual", sql)
        self.assertIn(parameters["ADF_LIBREPM_OWNER_ID"], sql)
        self.assertNotIn("\\gset", sql)
        self.assertNotIn("CREATE TEMP", sql)
        self.assertNotIn(identity["primaryEmail"], sql.split("COMMIT;", 1)[1])
        with self.assertRaises(ValueError):
            renderer.render(
                identity,
                {**parameters, "ADF_OWNER_EMAIL": "another@example.invalid"},
                self.public,
            )

    def test_first_gateway_does_not_invent_its_url_and_native_runtime_waits_for_verified_origin(
        self,
    ):
        self.config["initialServices"] = {"bandiPortal": True}
        self.config["backendOrigins"].pop("bandiPortal")
        public = copy.deepcopy(self.public)
        for key in ("bandiPortal", "bandiGateway"):
            public.get("apiOrigins", {}).pop(key, None)
        plan = deploy.prepare(self.config, public, self.root)
        gateway = next(
            item for item in plan["services"] if item["application"] == "bandiPortal"
        )
        self.assertTrue(gateway["peerOriginsReady"])
        self.assertIn("--allow-unauthenticated", gateway["command"])
        with patch.object(
            deploy, "run_gcloud", return_value=SimpleNamespace(stdout=b"")
        ) as command:
            deploy.execute(
                plan, "runtime", True, self.config, applications=["bandiPortal"]
            )
            self.assertEqual(command.call_count, 2)
        with patch.object(deploy, "run_gcloud") as command:
            with self.assertRaises(deploy.PreparationError):
                deploy.execute(plan, "runtime", True, self.config)
            command.assert_not_called()
        for app in ("associago", "librepm"):
            env = json.loads((self.root / (app + "-runtime-env.json")).read_text())
            self.assertNotIn("SUITE_BANDI_API_URL", env)


if __name__ == "__main__":
    unittest.main()
