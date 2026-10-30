"""Run all three real HTTP backends on loopback with synthetic, disposable data.

Requires freshly built bootJars and Bandi's existing virtualenv. No app .env,
database credentials, cloud endpoints or user databases are read. The hard
deadline is 240 seconds and every child is terminated in finally.
"""

from __future__ import annotations

import argparse
import copy
from contextlib import closing
from decimal import Decimal
import http.cookiejar
import json
import os
from pathlib import Path
import secrets
import shutil
import socket
import sqlite3
import subprocess
import sys
import tempfile
import threading
import time
from urllib.error import HTTPError, URLError
from urllib.request import HTTPCookieProcessor, ProxyHandler, Request, build_opener
import uuid

from check_contract import Contract, ROOT

PREFIX = "/api/integrations/v1"
ORG = "9b8bd5f6-e4d9-4a1a-9f3d-44a3f969f401"
PREPARATION = "72576a41-1783-4aaf-9c31-758901e74dbc"
FOREIGN_PREPARATION = "93569ea8-e1d4-4acd-ab7c-d277cb638ce0"
OWNER = "3adf2ef0-5372-40ea-ac1b-7985c02c61e6"


def port():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


def clean_environment():
    allowed = {"PATH", "SYSTEMROOT", "WINDIR", "COMSPEC", "PATHEXT", "TEMP", "TMP", "JAVA_HOME"}
    return {key: value for key, value in os.environ.items() if key.upper() in allowed}


