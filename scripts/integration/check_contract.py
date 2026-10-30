"""Validate the suite's one canonical OpenAPI boundary and serialized responses.

Runs offline. References may only address schemas in this repository's contract.
Usage: python scripts/integration/check_contract.py [--response-log responses.json]
"""

from __future__ import annotations

import argparse
import hashlib
import ipaddress
import json
from pathlib import Path
import sys
from urllib.parse import parse_qs, urlsplit

try:
    from jsonschema import Draft202012Validator, FormatChecker
except ImportError as exc:
    raise SystemExit(
        "Install validation dependencies: python -m pip install -r integration/requirements.txt"
    ) from exc

ROOT = Path(__file__).resolve().parents[2]
CONTRACT_PATH = ROOT / "integration" / "suite-v1.openapi.json"
FIXTURES = ROOT / "integration" / "fixtures"


def load_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def localize(value):
    """Reject external references, then make component schemas Draft 2020-12."""
    if isinstance(value, list):
        return [localize(item) for item in value]
    if not isinstance(value, dict):
        return value
    result = {}
    for key, item in value.items():
        if key == "$ref":
            prefix = "#/components/schemas/"
            if not isinstance(item, str) or not item.startswith(prefix):
                raise ValueError(f"Only local component schema references are allowed: {item!r}")
            name = item[len(prefix) :]
            if "/" in name or "~" in name or not name:
                raise ValueError(f"Invalid component reference: {item!r}")
            result[key] = "#/$defs/" + name
        else:
            result[key] = localize(item)
    return result


class Contract:
    def __init__(self, path: Path = CONTRACT_PATH):
        self.spec = load_json(path)
        if self.spec.get("openapi") != "3.1.0":
            raise ValueError("Expected OpenAPI 3.1.0")
        self.schemas = localize(self.spec["components"]["schemas"])
        for schema in self.schemas.values():
            Draft202012Validator.check_schema(schema)
        # Scan all references, including operations, without fetching any origin.
        localize(self.spec)
        self._validators = {}

    def validate(self, schema: str, payload):
        if schema not in self.schemas:
            raise ValueError(f"Unknown schema: {schema}")
        if schema not in self._validators:
            self._validators[schema] = Draft202012Validator(
                {"$ref": "#/$defs/" + schema, "$defs": self.schemas},
                format_checker=FormatChecker(),
            )
        errors = sorted(
            self._validators[schema].iter_errors(payload), key=lambda error: str(error.json_path)
        )
        if errors:
            raise ValueError("; ".join(f"{error.json_path}: {error.message}" for error in errors))
        self._validate_links(payload)

    def _validate_links(self, payload):
        if isinstance(payload, list):
            for item in payload:
                self._validate_links(item)
        if isinstance(payload, dict):
            for app, url in payload.get("links", {}).items():
                if url is not None:
                    validate_url(url, deep_link=True)
            for key in ("financial", "workforce", "preparation"):
                value = payload.get(key)
                if value is not None and not isinstance(value, dict):
                    raise ValueError(f"{key} must be an aggregate or null")

    def validate_http(self, entry: dict):
        """A capture contains only system/method/path/status/payload, never headers."""
        forbidden = {"headers", "requestHeaders", "key", "secret", "cookie", "authorization"}
        if forbidden.intersection(entry):
            raise ValueError("Response evidence must not contain authentication material")
        system = entry["system"]
        method = entry["method"].lower()
        path = entry["path"]
        operation = self.spec["paths"][path][method]
        if system not in operation["x-suite-systems"]:
            raise ValueError(f"Operation {method} {path} is not provided by {system}")
        status = str(entry["status"])
        response = operation["responses"].get(status)
        if response is None:
            raise ValueError(f"Undocumented response status {status}")
        schema = response["content"]["application/json"]["schema"]["$ref"].split("/")[-1]
        if status == "200":
            schema = operation.get("x-suite-response-schemas", {}).get(system, schema)
        self.validate(schema, entry["payload"])


def validate_url(url: str, deep_link: bool = False):
    parsed = urlsplit(url)
    if parsed.username is not None or parsed.password is not None or parsed.fragment:
        raise ValueError("URL cannot contain credentials or a fragment")
    host = parsed.hostname
    if not host:
        raise ValueError("URL needs an absolute host")
    loopback = host.lower() == "localhost"
    try:
        loopback = loopback or ipaddress.ip_address(host).is_loopback
    except ValueError:
        pass
    if parsed.scheme != "https" and not (parsed.scheme == "http" and loopback):
        raise ValueError("HTTPS is required outside loopback")
    if parsed.query:
        if not deep_link:
            raise ValueError("Configured origins cannot contain a query")
        query = parse_qs(parsed.query, keep_blank_values=True, strict_parsing=True)
        if set(query) != {"suiteProject"} or len(query["suiteProject"]) != 1:
            raise ValueError("Deep links accept only one suiteProject query parameter")
        import uuid

        uuid.UUID(query["suiteProject"][0])
    # Access .port to reject malformed or out-of-range ports.
    _ = parsed.port


