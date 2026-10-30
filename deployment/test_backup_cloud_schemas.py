"""Safety tests without remote databases or real dumps."""

from __future__ import annotations

import io
import json
from datetime import UTC, datetime, timedelta
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import backup_cloud_schemas as backup


class BackupTests(unittest.TestCase):
    def test_catchup_reuses_only_verified_successful_archive_from_today(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path = root / ("backup-20261030T120000Z-" + "a" * 32)
            path.mkdir()
            (path / "archive.dump").write_bytes(b"synthetic verified archive")
            (path / "archive.list").write_bytes(b"synthetic verified index")
            (path / "state.json").write_text(json.dumps({"status": "succeeded"}))
            manifest = {
                "status": "succeeded",
                "created_at": datetime.now(UTC).isoformat(),
                "schemas": list(backup.SCHEMAS),
                "archive": "archive.dump",
                "sha256": backup.sha256(path / "archive.dump"),
                "list_sha256": backup.sha256(path / "archive.list"),
            }
            (path / "manifest.json").write_text(json.dumps(manifest))
            self.assertEqual(backup.verified_backup_today(root), path)
            (path / "archive.dump").write_bytes(b"corrupt archive")
            self.assertIsNone(backup.verified_backup_today(root))

    def test_catchup_does_not_skip_yesterday_or_failed_runs(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            path = root / ("backup-20261030T120000Z-" + "b" * 32)
            path.mkdir()
            (path / "archive.dump").write_bytes(b"archive")
            (path / "archive.list").write_bytes(b"index")
            (path / "state.json").write_text(json.dumps({"status": "succeeded"}))
            manifest = {
                "status": "succeeded",
                "created_at": (datetime.now(UTC) - timedelta(days=2)).isoformat(),
                "schemas": list(backup.SCHEMAS),
                "archive": "archive.dump",
                "sha256": backup.sha256(path / "archive.dump"),
                "list_sha256": backup.sha256(path / "archive.list"),
            }
            (path / "manifest.json").write_text(json.dumps(manifest))
            self.assertIsNone(backup.verified_backup_today(root))
            manifest["created_at"] = datetime.now(UTC).isoformat()
            (path / "manifest.json").write_text(json.dumps(manifest))
            (path / "state.json").write_text(json.dumps({"status": "failed"}))
            self.assertIsNone(backup.verified_backup_today(root))

    def source(self):
        return backup.Connection(
            "127.0.0.1",
            5432,
            "synthetic",
            "backup_owner",
            r"secret:with\escapes",
            "disable",
        )

    def test_remote_unverified_tls_runtime_role_and_connection_override_fail_closed(
        self,
    ):
        for source in (
            backup.Connection(
                "remote.example.org",
                5432,
                "postgres",
                "backup_owner",
                "secret",
                "disable",
            ),
            backup.Connection(
                "127.0.0.1",
                5432,
                "dbname=other host=remote",
                "backup_owner",
                "secret",
                "disable",
            ),
            backup.Connection(
                "127.0.0.1", 5432, "synthetic", "bandi_portal_app", "secret", "disable"
            ),
        ):
            with self.assertRaises(backup.BackupError):
                source.validated()

    def test_private_pgpass_escaping_and_no_password_docker_environment(self):
        value = self.source()
        self.assertNotIn(value.password, repr(value))
        with (
            patch.object(backup, "protect_directory"),
            backup.credentials(value) as root,
        ):
            self.assertIn(r"secret\:with\\escapes", (root / "pgpass").read_text())
            self.assertNotIn("PGPASSWORD", (root / "connection.env").read_text())
            self.assertNotIn(value.password, (root / "connection.env").read_text())

    def test_dump_cannot_be_written_below_a_repository(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / ".git").mkdir()
            with self.assertRaises(backup.BackupError):
                backup.outside_repository(root / "backups")

    def test_success_has_digest_and_failed_listing_keeps_archive_without_success_manifest(
        self,
    ):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            failed = [False]

            def fake_command(output, args, state, **kwargs):
                if args[0] == "pg_dump":
                    (output / "archive.dump").write_bytes(b"PGDMP-synthetic-fixture")
                    return output / "command.stdout"
                if failed[0]:
                    raise backup.BackupError("SYNTHETIC_LIST_FAILURE")
                path = output / "archive.list"
                path.write_text("synthetic TOC")
                return path

            with (
                patch.object(backup, "protect_directory"),
                patch.object(backup, "docker_command", side_effect=fake_command),
            ):
                first = backup.run_backup(
                    self.source(), root, schemas=("bandi_portal",)
                )
                manifest = json.loads((first / "manifest.json").read_text())
                self.assertEqual(
                    manifest["sha256"], backup.sha256(first / "archive.dump")
                )
                failed[0] = True
                with self.assertRaises(backup.BackupError):
                    backup.run_backup(self.source(), root, schemas=("bandi_portal",))
            second = next(path for path in root.iterdir() if path != first)
            self.assertTrue((second / "archive.dump").exists())
            self.assertFalse((second / "manifest.json").exists())
            self.assertEqual(
                json.loads((second / "state.json").read_text())["status"], "failed"
            )
            self.assertTrue((first / "manifest.json").exists())

    def test_docker_arguments_never_include_password(self):
        seen = []

        class Child:
            pid, returncode = 42, 0
            stderr = io.BytesIO(b"")

            def __init__(self, command, **kwargs):
                seen.extend(command)

            def poll(self):
                return 0

        with (
            tempfile.TemporaryDirectory() as directory,
            patch.object(backup, "protect_directory"),
            patch.object(backup.subprocess, "Popen", Child),
        ):
            backup.docker_command(
                Path(directory),
                ["pg_dump", "--schema=bandi_portal"],
                {},
                seconds=1,
                source=self.source(),
            )
        self.assertNotIn(self.source().password, " ".join(seen))
        self.assertNotIn("PGPASSWORD", " ".join(seen))


if __name__ == "__main__":
    unittest.main()
