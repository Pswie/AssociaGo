"""A partial release must never migrate the other applications."""

from contextlib import ExitStack, redirect_stderr, redirect_stdout
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import migrate_owner_once as migrate


class OwnerMigrationSelectionTests(unittest.TestCase):
    def test_application_selection_controls_actual_migration_calls(self):
        for selected in (["associago"], ["bandi_portal"], ["librepm", "associago"], []):
            with self.subTest(
                selected=selected
            ), tempfile.TemporaryDirectory() as folder:
                root = Path(folder)
                config = root / "owners.json"
                public = root / "public.json"
                receipt = root / "receipt.json"
                config.write_text(
                    json.dumps(
                        {key: {} for key in ("associago", "librepm", "bandi_portal")}
                    )
                )
                public.write_text(
                    json.dumps(
                        {
                            "google": {
                                "preparedImages": {
                                    key: "synthetic-digest"
                                    for key in ("associago", "librepm", "bandiGateway")
                                }
                            }
                        }
                    )
                )
                argv = [
                    "migrate",
                    "--config",
                    str(config),
                    "--public-settings",
                    str(public),
                    "--receipt",
                    str(receipt),
                    "--apply",
                    "--ca-file",
                    "synthetic-ca",
                    "--ca-sha256",
                    "synthetic-pin",
                ]
                for schema in selected:
                    argv.extend(["--schema", schema])
                with ExitStack() as stack:
                    stack.enter_context(patch("sys.argv", argv))
                    stack.enter_context(patch.object(migrate, "validate"))
                    stack.enter_context(
                        patch.object(
                            migrate, "validate_ca", return_value=root / "ca.crt"
                        )
                    )
                    stack.enter_context(patch.object(migrate, "protect_directory"))
                    stack.enter_context(
                        patch.object(
                            migrate,
                            "create_trust_store",
                            return_value=root / "trust.jks",
                        )
                    )
                    stack.enter_context(
                        patch.object(
                            migrate.subprocess,
                            "run",
                            side_effect=AssertionError(
                                "No real process in selection tests"
                            ),
                        )
                    )
                    java = stack.enter_context(
                        patch.object(
                            migrate,
                            "java_migration",
                            return_value={"verified": "synthetic-java"},
                        )
                    )
                    gateway = stack.enter_context(
                        patch.object(
                            migrate,
                            "gateway_migration",
                            return_value={"verified": "synthetic-gateway"},
                        )
                    )
                    stack.enter_context(redirect_stdout(io.StringIO()))
                    migrate.main()
                expected = selected or ["associago", "librepm", "bandi_portal"]
                self.assertEqual(
                    [call.args[0] for call in java.call_args_list],
                    [
                        schema
                        for schema in ("associago", "librepm")
                        if schema in expected
                    ],
                )
                self.assertEqual(gateway.call_count, int("bandi_portal" in expected))
                state = json.loads(receipt.read_text())
                self.assertEqual(state["selectedSchemas"], expected)
                self.assertEqual(state["status"], "succeeded")
                self.assertEqual(len(state["schemas"]), len(expected))

    def test_duplicate_selection_stops_before_any_remote_work(self):
        argv = [
            "migrate",
            "--config",
            "unused",
            "--receipt",
            "unused",
            "--schema",
            "associago",
            "--schema",
            "associago",
        ]
        with patch("sys.argv", argv), patch.object(
            migrate, "validate"
        ) as validate, redirect_stderr(io.StringIO()):
            with self.assertRaises(SystemExit) as stopped:
                migrate.main()
        self.assertEqual(stopped.exception.code, 2)
        validate.assert_not_called()


if __name__ == "__main__":
    unittest.main()
