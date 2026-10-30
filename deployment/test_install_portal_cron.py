"""Offline tests: no cron-job.org account or real gateway is contacted."""

from __future__ import annotations

import copy
from contextlib import redirect_stderr, redirect_stdout
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
import warnings
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location(
    "portal_cron", Path(__file__).with_name("install_portal_cron.py")
)
cron = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(cron)
KEY = "synthetic-cron-key-at-least-thirty-two-characters"


class Provider:
    def __init__(self):
        self.jobs = {}
        self.writes = []
        self.incomplete = False

    def request(self, method, path, payload=None):
        if method == "GET" and path == "/jobs":
            return {
                "jobs": copy.deepcopy(list(self.jobs.values())),
                "someFailed": self.incomplete,
            }
        if method == "PUT":
            identity = max(self.jobs, default=0) + 1
            self.jobs[identity] = {"jobId": identity, **copy.deepcopy(payload["job"])}
            self.writes.append((method, identity))
            return {"jobId": identity}
        identity = int(path.split("/")[-1])
        if method == "PATCH":
            self.jobs[identity].update(copy.deepcopy(payload["job"]))
            self.writes.append((method, identity))
            return {}
        return {"jobDetails": copy.deepcopy(self.jobs[identity])}


class CronTests(unittest.TestCase):
    def setUp(self):
        self.spec = cron.specification("https://portal.example.org", KEY)

    def test_create_then_reapply_is_idempotent_and_rotation_updates_only_owned_job(
        self,
    ):
        provider = Provider()
        provider.jobs[10] = {
            "jobId": 10,
            "title": "Unrelated",
            "url": "https://other.example.org/",
        }
        unrelated = copy.deepcopy(provider.jobs[10])
        self.assertEqual(
            cron.apply(provider, self.spec), {"action": "created", "jobId": 11}
        )
        self.assertEqual(
            cron.apply(provider, self.spec), {"action": "unchanged", "jobId": 11}
        )
        rotated = cron.specification(
            "https://portal.example.org",
            "rotated-synthetic-cron-key-at-least-thirty-two",
        )
        self.assertEqual(
            cron.apply(provider, rotated), {"action": "updated", "jobId": 11}
        )
        self.assertEqual(provider.writes, [("PUT", 11), ("PATCH", 11)])
        self.assertEqual(provider.jobs[10], unrelated)
        self.assertFalse(provider.jobs[11]["saveResponses"])
        self.assertEqual(provider.jobs[11]["schedule"]["minutes"], [0, 30])
        self.assertEqual(provider.jobs[11]["requestMethod"], 0)

    def test_incomplete_or_missing_list_marker_fails_without_mutation(self):
        provider = Provider()
        provider.incomplete = True
        with self.assertRaises(cron.CronError):
            cron.apply(provider, self.spec)
        self.assertEqual(provider.writes, [])
        for value in (
            {"jobs": []},
            {"jobs": [], "someFailed": False, "hasMore": True},
            {"jobs": [], "someFailed": False, "totalCount": 1},
        ):
            with self.subTest(value=value), self.assertRaises(cron.CronError):
                cron.complete_listing(value)

    def test_duplicate_or_partial_identity_matches_are_never_modified(self):
        for jobs in (
            [{"jobId": 1, **self.spec["job"]}, {"jobId": 2, **self.spec["job"]}],
            [{"jobId": 1, **self.spec["job"], "url": "https://different.example.org/"}],
            [{"jobId": 1, **self.spec["job"], "title": "Unrelated"}],
        ):
            with self.subTest(jobs=len(jobs)):
                provider = Provider()
                provider.jobs = {item["jobId"]: item for item in jobs}
                with self.assertRaises(cron.CronError):
                    cron.apply(provider, self.spec)
                self.assertEqual(provider.writes, [])

    def test_public_https_origin_and_exact_maintenance_route_only(self):
        for url in (
            "http://portal.example.org",
            "https://u:p@portal.example.org",
            "https://portal.example.org/?key=x",
            "https://portal.example.org/#x",
            "https://portal.example.org/other",
            "https://localhost",
            "https://localhost.",
            "https://gateway.local",
            "https://127.0.0.1",
            "https://[::1]",
            "https://portal.example.org:99999",
        ):
            with self.subTest(url=url), self.assertRaises(cron.CronError):
                cron.specification(url, KEY)
        self.assertEqual(
            self.spec["job"]["url"], "https://portal.example.org" + cron.MAINTENANCE
        )

    def test_header_secrets_are_printable_ascii_only(self):
        for value in ("a" * 31, "a" * 32 + "\n", "a" * 32 + "é", "a" * 32 + "\x7f"):
            with self.subTest(size=len(value)), self.assertRaises(cron.CronError):
                cron.specification("https://portal.example.org", value)

    def test_export_default_has_no_network_and_never_prints_either_secret(self):
        fake_api = "synthetic-api-key-never-export-or-print"
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "private.json"
            stdout, stderr = io.StringIO(), io.StringIO()
            with (
                patch.dict(
                    "os.environ", {"COLLAB_CRON_KEY": KEY, "CRONJOB_API_KEY": fake_api}
                ),
                patch.object(
                    cron,
                    "Client",
                    side_effect=AssertionError("Network forbidden in export mode"),
                ),
                redirect_stdout(stdout),
                redirect_stderr(stderr),
            ):
                code = cron.main(
                    [
                        "--gateway-url",
                        "https://portal.example.org",
                        "--export",
                        str(output),
                    ]
                )
            self.assertEqual(code, 0)
            self.assertNotIn(KEY, stdout.getvalue() + stderr.getvalue())
            self.assertNotIn(
                fake_api, stdout.getvalue() + stderr.getvalue() + output.read_text()
            )
            self.assertEqual(json.loads(output.read_text()), self.spec)

    def test_transport_disallows_delete_and_never_follows_redirects(self):
        from urllib.error import HTTPError

        client = cron.Client("synthetic-api-key-at-least-sixteen")
        requests = []

        class RedirectingOpener:
            def open(self, request, **kwargs):
                requests.append(request)
                raise HTTPError(
                    request.full_url, 302, "secret diagnostic must not escape", {}, None
                )

        client.opener = RedirectingOpener()
        with self.assertRaises(cron.CronError):
            client.request("GET", "/jobs")
        with self.assertRaises(cron.CronError):
            client.request("DELETE", "/jobs/1")
        self.assertEqual(len(requests), 1)
        self.assertEqual(requests[0].full_url, "https://api.cron-job.org/jobs")
        self.assertIsNone(
            cron.NoRedirect().redirect_request(
                None, None, 302, "", {}, "https://other.invalid"
            )
        )

    def test_local_prompt_overrides_environment_and_receipt_has_no_secrets(self):
        provider = Provider()
        prompted_api = "synthetic-prompted-api-key-private"
        stale_api = "synthetic-stale-api-key-must-not-be-used"
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            key_file, exported, receipt = (root / name for name in ("cron-key.local", "export.json", "receipt.json"))
            key_file.write_text(KEY)
            arguments = [
                "--gateway-url", "https://portal.example.org",
                "--cron-key-file", str(key_file),
                "--export", str(exported), "--receipt", str(receipt),
                "--apply", "--prompt-api-key",
            ]
            stdout, stderr = io.StringIO(), io.StringIO()
            with (
                patch.dict("os.environ", {"CRONJOB_API_KEY": stale_api}),
                patch.object(cron, "hidden_secret", return_value=prompted_api) as prompt,
                patch.object(cron, "Client", return_value=provider) as client,
                redirect_stdout(stdout), redirect_stderr(stderr),
            ):
                self.assertEqual(cron.main(arguments), 0)
                self.assertEqual(cron.main(arguments), 0)
            self.assertEqual(prompt.call_count, 2)
            self.assertEqual(client.call_count, 2)
            client.assert_called_with(prompted_api)
            self.assertEqual(provider.writes, [("PUT", 1)])
            result = json.loads(receipt.read_text())
            self.assertTrue(result["configurationVerified"])
            self.assertFalse(result["executionVerified"])
            self.assertEqual(result["action"], "unchanged")
            self.assertEqual(result["jobId"], 1)
            self.assertEqual(set(result), {"schema", "provider", "checkedAt", "configurationVerified", "executionVerified", "action", "jobId"})
            for value in (KEY, prompted_api, stale_api):
                self.assertNotIn(value, stdout.getvalue() + stderr.getvalue() + receipt.read_text())
            self.assertNotIn(prompted_api, exported.read_text())

    def test_echoed_getpass_fallback_stops_before_reading_a_secret(self):
        def unavailable_prompt(_prompt):
            warnings.warn("Hidden input unavailable", cron.getpass.GetPassWarning)
            raise AssertionError("Echoed input must never be reached")

        with patch.object(cron.getpass, "getpass", side_effect=unavailable_prompt):
            with self.assertRaisesRegex(cron.CronError, "console locale interattiva"):
                cron.hidden_secret("API key: ")

    def test_failure_replaces_an_old_success_receipt_without_raw_errors(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            receipt = root / "receipt.json"
            receipt.write_text('{"configurationVerified": true}')
            stdout, stderr = io.StringIO(), io.StringIO()
            with (
                patch.dict("os.environ", {"COLLAB_CRON_KEY": KEY}),
                patch.object(cron, "hidden_secret", return_value="synthetic-prompted-api-key-private"),
                patch.object(cron, "Client", side_effect=RuntimeError("raw HTTP diagnostic with synthetic-secret-value")),
                redirect_stdout(stdout), redirect_stderr(stderr),
            ):
                code = cron.main([
                    "--gateway-url", "https://portal.example.org",
                    "--export", str(root / "export.json"),
                    "--receipt", str(receipt), "--apply", "--prompt-api-key",
                ])
            self.assertEqual(code, 1)
            result = json.loads(receipt.read_text())
            self.assertFalse(result["configurationVerified"])
            self.assertEqual(result["action"], "interrupted")
            self.assertIsNone(result["jobId"])
            self.assertNotIn("synthetic-secret-value", stdout.getvalue() + stderr.getvalue() + receipt.read_text())


if __name__ == "__main__":
    unittest.main()
