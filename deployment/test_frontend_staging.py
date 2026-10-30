"""Offline deployment safety checks; no network or real Vercel writes."""
import contextlib
import importlib.util
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT))
import prepare_frontends as preparation
import deploy_frontend_preview as deployment


class PreviewGuards(unittest.TestCase):
    def fixture(self, name="bandi", state="awaiting-gateway", environment=None):
        # Tiny synthetic receipts are intentionally retained for review in OS temp.
        root = Path(tempfile.mkdtemp(prefix="suite-preview-guard-")).resolve()
        app = root / name
        app.mkdir()
        preparation.project_link(app, "prj_synthetic", "team_synthetic")
        (app / "app.txt").write_text("synthetic public artifact", encoding="utf-8")
        preparation.write_json(root / f"{name}-inventory.json", preparation.inventory(app))
        preparation.write_json(root / "state.json", {"status": "succeeded", "projects": {name: {"path": str(app), "projectId": "prj_synthetic", "kind": "source-nextjs", "state": state, "publicEnvironment": environment or {}}}})
        return root

    def invoke(self, root, extra):
        with patch.object(sys, "argv", ["preview", "--state", str(root / "state.json"), *extra]), patch.object(deployment.subprocess, "run", side_effect=AssertionError("No real CLI may run")), contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            deployment.main()

    def test_missing_gateway_cannot_deploy(self):
        root = self.fixture()
        with self.assertRaisesRegex(RuntimeError, "gateway URL is missing"):
            self.invoke(root, ["--app", "bandi", "--deploy-preview"])

    def test_production_target_is_not_exposed(self):
        root = self.fixture()
        with self.assertRaises(SystemExit) as result:
            self.invoke(root, ["--app", "bandi", "--target", "production"])
        self.assertEqual(result.exception.code, 2)

    def test_modified_snapshot_is_rejected(self):
        root = self.fixture(state="prepared")
        (root / "bandi/app.txt").write_text("changed", encoding="utf-8")
        with self.assertRaisesRegex(RuntimeError, "Snapshot changed"):
            self.invoke(root, ["--app", "bandi", "--deploy-preview"])

    def test_additional_unreviewed_file_is_rejected(self):
        root = self.fixture(state="prepared")
        (root / "bandi/extra.txt").write_text("unreviewed", encoding="utf-8")
        with self.assertRaisesRegex(RuntimeError, "file set changed"):
            self.invoke(root, ["--app", "bandi", "--deploy-preview"])

    def test_private_configuration_is_never_passed_to_cli(self):
        root = self.fixture(state="prepared", environment={"CLERK_SECRET_KEY": "synthetic-value"})
        with self.assertRaisesRegex(RuntimeError, "Secret configuration"):
            self.invoke(root, ["--app", "bandi", "--deploy-preview"])


if __name__ == "__main__":
    unittest.main()
