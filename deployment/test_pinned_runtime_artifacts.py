"""Offline artifact guards: no Docker, provider calls or application data."""
from __future__ import annotations

import hashlib
from pathlib import Path
from types import SimpleNamespace
import tempfile
import unittest
from unittest.mock import patch

import build_runtime as runtime
import prepare_frontends as frontends


class JarPinningTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix="suite-jar-guard-")
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.jar = self.root / "tested.jar"
        self.jar.write_bytes(b"synthetic reviewed artifact")
        self.digest = hashlib.sha256(self.jar.read_bytes()).hexdigest()

    def test_explicit_jar_uses_hash_and_ignores_stale_default_output(self):
        default = self.root / "build/libs"
        default.mkdir(parents=True)
        (default / "stale.jar").write_bytes(b"stale")
        self.assertEqual(runtime.select_jar(self.root, self.jar, self.digest.upper()), (self.jar.resolve(), self.digest))

    def test_both_explicit_options_are_required(self):
        for jar, digest in ((self.jar, None), (None, self.digest)):
            with self.subTest(jar=jar, digest=digest), self.assertRaises(ValueError):
                runtime.select_jar(self.root, jar, digest)

    def test_mismatch_and_invalid_digests_reject_before_any_copy(self):
        for digest in ("0" * 64, "", "not-a-digest", self.digest + " "):
            with self.subTest(digest=digest), self.assertRaises(ValueError):
                runtime.select_jar(self.root, self.jar, digest)

    def test_plain_missing_and_nonjar_inputs_are_rejected(self):
        for name in ("tested-plain.jar", "tested.zip"):
            file = self.root / name
            file.write_bytes(self.jar.read_bytes())
            with self.subTest(name=name), self.assertRaises(ValueError):
                runtime.select_jar(self.root, file, self.digest)
        with self.assertRaises(FileNotFoundError):
            runtime.select_jar(self.root, self.root / "missing.jar", self.digest)

    def test_legacy_selection_still_requires_exactly_one_bootjar(self):
        default = self.root / "build/libs"
        default.mkdir(parents=True)
        with self.assertRaisesRegex(RuntimeError, "one current bootJar"):
            runtime.select_jar(self.root)
        (default / "application.jar").write_bytes(self.jar.read_bytes())
        (default / "application-plain.jar").write_bytes(b"ignored")
        self.assertEqual(runtime.select_jar(self.root), ((default / "application.jar").resolve(), self.digest))
        (default / "another.jar").write_bytes(b"ambiguous")
        with self.assertRaisesRegex(RuntimeError, "one current bootJar"):
            runtime.select_jar(self.root)

    def test_changed_bytes_cannot_enter_docker_context(self):
        source, digest = runtime.select_jar(self.root, self.jar, self.digest)
        self.jar.write_bytes(b"changed after selection")
        with self.assertRaisesRegex(RuntimeError, "changed while preparing"):
            runtime.copy_jar(source, self.root / "context.jar", digest)

    def test_copy_matches_the_exact_reviewed_digest(self):
        destination = self.root / "context.jar"
        runtime.copy_jar(self.jar, destination, self.digest)
        self.assertEqual(hashlib.sha256(destination.read_bytes()).hexdigest(), self.digest)

    def test_gateway_includes_access_code_and_runtime_catalog(self):
        repository = Path(__file__).resolve().parents[2] / "Bandi"
        files = runtime.gateway_modules(repository)
        for relative in ("apps/api/collaboration/access.py", "apps/api/collaboration/access_router.py", "apps/api/collaboration/access_catalog.json"):
            self.assertIn(relative, files)
            self.assertTrue((repository / relative).is_file())
        self.assertFalse(any(name.startswith("data/") or ".env" in name for name in files))


class SpaIsolationTests(unittest.TestCase):
    def settings(self):
        return {
            "clerk": {"publishableKey": "pk_test_synthetic"},
            "apiOrigins": {"associago": "https://authority.example"},
            "origins": {"primary": "https://primary.example"},
            "vercel": {"teamId": "team_fixture"},
        }

    def environment(self):
        return frontends.spa_environment("https://projects.example", self.settings())

    def entry(self, extra="", connect=None):
        connect = connect or "'self' https://projects.example https://primary.example https://authority.example"
        return f'<meta http-equiv="Content-Security-Policy" content="default-src \'self\'; connect-src {connect};">{extra}'

    def test_environment_pins_access_authority_separately_from_application(self):
        value = self.environment()
        self.assertEqual(value["VITE_SUITE_ACCESS_API_URL"], "https://authority.example")
        self.assertEqual(value["VITE_API_BASE_URL"], "https://projects.example/api")
        settings = self.settings()
        settings["apiOrigins"]["associago"] = "http://authority.example"
        with self.assertRaises(ValueError):
            frontends.spa_environment("https://projects.example", settings)

    def test_csp_checks_actual_directive_not_origin_strings_elsewhere(self):
        frontends.verify_spa_csp(self.entry(), self.environment())
        with self.assertRaises(RuntimeError):
            frontends.verify_spa_csp(self.entry('https://authority.example', "'self' https://projects.example https://primary.example"), self.environment())

    def test_ambiguous_unresolved_and_overbroad_policies_are_rejected(self):
        for html in ("https://projects.example https://primary.example https://authority.example", self.entry() * 2, self.entry('%ACCESS_ORIGIN%'), self.entry(connect="* https://projects.example https://primary.example https://authority.example"), self.entry().replace("connect-src", "connect-src 'self'; connect-src")):
            with self.subTest(html=html), self.assertRaises(RuntimeError):
                frontends.verify_spa_csp(html, self.environment())

    def test_new_output_is_isolated_and_legacy_dist_is_untouched(self):
        with tempfile.TemporaryDirectory(prefix="suite-spa-isolation-") as temporary:
            root = Path(temporary)
            desktop = root / "LibrePM/desktop"
            legacy = desktop / "dist-web"
            legacy.mkdir(parents=True)
            marker = legacy / "original.txt"
            marker.write_bytes(b"preserve old artifact")
            output = root / "reviewed-run"
            output.mkdir()
            commands = []

            def build(command, **kwargs):
                commands.append(command)
                built = Path(command[command.index("--outDir") + 1])
                self.assertTrue(built.is_dir())
                self.assertTrue(built.is_relative_to(output))
                self.assertNotEqual(built, legacy)
                (built / "index.web.html").write_text(self.entry(), encoding="utf-8")
                return SimpleNamespace(returncode=0)

            with patch.object(frontends, "GITHUB", root), patch.object(frontends.subprocess, "run", side_effect=build):
                result = frontends.build_spa("librepm", "LibrePM", "prj_fixture", "https://projects.example", self.settings(), output)
            self.assertEqual(marker.read_bytes(), b"preserve old artifact")
            self.assertEqual(result["state"], "prepared")
            self.assertEqual(len(commands), 1)
            emitted = output / "librepm/.vercel/output/static/index.web.html"
            self.assertTrue(emitted.is_file())
            frontends.verify_spa_csp(emitted.read_text(), self.environment())


if __name__ == "__main__":
    unittest.main()