class Smoke:
    def __init__(self, directory: Path, output: Path):
        self.directory = directory
        self.output = output
        self.contract = Contract()
        self.children = []
        self.logs = []
        self.captures = []
        self.checks = []
        self.urls = {system: f"http://127.0.0.1:{port()}" for system in ("librepm", "associago", "bandi")}
        self.keys = {system: secrets.token_urlsafe(40) for system in ("associago", "bandi")}
        self.opener = build_opener(ProxyHandler({}), HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.deadline = time.monotonic() + 240

    def stop(self):
        for child in reversed(self.children):
            if child.poll() is None:
                self.terminate(child)
        for child in reversed(self.children):
            try:
                child.wait(timeout=8)
            except subprocess.TimeoutExpired:
                child.kill()
                child.wait(timeout=8)
        for log in self.logs:
            log.close()

    @staticmethod
    def terminate(child):
        if child.poll() is not None:
            return
        if os.name == "nt":
            # Windows virtualenv redirectors can spawn a base Python child.
            # Stop only the process tree that this harness itself created.
            subprocess.run(["taskkill", "/PID", str(child.pid), "/T", "/F"],
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=8, check=False)
        else:
            child.terminate()

    def start(self, name, command, env):
        log = (self.directory / (name + ".log")).open("w", encoding="utf-8")
        self.logs.append(log)
        child = subprocess.Popen(command, cwd=self.directory, env=env, stdout=log, stderr=subprocess.STDOUT)
        self.children.append(child)
        return child

    def request(self, system, method, path, body=None, headers=None):
        if time.monotonic() > self.deadline:
            raise TimeoutError("Native smoke deadline exceeded")
        data = None if body is None else json.dumps(body).encode("utf-8")
        req = Request(self.urls[system] + path, data=data, method=method,
                      headers={"Accept": "application/json", "Content-Type": "application/json", **(headers or {})})
        try:
            response = self.opener.open(req, timeout=8)
        except HTTPError as error:
            response = error
        with response:
            raw = response.read(2 * 1024 * 1024)
            return response.status, json.loads(raw) if raw else None

    def ready(self, name, path):
        until = min(self.deadline, time.monotonic() + 45)
        while time.monotonic() < until:
            try:
                status, _ = self.request(name, "GET", path)
                if status < 500:
                    return
            except (URLError, TimeoutError, ConnectionError, json.JSONDecodeError):
                pass
            if any(child.poll() is not None for child in self.children):
                raise RuntimeError(f"A native child exited while waiting for {name}; see local test logs")
            time.sleep(0.2)
        raise TimeoutError(f"Native {name} did not become ready within 45 seconds")

    def peer(self, system, peer=None, organization=ORG):
        source = peer or ("bandi" if system == "librepm" else "librepm")
        key = self.keys[source] if system == "librepm" else self.keys[system]
        return {"X-Suite-Peer": source, "X-Suite-Key": key, "X-Suite-Organization": organization}

    def expect(self, name, system, method, template, expected, body=None, headers=None, suite_id=None):
        path = template.replace("{suiteProjectId}", suite_id or str(uuid.uuid4()))
        status, payload = self.request(system, method, path, body, headers)
        if status != expected:
            # Do not echo headers, cookies, request bodies or exception internals.
            code = payload.get("code", payload.get("error", "unknown")) if isinstance(payload, dict) else "non-json"
            raise AssertionError(f"{name}: {system} {method} expected {expected}, received {status} ({code})")
        capture = {"system": system, "method": method, "path": template, "status": status, "payload": payload}
        self.contract.validate_http(capture)
        self.captures.append(capture)
        self.checks.append(name)
        return payload

    def persist_evidence(self):
        self.output.parent.mkdir(parents=True, exist_ok=True)
        self.output.write_text(json.dumps(self.captures, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    def persist_diagnostics(self):
        self.output.parent.mkdir(parents=True, exist_ok=True)
        for path in self.directory.glob("*.log"):
            content = path.read_text(encoding="utf-8", errors="replace")
            for key in self.keys.values():
                content = content.replace(key, "[redacted-test-key]")
            # Keep bounded, local diagnostics; they are ignored by Git.
            target = self.output.parent / path.name
            target.write_text("\n".join(content.splitlines()[-100:]) + "\n", encoding="utf-8")
        for path in self.directory.glob("*-received-projection.json"):
            shutil.copyfile(path, self.output.parent / path.name)


def run(args, smoke):
    java = shutil.which("java")
    if not java:
        raise RuntimeError("Java 21 is required")
    common = {"SUITE_ENABLED": "true", "SUITE_ORGANIZATION_ID": ORG,
              "SUITE_ASSOCIAGO_KEY": smoke.keys["associago"], "SUITE_BANDI_KEY": smoke.keys["bandi"]}
    for system, url in smoke.urls.items():
        common[f"SUITE_{system.upper()}_API_URL"] = url
        common[f"SUITE_{system.upper()}_PUBLIC_URL"] = url
    libre_data = smoke.directory / "librepm"
    as_data = smoke.directory / "associago"
    libre_data.mkdir()
    as_data.mkdir()
    # The desktop data source is explicitly configured in this disposable path.
    (as_data / "config.json").write_text(json.dumps({"configured": True, "dbType": "SQLITE"}), encoding="utf-8")
    for system, jar, data, extra in [
        ("librepm", args.librepm_jar, libre_data, {"LIBREPM_DATA_PATH": str(libre_data), "SUITE_IMPORT_OWNER_ID": OWNER,
                                                "SUITE_SYNC_INTERVAL_MS": "3600000", "SUITE_SYNC_INITIAL_DELAY_MS": "3600000"}),
        ("associago", args.associago_jar, as_data, {"SUITE_ASSOCIATION_ID": "1"}),
    ]:
        # Other agents may rebuild build/libs while this smoke test is running.
        # Snapshot each artifact before Java opens it, keeping active jars stable.
        jar_snapshot = smoke.directory / (system + "-test.jar")
        shutil.copyfile(jar.resolve(), jar_snapshot)
        env = {**clean_environment(), **common, **extra}
        smoke.start(system, [java, f"-D{system}.data.path={data}", "-jar", str(jar_snapshot),
                            "--spring.profiles.active=desktop", "--server.address=127.0.0.1",
                            "--server.port=" + smoke.urls[system].rsplit(":", 1)[1],
                            "--logging.level.root=WARN", "--logging.level.org.hibernate.SQL=WARN"], env)
    smoke.ready("librepm", "/api/bootstrap")
    smoke.ready("associago", "/actuator/health")
    with closing(sqlite3.connect(libre_data / "librepm.db", timeout=10)) as database, database:
        columns = {row[1] for row in database.execute("PRAGMA table_info(users)")}
        values = {"id": OWNER, "username": "suite-smoke", "display_name": "Synthetic suite owner",
                  "password_hash": "not-used-for-desktop-smoke", "active": 1, "is_ghost": 0,
                  "created_at": "2026-10-29 00:00:00.000", "updated_at": "2026-10-29 00:00:00.000",
                  "version": 0, "sync_status": "LOCAL_ONLY"}
        values = {key: value for key, value in values.items() if key in columns}
        database.execute("INSERT INTO users (" + ",".join(values) + ") VALUES (" + ",".join("?" for _ in values) + ")", list(values.values()))
    with closing(sqlite3.connect(as_data / "associago.db", timeout=10)) as database, database:
        database.execute("INSERT INTO associations (id,name) VALUES (1,'Synthetic smoke association')")
    password = secrets.token_urlsafe(24)
    config = smoke.directory / "bandi-test-config.json"
    config.write_text(json.dumps({"organizationId": ORG, "preparationId": PREPARATION,
                                 "foreignPreparationId": FOREIGN_PREPARATION, "key": smoke.keys["bandi"],
                                 "sessionSecret": secrets.token_urlsafe(48), "password": password,
                                 "registryUrl": smoke.urls["librepm"]}), encoding="utf-8")
    bandi_python = args.bandi_repo / ".venv" / ("Scripts/python.exe" if os.name == "nt" else "bin/python")
    bandi_child = smoke.start("bandi", [str(bandi_python), str(Path(__file__).with_name("bandi_test_server.py")),
                                      "--repo", str(args.bandi_repo.resolve()), "--config", str(config),
                                      "--port", smoke.urls["bandi"].rsplit(":", 1)[1]], clean_environment())
    smoke.ready("bandi", "/auth/me")
    print("Three native backends ready; verifying the shared project workflow.", flush=True)
    smoke.expect("bandi-user-auth-required", "bandi", "GET", PREFIX + "/projects", 401)
    peer_path = PREFIX + "/peer/projects/{suiteProjectId}"
    for system in smoke.urls:
        smoke.expect("missing-peer-auth", system, "GET", peer_path, 401)
        headers = smoke.peer(system)
        headers["X-Suite-Key"] = "invalid-test-key"
        smoke.expect("invalid-peer-key", system, "GET", peer_path, 401, headers=headers)
        smoke.expect("foreign-organization-header", system, "GET", peer_path, 403,
                     headers=smoke.peer(system, organization=str(uuid.uuid4())))
        smoke.expect("malformed-project-uuid", system, "GET", peer_path, 400,
                     headers=smoke.peer(system), suite_id="not-a-uuid")
    spoofed = json.loads((ROOT / "integration/fixtures/seed-associago.json").read_text(encoding="utf-8"))
    smoke.expect("native-source-identity-spoof-rejected", "librepm", "POST", PREFIX + "/peer/projects", 403,
                 body=spoofed, headers=smoke.peer("librepm"))
    status, _ = smoke.request("bandi", "POST", "/auth/login", {"username": "suite-smoke", "password": password})
    if status != 200:
        raise AssertionError("Synthetic operator login failed")
    shared = smoke.expect("bandi-share-through-real-registry", "bandi", "POST", PREFIX + "/projects/share", 200,
                          body={"localId": PREPARATION})
    sid = shared["suiteProjectId"]
    repeated = smoke.expect("bandi-share-idempotent", "bandi", "POST", PREFIX + "/projects/share", 200,
                            body={"localId": PREPARATION})
    assert repeated["suiteProjectId"] == sid, "Repeated share duplicated the registry project"
    context = smoke.expect("three-native-services-sync", "librepm", "POST", PREFIX + "/peer/projects/{suiteProjectId}/sync", 200,
                           headers=smoke.peer("librepm"), suite_id=sid)
    assert {state["system"]: state["status"] for state in context["sync"]} == {"associago": "synced", "bandi": "synced"}, "Both native peers must sync successfully"
    assert Decimal(context["financial"]["income"]) == 0, "A planned budget must not generate income"
    assert context["preparation"]["localId"] == PREPARATION, "Preparation mapping was lost"
    smoke.expect("bandi-open-shared-project", "bandi", "GET", PREFIX + "/projects/{suiteProjectId}", 200, suite_id=sid)
    smoke.expect("bandi-reject-foreign-profile", "bandi", "POST", PREFIX + "/projects/{suiteProjectId}/link", 403,
                 body={"localId": FOREIGN_PREPARATION}, suite_id=sid)
    for system in ("associago", "bandi"):
        smoke.expect("native-peer-snapshot", system, "GET", peer_path, 200, headers=smoke.peer(system), suite_id=sid)
    with closing(sqlite3.connect(libre_data / "librepm.db")) as database:
        assert database.execute("SELECT count(*) FROM projects").fetchone()[0] == 1, "Duplicate registry project"
    with closing(sqlite3.connect(as_data / "associago.db")) as database:
        assert database.execute("SELECT count(*) FROM projects").fetchone()[0] == 1, "Duplicate local accounting project"
        assert database.execute("SELECT count(*) FROM transactions").fetchone()[0] == 0, "Budget generated ledger transactions"
    accounting_id = next(item["localId"] for item in context["instances"] if item["system"] == "associago")
    reshared = smoke.expect("associago-share-existing-mirror-reuses-registry-project", "associago", "POST",
                            PREFIX + "/projects/share", 200, body={"localId": accounting_id})
    assert reshared["suiteProjectId"] == sid, "Re-sharing an existing accounting mirror created another suite project"
    catalog = smoke.expect("registry-count-after-accounting-reshare", "librepm", "GET", PREFIX + "/peer/projects", 200,
                           headers=smoke.peer("librepm"))
    assert len(catalog) == 1, "Accounting re-share duplicated the registry project"
    revised = json.loads((ROOT / "integration/fixtures/seed-bandi.json").read_text(encoding="utf-8"))
    revised["source"]["revision"] = "different-opaque-revision"
    revised["title"] = "Candidate title must not replace the operating plan"
    preserved = smoke.expect("changed-seed-revision-preserves-operating-plan", "librepm", "POST",
                             PREFIX + "/peer/projects", 200, body=revised, headers=smoke.peer("librepm"))
    assert preserved["suiteProjectId"] == sid and preserved["title"] == context["title"], "Seed retry overwrote the operating plan"
    unrepresentable = copy.deepcopy(revised)
    unrepresentable["source"]["localId"] = str(uuid.uuid4())
    unrepresentable["plannedBudget"] = {"amount": "9007199254740993", "currency": "EUR"}
    smoke.expect("registry-rejects-lossy-native-budget", "librepm", "POST", PREFIX + "/peer/projects", 409,
                 body=unrepresentable, headers=smoke.peer("librepm"))
    other_seed = copy.deepcopy(revised)
    other_seed["source"]["localId"] = str(uuid.uuid4())
    other_seed["title"] = "Independent synthetic suite project"
    other = smoke.expect("register-second-project-for-link-conflict", "librepm", "POST", PREFIX + "/peer/projects", 200,
                         body=other_seed, headers=smoke.peer("librepm"))
    smoke.expect("accounting-preflight-rejects-rebind", "associago", "POST", PREFIX + "/projects/{suiteProjectId}/link", 409,
                 body={"localId": accounting_id}, suite_id=other["suiteProjectId"])
    untouched = smoke.expect("rejected-accounting-rebind-does-not-mutate-registry", "librepm", "GET", peer_path, 200,
                             headers=smoke.peer("librepm"), suite_id=other["suiteProjectId"])
    assert not any(item["system"] == "associago" for item in untouched["instances"]), "Rejected local rebind created a remote instance"
    catalog = smoke.expect("registry-count-after-rejected-rebind", "librepm", "GET", PREFIX + "/peer/projects", 200,
                           headers=smoke.peer("librepm"))
    assert len(catalog) == 2, "Rejected link or lossy budget import created an extra registry project"
    projection = json.loads((ROOT / "integration/fixtures/projection-linked.json").read_text(encoding="utf-8"))
    projection.update(suiteProjectId=str(uuid.uuid4()), sourceVersion=2, targetLocalId=None)
    for system in ("associago", "bandi"):
        first = smoke.expect("native-upsert", system, "PUT", peer_path, 200, body=projection,
                             headers=smoke.peer(system), suite_id=projection["suiteProjectId"])
        retry = smoke.expect("native-upsert-idempotent", system, "PUT", peer_path, 200, body=projection,
                             headers=smoke.peer(system), suite_id=projection["suiteProjectId"])
        assert retry["localId"] == first["localId"], "Idempotent upsert created another local record"
        stale = copy.deepcopy(projection)
        stale["sourceVersion"] = 1
        smoke.expect("native-stale-version", system, "PUT", peer_path, 409, body=stale,
                     headers=smoke.peer(system), suite_id=projection["suiteProjectId"])
        conflict = copy.deepcopy(projection)
        conflict["title"] = "Different content at same version"
        smoke.expect("native-version-conflict", system, "PUT", peer_path, 409, body=conflict,
                     headers=smoke.peer(system), suite_id=projection["suiteProjectId"])
        foreign = copy.deepcopy(projection)
        foreign["organizationId"] = str(uuid.uuid4())
        smoke.expect("native-body-org-mismatch", system, "PUT", peer_path, 403, body=foreign,
                     headers=smoke.peer(system), suite_id=projection["suiteProjectId"])
        smoke.expect("native-path-body-id-mismatch", system, "PUT", peer_path, 400, body=projection,
                     headers=smoke.peer(system), suite_id=str(uuid.uuid4()))
        overflow = copy.deepcopy(projection)
        overflow["sourceVersion"] = 2147483648
        smoke.expect("native-version-overflow-rejected", system, "PUT", peer_path, 400, body=overflow,
                     headers=smoke.peer(system), suite_id=projection["suiteProjectId"])
    for budget, name in [({"amount": "1", "currency": "USD"}, "accounting-currency-conflict"),
                         ({"amount": "1.001", "currency": "EUR"}, "accounting-subcent-conflict")]:
        invalid = copy.deepcopy(projection)
        invalid["plannedBudget"] = budget
        smoke.expect(name, "associago", "PUT", peer_path, 409, body=invalid,
                     headers=smoke.peer("associago"), suite_id=projection["suiteProjectId"])
    smoke.terminate(bandi_child)
    bandi_child.wait(timeout=8)
    partial = smoke.expect("native-peer-outage-isolated", "librepm", "POST", PREFIX + "/peer/projects/{suiteProjectId}/sync", 200,
                           headers=smoke.peer("librepm"), suite_id=sid)
    statuses = {state["system"]: state["status"] for state in partial["sync"]}
    assert statuses == {"associago": "synced", "bandi": "error"}, "One unavailable peer must not fail the other"
    assert partial["financial"] is not None, "Healthy accounting peer summary lost during another peer outage"
    smoke.persist_evidence()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--librepm-jar", type=Path, required=True)
    parser.add_argument("--associago-jar", type=Path, required=True)
    parser.add_argument("--bandi-repo", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=ROOT / "integration/.artifacts/native-responses.json")
    args = parser.parse_args()
    for jar in (args.librepm_jar, args.associago_jar):
        if not jar.is_file() or jar.suffix != ".jar":
            parser.error(f"Fresh bootJar required: {jar}")
    with tempfile.TemporaryDirectory(prefix="suite-native-smoke-") as directory:
        smoke = Smoke(Path(directory), args.output.resolve())
        watchdog = threading.Timer(240, smoke.stop)
        watchdog.daemon = True
        watchdog.start()
        try:
            run(args, smoke)
            print(f"PASS: {len(smoke.checks)} native HTTP checks; sanitized evidence: {args.output}")
        except Exception:
            # Evidence from completed calls is still useful, never contains credentials.
            smoke.persist_evidence()
            smoke.persist_diagnostics()
            raise
        finally:
            watchdog.cancel()
            smoke.stop()


if __name__ == "__main__":
    main()
