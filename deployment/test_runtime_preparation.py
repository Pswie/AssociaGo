"""Offline guards: isolated bundles, owner-only migrations, verified bootstrap."""

from __future__ import annotations

import copy
from datetime import datetime, timedelta, timezone
import hashlib
import io
import json
from pathlib import Path
from types import SimpleNamespace
import tempfile
import unittest
from unittest.mock import patch

from dotenv import dotenv_values

import deploy_runtime_candidates as deploy
import migrate_owner_once as migrate
import prepare_initial_bootstrap as bootstrap
import prepare_runtime_bundles as generator
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
                if value.startswith("--set-secrets=")
            )
            if service["application"] == "bandiPortal":
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

    def test_bundle_serialization_round_trips_exactly(self):
        for app, values in self.values.items():
            payload = deploy.bundle_payload(app, values)
            result = (
                dotenv_values(stream=io.StringIO(payload.decode()), interpolate=False)
                if app == "bandiPortal"
                else json.loads(payload)
            )
            self.assertEqual(result, values)

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