def check_structure(contract: Contract):
    checks = 0
    schemes = contract.spec["components"]["securitySchemes"]
    for path, item in contract.spec["paths"].items():
        if not path.startswith("/api/integrations/v1/"):
            raise ValueError(f"Route outside the versioned suite boundary: {path}")
        for method, operation in item.items():
            if method == "parameters":
                continue
            if not operation.get("operationId") or not operation.get("x-suite-systems"):
                raise ValueError(f"Missing operation ownership: {method} {path}")
            for security in operation["security"]:
                if not set(security).issubset(schemes):
                    raise ValueError("Unknown security scheme")
            if "/peer/" in path and operation["security"] != [
                {"SuitePeer": [], "SuiteKey": [], "SuiteOrganization": []}
            ]:
                raise ValueError(f"All peer headers must be required together: {path}")
            checks += 1
    return checks


def check_fixtures(contract: Contract):
    count = 0
    for fixture in load_json(FIXTURES / "manifest.json"):
        contract.validate(fixture["schema"], load_json(FIXTURES / fixture["file"]))
        count += 1
    for case in load_json(FIXTURES / "negative.json"):
        try:
            contract.validate(case["schema"], case["payload"])
        except ValueError:
            count += 1
        else:
            raise ValueError(f"Invalid fixture accepted: {case['name']}")
    for case in load_json(FIXTURES / "url-security.json"):
        try:
            validate_url(case["url"], case.get("deepLink", False))
            valid = True
        except (ValueError, TypeError):
            valid = False
        if valid != case["valid"]:
            raise ValueError(f"URL security expectation failed: {case['name']}")
        count += 1
    # An external $ref must never cause a network lookup or repository escape.
    for ref in ("https://example.invalid/schema.json", "../other.json", "file:///private.json"):
        try:
            localize({"$ref": ref})
        except ValueError:
            count += 1
        else:
            raise ValueError("External schema reference accepted")
    return count


def check_snapshots(required: bool = False):
    """Verify byte-identical release copies, with no manifest-controlled paths."""
    filenames = {"suite-v1.openapi.json", "SUITE-V1-CONTRACT.md"}
    count = 0
    for repository in ("LibrePM", "Bandi"):
        directory = ROOT.parent / repository / "integration"
        manifest_path = directory / "SUITE-CONTRACT-SNAPSHOT.json"
        if not manifest_path.exists():
            if required:
                raise ValueError(f"Missing contract snapshot: {repository}/integration")
            continue
        manifest = load_json(manifest_path)
        if manifest.get("canonicalRepository") != "AssociaGo" or manifest.get("canonicalDirectory") != "integration":
            raise ValueError(f"Unexpected snapshot provenance: {repository}")
        entries = manifest.get("files", [])
        if {entry.get("path") for entry in entries} != filenames or len(entries) != len(filenames):
            raise ValueError(f"Unexpected snapshot files: {repository}")
        for entry in entries:
            filename = entry["path"]  # Exact allowlist above, never an arbitrary path.
            expected = hashlib.sha256((ROOT / "integration" / filename).read_bytes()).hexdigest()
            actual = hashlib.sha256((directory / filename).read_bytes()).hexdigest()
            if entry.get("sha256") != expected or actual != expected:
                raise ValueError(f"Contract snapshot drift: {repository}/integration/{filename}")
        count += 1
    return count


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--response-log", type=Path, help="JSON array of sanitized real HTTP captures")
    parser.add_argument("--schema", help="Validate one payload against a named component schema")
    parser.add_argument("--payload", type=Path, help="Payload JSON file used with --schema")
    parser.add_argument("--check-snapshots", action="store_true", help="Require and verify release snapshots in both sibling repositories")
    args = parser.parse_args()
    try:
        contract = Contract()
        operations = check_structure(contract)
        count = check_fixtures(contract)
        snapshots = check_snapshots(required=args.check_snapshots)
        captured = 0
        if bool(args.schema) != bool(args.payload):
            parser.error("--schema and --payload must be provided together")
        if args.schema:
            contract.validate(args.schema, load_json(args.payload))
            captured += 1
        if args.response_log:
            for entry in load_json(args.response_log):
                contract.validate_http(entry)
                captured += 1
        print(f"PASS: {operations} HTTP operations, {count} contract/security fixtures, {captured} captured responses, {snapshots} release snapshots")
    except (KeyError, ValueError, OSError) as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
