"""Exercise the real Java SPA clients with the generated deployment settings."""

import json
from pathlib import Path
import shutil
import subprocess
import unittest

import prepare_frontends as preparation


class JavaSpaApiContract(unittest.TestCase):
    def test_deployed_bases_reach_the_backend_profile_routes(self):
        settings = {
            "clerk": {"publishableKey": "synthetic-public-key"},
            "origins": {"primary": "https://primary.example"},
        }
        source_root = Path(__file__).resolve().parents[2]
        payload = {
            "associago": {
                "environment": preparation.spa_environment("https://associago.example", settings),
                "source": (source_root / "AssociaGo/desktop/src/renderer/src/api.js").read_text(encoding="utf-8"),
            },
            "librepm": {
                "environment": preparation.spa_environment("https://librepm.example", settings),
                "source": (source_root / "LibrePM/desktop/src/api/librepm.js").read_text(encoding="utf-8"),
            },
        }
        script = r"""
const fs = require('node:fs');
const assert = require('node:assert/strict');
const input = JSON.parse(fs.readFileSync(0, 'utf8'));
const calls = [];
console.log = () => {};
global.window = {};
global.fetch = async (url, options) => {
  calls.push({ url, authorization: options.headers.Authorization });
  return new Response(JSON.stringify({id: 'synthetic-user', roles: ['ADMIN']}), {
    headers: {'Content-Type': 'application/json'},
  });
};
async function load(entry) {
  const source = entry.source.replaceAll('import.meta.env', '(' + JSON.stringify(entry.environment) + ')');
  return import('data:text/javascript;base64,' + Buffer.from(source).toString('base64'));
}
(async () => {
  const associago = (await load(input.associago)).associago;
  associago.setTokenProvider(async () => 'synthetic-session');
  await associago.getCurrentProfile();
  await associago.listAssociations();
  const librepm = (await load(input.librepm)).default;
  librepm.setTokenProvider(async () => 'synthetic-session');
  await librepm.init();
  await librepm.me();
  assert.deepEqual(calls.map(call => call.url), [
    'https://associago.example/api/v1/me',
    'https://associago.example/api/v1/associations',
    'https://librepm.example/api/me',
  ]);
  assert.ok(calls.every(call => call.authorization === 'Bearer synthetic-session'));
  process.stdout.write(JSON.stringify({ profileRequests: calls.length, passed: true }));
})().catch(() => { process.stderr.write('Actual client API contract failed'); process.exitCode = 1; });
"""
        result = subprocess.run(
            [shutil.which("node") or "node", "-e", script],
            input=json.dumps(payload), text=True, encoding="utf-8",
            capture_output=True, timeout=15, check=False,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(json.loads(result.stdout), {"profileRequests": 3, "passed": True})


if __name__ == "__main__":
    unittest.main()
