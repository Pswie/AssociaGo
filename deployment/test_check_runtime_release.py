"""Protect release evidence from secret disclosure and unsafe cleanup decisions."""

import copy
import json
import unittest
from unittest.mock import patch

import check_runtime_release as audit


class ReleaseAuditTests(unittest.TestCase):
    def test_revision_summary_never_serializes_old_plaintext_or_arguments(self):
        secret = "SENSITIVE_VALUE_MUST_NOT_REACH_RECEIPT"
        document = {
            "metadata": {"name": "old-revision", "annotations": {"arbitrary": secret}},
            "spec": {
                "containers": [
                    {
                        "image": "image@sha256:1234",
                        "args": [secret],
                        "env": [
                            {"name": "DB_PASSWORD", "value": secret},
                            {
                                "name": "SPRING_APPLICATION_JSON",
                                "valueFrom": {
                                    "secretKeyRef": {"name": "runtime", "key": "1"}
                                },
                            },
                        ],
                    }
                ]
            },
        }
        result = audit.revision_summary(document)
        self.assertNotIn(secret, json.dumps(result))
        self.assertEqual(
            result["environmentNames"], ["DB_PASSWORD", "SPRING_APPLICATION_JSON"]
        )
        self.assertEqual(result["secretReferences"][0]["version"], "1")

    def test_metadata_runner_rejects_mutating_commands_before_starting_process(self):
        with patch.object(
            audit.subprocess, "run", side_effect=AssertionError("No process")
        ):
            for args in (
                ["run", "services", "delete"],
                ["run", "deploy", "app"],
                ["secrets", "versions", "access"],
            ):
                with self.subTest(args=args), self.assertRaises(ValueError):
                    audit.cloud_read(args)

    def test_tagged_zero_traffic_revision_is_retained_and_empty_env_is_normalized(self):
        current = {
            "metadata": {
                "name": "ready",
                "annotations": {"autoscaling.knative.dev/maxScale": "1"},
            },
            "spec": {
                "containers": [
                    {
                        "image": "image",
                        "env": [
                            {"name": "EMPTY"},
                            {
                                "name": "SPRING_APPLICATION_JSON",
                                "valueFrom": {
                                    "secretKeyRef": {"name": "runtime", "key": "1"}
                                },
                            },
                        ],
                    }
                ],
                "serviceAccountName": "account",
            },
        }
        old = copy.deepcopy(current)
        old["metadata"]["name"] = "tagged-old"
        unused = copy.deepcopy(old)
        unused["metadata"]["name"] = "unused"
        service = {
            "metadata": {"annotations": {"run.googleapis.com/maxScale": "1"}},
            "spec": {"template": current},
            "status": {
                "latestReadyRevisionName": "ready",
                "url": "https://service.example",
                "traffic": [
                    {"revisionName": "ready", "percent": 100},
                    {"revisionName": "tagged-old", "tag": "rollback", "percent": 0},
                ],
            },
        }
        result = audit.audit_service(
            service,
            [current, old, unused],
            "associago",
            {"image": "image", "account": "account", "service": "service"},
            {"apiOrigins": {"associago": "https://service.example"}},
            {"runtimeBundles": {"associago": {"name": "runtime", "version": 1}}},
            {"EMPTY": ""},
        )
        self.assertTrue(all(result["checks"].values()))
        self.assertEqual(
            [row["cleanupDisposition"] for row in result["revisionInventory"]],
            ["retain", "retain", "review-after-frontend-release"],
        )

    def test_http_origin_guard_runs_before_reading_any_bundle(self):
        with patch.object(
            audit.Path, "read_text", side_effect=AssertionError("No secrets read")
        ):
            for url in (
                "http://service.a.run.app",
                "https://evil.example",
                "https://service.a.run.app@evil.example",
            ):
                with self.subTest(url=url), self.assertRaises(ValueError):
                    audit.http_checks("associago", url, {})


if __name__ == "__main__":
    unittest.main()
