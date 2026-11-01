"""Inert rollout guards; every synthetic fixture is retained for review."""

from __future__ import annotations

import copy
import base64
import hashlib
import json
from pathlib import Path
import unittest
import uuid
from unittest.mock import patch

import deploy_runtime_candidates as runtime
import prepare_cloudflare_gateway as cloudflare
import build_runtime


class CloudflareGatewayTests(unittest.TestCase):
    def test_cli_readback_decodes_raw_payload_without_changing_newlines(self):
        payload = b'SYNTHETIC_KEY="synthetic-only"\nSECOND="value"\n'
        encoded = base64.urlsafe_b64encode(payload) + b"\r\n"
        self.assertEqual(cloudflare.decode_secret_payload(encoded), payload)
        self.assertNotEqual(payload.replace(b"\n", b"\r\n"), payload)
        with self.assertRaises(runtime.PreparationError):
            cloudflare.decode_secret_payload(b"invalid base64 payload")

    def setUp(self):
        self.root = (
            runtime.ROOT
            / ".artifacts"
            / ("test-cloudflare-gateway-" + uuid.uuid4().hex)
        )
        self.root.mkdir(parents=True)
        self.public = runtime.load(runtime.ROOT / "suite-public-settings.json")
        self.config = {
            "projectRef": "abcdefghijklmnopqrst",
            "revisionSuffix": "cf-ci-base",
            "organizationId": "9b8bd5f6-e4d9-4a1a-9f3d-44a3f969f401",
            "associationId": 1,
            "librepmOwnerId": "3adf2ef0-5372-40ea-ac1b-7985c02c61e6",
            "nodeId": "operator-pc",
            "bandiNativeEnabled": True,
            "bandiOfficialSourcesEnabled": True,
            "bandiCloudCatalogEnabled": True,
            "backendOrigins": {
                name: "https://" + name.lower() + ".example.org"
                for name in runtime.APPS
            },
            "runtimeServiceAccounts": {},
            "runtimeBundles": {},
        }
        for name in runtime.APPS:
            self.config["runtimeServiceAccounts"][name] = (
                "adf-"
                + name.lower()
                + "@"
                + self.public["google"]["project"]
                + ".iam.gserviceaccount.com"
            )
            values = {
                key: "synthetic-" + key + "-long-key-value-only"
                for key in runtime.REQUIRED[name]
            }
            if name == "bandiPortal":
                values["COLLAB_DATABASE_URL"] = (
                    "postgresql+psycopg://bandi_portal_app:synthetic-password-only@db.abcdefghijklmnopqrst.supabase.co:5432"
                    "/postgres?sslmode=verify-full&sslrootcert=system"
                )
                values["SUITE_LIBREPM_KEY"] = (
                    "synthetic-SUITE_BANDI_KEY-long-key-value-only"
                )
            else:
                values["SPRING_DATASOURCE_USERNAME"] = name + "_app"
                values["SPRING_DATASOURCE_URL"] = (
                    "jdbc:postgresql://db.abcdefghijklmnopqrst.supabase.co:5432/postgres?sslmode=verify-full"
                    "&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory&currentSchema="
                    + name
                )
            private_file = self.root / (name + ".private.json")
            private_file.write_text(json.dumps(values), encoding="utf-8")
            self.config["runtimeBundles"][name] = {
                "name": "adf-" + name.lower() + "-runtime-v1",
                "version": "1",
                "sourceFile": str(private_file),
            }
        base = self.root / "base"
        base.mkdir()
        runtime.prepare(self.config, self.public, base)
        name = self.config["runtimeBundles"]["bandiPortal"]["name"]
        self.service = {
            "metadata": {"name": cloudflare.SERVICE},
            "spec": {
                "template": {
                    "spec": {
                        "serviceAccountName": self.config["runtimeServiceAccounts"][
                            "bandiPortal"
                        ],
                        "containers": [
                            {
                                "env": [
                                    {"name": k, "value": v}
                                    for k, v in runtime.load(
                                        base / "bandiPortal-runtime-env.json"
                                    ).items()
                                ],
                                "volumeMounts": [
                                    {"name": "mounted", "mountPath": "/run/secrets"}
                                ],
                            }
                        ],
                        "volumes": [
                            {
                                "name": "mounted",
                                "secret": {
                                    "secretName": name,
                                    "items": [{"key": "1", "path": "gateway.env"}],
                                },
                            }
                        ],
                    }
                }
            },
            "status": {
                "latestReadyRevisionName": cloudflare.SERVICE + "-prior",
                "traffic": [
                    {"revisionName": cloudflare.SERVICE + "-prior", "percent": 100}
                ],
            },
        }
        self.image = self.public["google"]["preparedImages"]["bandiGateway"]
        self.key = {"ARCHIVE_READ_KEY": "a" * 43}

    def prepare(self, **kwargs):
        directory = self.root / ("prepared-" + uuid.uuid4().hex)
        directory.mkdir()
        defaults = dict(
            config=self.config,
            public=self.public,
            service=self.service,
            key=self.key,
            image=self.image,
            revision="cf-ci-candidate",
            next_version=2,
            directory=directory,
        )
        defaults.update(kwargs)
        with patch.object(
            runtime, "run_gcloud", side_effect=AssertionError("No cloud calls")
        ):
            return cloudflare.prepare(**defaults), directory

    def test_candidate_has_no_traffic_keeps_old_version_and_exact_payload_values(self):
        before = copy.deepcopy(self.service)
        source = Path(self.config["runtimeBundles"]["bandiPortal"]["sourceFile"])
        before_bytes = source.read_bytes()
        plan, directory = self.prepare()
        self.assertEqual(self.service, before)
        self.assertEqual(source.read_bytes(), before_bytes)
        self.assertEqual(plan["previousTraffic"], before["status"]["traffic"])
        self.assertIn("--no-traffic", plan["command"])
        self.assertIn("--tag=cf-candidate", plan["command"])
        self.assertIn("--remove-volume-mount=/run/secrets", plan["command"])
        self.assertEqual(plan["deletions"], [])
        self.assertFalse(plan["productionTrafficChanged"])
        self.assertEqual(plan["previousSecretVersion"], "1")
        self.assertEqual(plan["newSecretVersion"], "2")
        bundle = runtime.load(plan["newRuntimeBundleFile"])
        self.assertEqual(
            {k: v for k, v in bundle.items() if k != cloudflare.ARCHIVE_KEY},
            json.loads(before_bytes),
        )
        self.assertEqual(bundle[cloudflare.ARCHIVE_KEY], self.key["ARCHIVE_READ_KEY"])
        self.assertNotIn(self.key["ARCHIVE_READ_KEY"], json.dumps(plan))
        env = runtime.load(directory / "bandiPortal-runtime-env.json")
        self.assertEqual(env["COLLAB_CLOUD_CATALOG_STORE"], "cloudflare")
        self.assertNotIn(cloudflare.ARCHIVE_KEY, env)

    def test_single_bundle_can_include_distinct_discovery_credentials(self):
        plan, directory = self.prepare(
            discovery={
                "origin": "https://adf-discovery.example.workers.dev",
                "key": "b" * 43,
            }
        )
        bundle = runtime.load(plan["newRuntimeBundleFile"])
        self.assertEqual(bundle["COLLAB_DISCOVERY_READ_KEY"], "b" * 43)
        env = runtime.load(directory / "bandiPortal-runtime-env.json")
        self.assertEqual(
            env["COLLAB_DISCOVERY_ORIGIN"], "https://adf-discovery.example.workers.dev"
        )
        self.assertNotIn("b" * 43, json.dumps(plan))

    def test_rejects_changed_environment_service_account_or_mount(self):
        for change in (
            lambda s: s["spec"]["template"]["spec"]["containers"][0]["env"].append(
                {"name": "UNREVIEWED", "value": "yes"}
            ),
            lambda s: s["spec"]["template"]["spec"].update(
                serviceAccountName="unreviewed@example.org"
            ),
            lambda s: s["spec"]["template"]["spec"]["volumes"][0]["secret"]["items"][
                0
            ].update(key="latest"),
        ):
            service = copy.deepcopy(self.service)
            change(service)
            with self.assertRaises(runtime.PreparationError):
                self.prepare(service=service)

    def test_rejects_reused_malformed_keys_or_unpinned_images_and_versions(self):
        for changes in (
            {"key": {"ARCHIVE_READ_KEY": "too-short"}},
            {"next_version": 3},
            {"next_version": True},
            {"image": self.image.split("@")[0] + ":latest"},
            {"discovery": {"origin": "http://insecure.example.org", "key": "b" * 43}},
            {
                "discovery": {
                    "origin": "https://adf-discovery.example.workers.dev",
                    "key": "a" * 43,
                }
            },
        ):
            with self.subTest(changes=list(changes)):
                with self.assertRaises(runtime.PreparationError):
                    self.prepare(**changes)

    def test_gateway_context_includes_remote_reader_but_no_exporter_or_publisher(self):
        modules = build_runtime.gateway_modules(runtime.ROOT.parent.parent / "Bandi")
        self.assertIn("apps/api/cloud_catalog/remote_store.py", modules)
        self.assertEqual(
            [name for name in modules if name.startswith("apps/api/bandi_catalog/")],
            ["apps/api/bandi_catalog/" + name for name in (
                "__init__.py", "contracts.py", "router.py", "schema.json", "service.py",
            )],
        )
        self.assertFalse(
            any(any(part in name for part in ("publisher", "exporter", "local_reader", "projection", "analysis_loop")) for name in modules)
        )

    def test_archive_gate_needs_exact_byte_hash_full_verification_and_private_routing(
        self,
    ):
        manifest = {
            "snapshotId": "synthetic",
            "xzBytes": 4,
            "databaseBytes": 8,
            "xzSha256": "1" * 64,
            "databaseSha256": "2" * 64,
            "externalStorage": {
                "chunks": [
                    {"index": 0, "bytes": 4, "sha256": "4" * 64, "path": "/private"}
                ]
            },
        }
        manifest_sha = hashlib.sha256(b"exact original manifest bytes").hexdigest()
        receipt = {
            "status": "succeeded",
            "workerUrl": cloudflare.ORIGIN,
            "workerFirstVerified": True,
            "anonymousDenied": True,
            "snapshotId": "synthetic",
            "manifestSha256": manifest_sha,
            "result": {
                "packedBytes": 4,
                "rawBytes": 8,
                "packedSha256": "1" * 64,
                "rawSha256": "2" * 64,
                "chunks": [{"index": 0, "bytes": 4, "sha256": "4" * 64}],
            },
        }
        cloudflare.validate_verification(receipt, manifest, manifest_sha)
        for field, value in (
            ("status", "failed"),
            ("workerFirstVerified", False),
            ("anonymousDenied", False),
            ("manifestSha256", "3" * 64),
        ):
            broken = dict(receipt, **{field: value})
            with self.assertRaises(runtime.PreparationError):
                cloudflare.validate_verification(broken, manifest, manifest_sha)
        broken = copy.deepcopy(receipt)
        broken["result"]["chunks"] = []
        with self.assertRaises(runtime.PreparationError):
            cloudflare.validate_verification(broken, manifest, manifest_sha)


if __name__ == "__main__":
    unittest.main()
