"""Retained synthetic fixtures for one additive runtime version, no cloud calls."""
import copy
import hashlib
import json
import os
from pathlib import Path
import unittest
import uuid

import deploy_runtime_candidates as runtime
import prepare_bandi_gateway as bandi


class BandiGatewayPreparationTests(unittest.TestCase):
    def setUp(self):
        suffix = uuid.uuid4().hex
        self.public = runtime.ROOT / ".artifacts" / ("test-bandi-prep-" + suffix)
        self.public.mkdir()
        self.private = Path(os.environ["LOCALAPPDATA"]) / "BandiSuite/private" / ("synthetic-test-runtime3-" + suffix)
        self.previous = {"COLLAB_NODE_KEY": "synthetic-old-node-key", "COLLAB_DATABASE_URL": "synthetic-old-app-role"}
        self.payload = runtime.bundle_payload("bandiPortal", self.previous)
        self.key = {"BANDI_READ_KEY": "a" * 43}
        self.service = {"metadata": {"name": bandi.SERVICE}, "spec": {"template": {"spec": {"containers": [{"image": "synthetic-image-digest", "env": [{"name": "BANDI_MODE", "value": "gateway"}], "volumeMounts": [{"name": "runtime", "mountPath": "/run/secrets"}]}], "volumes": [{"name": "runtime", "secret": {"secretName": "synthetic-runtime", "items": [{"key": "2", "path": "gateway.env"}]}}]}}}, "status": {"latestReadyRevisionName": "synthetic-previous", "traffic": [{"revisionName": "synthetic-previous", "percent": 100}]}}

    def prepare(self, **changes):
        args = dict(service=self.service, previous=self.previous, previous_payload=self.payload, key_document=self.key, private_directory=self.private, public_directory=self.public, validate_settings=lambda values: None)
        args.update(changes)
        return bandi.prepare(**args)

    def test_preserves_old_values_and_adds_only_read_origin_ttl_without_cloud_writes(self):
        original = copy.deepcopy(self.previous)
        result = self.prepare()
        values = json.loads(Path(result["privateJsonFile"]).read_text())
        self.assertEqual(self.previous, original)
        self.assertEqual({k: values[k] for k in original}, original)
        self.assertEqual(set(values) - set(original), {"COLLAB_BANDI_CATALOG_ORIGIN", bandi.READ_KEY, "COLLAB_NODE_ONLINE_SECONDS"})
        self.assertEqual(values["COLLAB_BANDI_CATALOG_ORIGIN"], bandi.ORIGIN)
        self.assertEqual(values["COLLAB_NODE_ONLINE_SECONDS"], "180")
        self.assertEqual(hashlib.sha256(Path(result["payloadFile"]).read_bytes()).hexdigest(), result["payloadSha256"])
        self.assertNotIn("a" * 43, json.dumps(result))
        self.assertEqual(result["secretWrites"], 0)

    def test_rejects_wrong_runtime_version_before_writing(self):
        bad = copy.deepcopy(self.service)
        bad["spec"]["template"]["spec"]["volumes"][0]["secret"]["items"][0]["key"] = "1"
        with self.assertRaises(runtime.PreparationError): self.prepare(service=bad)
        self.assertFalse(self.private.exists())

    def test_rejects_publish_keys_and_reused_keys(self):
        for key in [{**self.key, "BANDI_PUBLISH_KEY": "b" * 43}, {"BANDI_READ_KEY": "synthetic-old-node-key"}]:
            with self.subTest(keys=list(key)):
                with self.assertRaises(runtime.PreparationError): self.prepare(key_document=key)
        self.assertFalse(self.private.exists())

    def test_rejects_newline_alteration_and_does_not_print_settings_values(self):
        with self.assertRaises(runtime.PreparationError): self.prepare(previous_payload=self.payload.replace(b"\n", b"\r\n"))
        def reject(values): raise ValueError(values[bandi.READ_KEY])
        with self.assertRaisesRegex(runtime.PreparationError, "^New runtime settings rejected$"): self.prepare(validate_settings=reject)
        self.assertFalse(self.private.exists())

    def test_rejects_repository_private_destination_and_existing_plan(self):
        with self.assertRaises(runtime.PreparationError): self.prepare(private_directory=self.public / "private")
        (self.public / "runtime3-plan.json").write_text("retained-original")
        with self.assertRaises(runtime.PreparationError): self.prepare()
        self.assertEqual((self.public / "runtime3-plan.json").read_text(), "retained-original")
        self.assertFalse(self.private.exists())


if __name__ == "__main__": unittest.main()
