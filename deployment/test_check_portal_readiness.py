"""Offline readiness tests; never inspect credentials outside explicitly declared files."""

import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import check_portal_readiness as readiness


class ReadinessTests(unittest.TestCase):
    def test_only_declared_cron_names_are_reported(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "private.env"
            path.write_text(
                "COLLAB_CRON_KEY=secret-cron-value\nCRONJOB_API_KEY=secret-api-value\nUNRELATED_TOKEN=private\n"
            )
            result = readiness.declared_names(path)
            self.assertEqual(result, ["COLLAB_CRON_KEY", "CRONJOB_API_KEY"])
            self.assertNotIn("secret", json.dumps(result))

    def test_missing_config_never_starts_any_worker_or_remote_request(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            with (
                patch.object(readiness, "docker_status", return_value={}),
                patch.object(readiness, "task_status", return_value={}),
                patch.object(
                    readiness,
                    "run",
                    side_effect=AssertionError("No native command expected"),
                ),
            ):
                result = readiness.collect(
                    repo, repo / "missing.env", repo / "missing.json", None, None
                )
            self.assertFalse(result["catalog_node_ready"])
            self.assertFalse(result["activation_verified"])
            self.assertFalse(result["remote_requests"])
            self.assertFalse(result["daemon_started"])

    def test_failed_native_config_output_is_never_published(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            (repo / ".venv" / "Scripts").mkdir(parents=True)
            (repo / ".venv" / "Scripts" / "python.exe").touch()
            (repo / ".env").touch()
            with (
                patch.object(readiness, "docker_status", return_value={}),
                patch.object(readiness, "task_status", return_value={}),
                patch.object(
                    readiness, "run", return_value=(1, "password=synthetic-never-print")
                ),
            ):
                result = readiness.collect(
                    repo, repo / "missing.env", repo / "missing.json", None, None
                )
            self.assertNotIn("synthetic", json.dumps(result))
            self.assertEqual(result["archive"]["reason"], "NATIVE_CONFIG_CHECK_FAILED")

    def test_cron_environment_reports_names_without_values(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            with (
                patch.dict(
                    "os.environ",
                    {
                        "COLLAB_CRON_KEY": "private-cron",
                        "CRONJOB_API_KEY": "private-api",
                    },
                ),
                patch.object(readiness, "docker_status", return_value={}),
                patch.object(readiness, "task_status", return_value={}),
            ):
                result = readiness.collect(
                    repo, repo / "missing.env", repo / "missing.json", None, None
                )
            self.assertEqual(
                result["cron"]["environment_names_present"], list(readiness.CRON_NAMES)
            )
            self.assertNotIn("private-cron", json.dumps(result))
            self.assertNotIn("private-api", json.dumps(result))


if __name__ == "__main__":
    unittest.main()
