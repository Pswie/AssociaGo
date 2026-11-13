"""Bounded smoke of an immutable gateway image on disposable local PostgreSQL.

No production credentials, configured database URL, public application port,
provider call, remote mutation, file deletion or existing container is used.
The outer process is an independent OS watchdog. Every created container is
checked by its immutable ID, unique label and name before cleanup.
"""

from __future__ import annotations

import argparse
import ctypes
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import sys
import threading
import time
from uuid import uuid4

ROOT = Path(__file__).resolve().parents[1]
PG_IMAGE = "pgvector/pgvector:0.8.5-pg17"
LABEL = "codex.association.image-smoke"
DEADLINE = 240


def stamp() -> str:
    return datetime.now(timezone.utc).isoformat()


def save(path: Path, **values) -> None:
    for attempt in range(5):
        pending = path.with_name(path.name + "." + uuid4().hex + ".pending")
        try:
            current = json.loads(path.read_text()) if path.exists() else {}
            current.update(values, updated_at=stamp())
            pending.write_text(json.dumps(current, indent=2), encoding="utf-8")
            os.replace(pending, path)
            return
        except (OSError, ValueError):
            time.sleep(0.03 * (attempt + 1))
    try:
        with path.with_name("state-errors.log").open("a") as stream:
            stream.write(stamp() + " STATE_WRITE_FAILED\n")
    except OSError:
        pass


def birth(pid: int) -> str | None:
    if os.name == "nt":
        from ctypes import wintypes

        kernel = ctypes.WinDLL("kernel32", use_last_error=True)
        kernel.OpenProcess.argtypes = [wintypes.DWORD, wintypes.BOOL, wintypes.DWORD]
        kernel.OpenProcess.restype = wintypes.HANDLE
        kernel.GetProcessTimes.argtypes = [wintypes.HANDLE] + [
            ctypes.POINTER(wintypes.FILETIME)
        ] * 4
        kernel.CloseHandle.argtypes = [wintypes.HANDLE]
        handle = kernel.OpenProcess(0x1000, False, pid)
        if not handle:
            return None
        try:
            values = [wintypes.FILETIME() for _ in range(4)]
            if not kernel.GetProcessTimes(
                handle, *(ctypes.byref(value) for value in values)
            ):
                return None
            return "windows-filetime:" + str(
                (values[0].dwHighDateTime << 32) + values[0].dwLowDateTime
            )
        finally:
            kernel.CloseHandle(handle)
    try:
        return (
            "proc:"
            + Path(f"/proc/{pid}/stat").read_text().rsplit(")", 1)[1].split()[19]
        )
    except (OSError, IndexError):
        return None


def docker(*arguments, timeout=25, environment=None) -> str:
    result = subprocess.run(
        ["docker", *arguments],
        capture_output=True,
        text=True,
        timeout=timeout,
        env=environment,
    )
    if result.returncode:
        raise RuntimeError(
            "Docker operation failed; details are not copied to a public receipt"
        )
    return result.stdout.strip()


def cleanup(path: Path) -> None:
    data = json.loads(path.read_text())
    closed = data.get("containers_closed", [])
    for kind in ("app", "database"):
        name = data[kind + "_name"]
        probe = subprocess.run(
            ["docker", "inspect", name], capture_output=True, text=True, timeout=20
        )
        if probe.returncode:
            continue
        item = json.loads(probe.stdout)[0]
        labels = item.get("Config", {}).get("Labels", {}) or {}
        expected_id = data.get(kind + "_id")
        if (
            item["Name"] != "/" + name
            or labels.get(LABEL) != data["run_id"]
            or labels.get(LABEL + ".kind") != kind
            or expected_id is not None
            and item["Id"] != expected_id
        ):
            raise RuntimeError("Owned smoke container identity does not match")
        # Only this newly-created labelled fixture; never a path or user container.
        docker("rm", "--force", item["Id"])
        if not any(row["id"] == item["Id"] for row in closed):
            closed.append({"kind": kind, "id": item["Id"]})
    save(path, containers_closed=closed, cleanup_complete=True)


PROGRAM = r"""
import base64, hashlib, io, json, os, secrets, sys, traceback, zipfile
from datetime import datetime, timezone, timedelta
from pathlib import Path
from uuid import uuid4
from urllib.parse import quote
import jwt
import psycopg
from cryptography.hazmat.primitives.asymmetric import rsa
from fastapi.testclient import TestClient
from apps.api.config import Settings
from apps.api.collaboration.migrate import migrate_portal
from apps.api.collaboration.gateway import create_gateway_app
from apps.api.collaboration.security import TokenVerifier

cfg = __CONFIG__
phase = 'starting'
checks = []
def passed(name):
    checks.append(name)
    print(json.dumps({'stage': name}), flush=True)
try:
    password = quote(cfg['password'], safe='')
    runtime = 'postgresql://bandi_portal_app:' + password + '@127.0.0.1:5432/postgres'
    owner_url = 'postgresql://bandi_portal_owner:' + password + '@127.0.0.1:5432/postgres'
    issuer = 'https://synthetic.clerk.accounts.dev'
    settings = Settings(_env_file=None, app_env='test', bandi_mode='gateway',
        collab_database_url=runtime, collab_migration_database_url=owner_url,
        collab_clerk_issuer=issuer, collab_owner_subject='user_smoke_owner',
        collab_allowed_subjects='user_smoke_editor,user_smoke_viewer,user_smoke_other',
        collab_node_id='image-smoke', collab_node_key=secrets.token_urlsafe(40),
        collab_cron_key=secrets.token_urlsafe(40), cors_origins='https://fixture.invalid',
        agno_enabled=False)
    phase = 'migrations'
    migrate_portal(settings)
    with psycopg.connect(owner_url) as connection:
        connection.execute('REVOKE ALL ON bandi_portal.portal_schema_migrations FROM bandi_portal_app')
        connection.execute('REVOKE UPDATE,DELETE ON bandi_portal.portal_revisions FROM bandi_portal_app')
        actual = dict(connection.execute('SELECT version,checksum FROM bandi_portal.portal_schema_migrations'))
        expected = {int(p.name.split('_')[0]): hashlib.sha256(p.read_text().encode()).hexdigest()
                    for p in Path('/app/apps/api/collaboration/migrations').glob('*.sql')}
        assert actual == expected and {6,7} <= set(actual)
        tables = [r[0] for r in connection.execute("SELECT tablename FROM pg_tables WHERE schemaname='bandi_portal' AND tablename LIKE 'association_%'")]
        assert len(tables) == 6
        for table in tables:
            target = 'bandi_portal.' + table
            for role in ('anon','authenticated'):
                assert connection.execute("SELECT has_table_privilege(%s,%s,'SELECT,INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES,TRIGGER')",(role,target)).fetchone()[0] is False
        for table in ('association_profile_documents','association_profile_revisions','association_requirement_sets'):
            assert connection.execute("SELECT has_table_privilege('bandi_portal_app',%s,'UPDATE,DELETE,TRUNCATE')",('bandi_portal.'+table,)).fetchone()[0] is False
    passed('actual_migrations_and_private_runtime_grants')

    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    def headers(subject='user_smoke_owner'):
        now = datetime.now(timezone.utc)
        return {'Authorization':'Bearer '+jwt.encode({'iss':issuer,'sub':subject,'iat':now,'exp':now+timedelta(minutes=5),'azp':'https://fixture.invalid'},key,algorithm='RS256')}
    app = create_gateway_app(settings, verifier=TokenVerifier(settings,key=key.public_key()))
    class Index:
        card = {'id':'c'*64,'title':'Synthetic sport call','officialUrl':'https://example.org/call',
                'sourceRevision':'a'*64,'inputHash':None,'needsRefresh':False,'lifecycle':{'deadline':'2099-12-31'}}
        async def page(self,query):
            assert query.limit == 5 and query.profile == 'all'
            return {'items':[self.card],'hasMore':False,'nextCursor':None}
        async def detail(self,identity):
            assert identity == 'c'*64
            return {'item':self.card}
        async def aclose(self): pass
    app.state.bandi_index = Index()
    with TestClient(app) as client:
        prefix = '/api/collaboration/v1'
        def request(method,path,payload=None,subject='user_smoke_owner',status=200):
            response = client.request(method,prefix+path,headers=headers(subject) if subject else {},json=payload)
            assert response.status_code == status,(method,path,response.status_code,status)
            return response.json() if response.content else None
        assert client.get('/health').status_code == 200
        request('GET','/associations',subject=None,status=401)
        assert client.get('/docs').status_code == 404
        assert client.get('/api/calls').status_code == 404
        phase = 'profiles'
        def create(name):
            return request('POST','/associations',{'requestId':str(uuid4()),'name':name,'taxCode':None},status=201)
        first, second = create('Synthetic first association'), create('Synthetic second association')
        def member(profile,subject,role):
            return request('PUT','/associations/'+profile['id']+'/members/'+subject,{'expectedVersion':profile['version'],'role':role})
        first = member(first,'user_smoke_viewer','viewer')
        first = member(first,'user_smoke_editor','editor')
        second = member(second,'user_smoke_other','editor')
        own = request('GET','/associations',subject='user_smoke_editor')['items']
        assert [x['id'] for x in own] == [first['id']]
        request('GET','/associations/'+second['id'],subject='user_smoke_editor',status=404)
        request('PUT','/associations/'+first['id'],{'expectedVersion':first['version'],'name':first['name'],'taxCode':None,'facts':[]},subject='user_smoke_viewer',status=403)
        passed('authenticated_profile_memberships_and_tenant_isolation')

        def upload(profile,name,payload,kind='statute'):
            body = {'requestId':str(uuid4()),'expectedVersion':profile['version'],'kind':kind,
                    'filename':name,'contentBase64':base64.b64encode(payload).decode()}
            result = request('POST','/associations/'+profile['id']+'/documents',body,subject='user_smoke_editor',status=201)
            return result,body
        phase = 'linux_txt_parser'
        text = b'Associazione di promozione sociale. Finalita: sport e attivita culturali.'
        first, uploaded = upload(first,'statuto.txt',text)
        replay = request('POST','/associations/'+first['id']+'/documents',uploaded,subject='user_smoke_editor')
        assert replay['version'] == first['version'] and replay['documentCount'] == 1
        doc = first['documents'][0]
        original = request('GET','/associations/'+first['id']+'/documents/'+doc['id'],subject='user_smoke_editor')
        assert base64.b64decode(original['contentBase64']) == text
        assert original['sha256'] == hashlib.sha256(text).hexdigest()
        request('GET','/associations/'+second['id']+'/documents/'+doc['id'],status=404)
        request('GET','/associations/'+first['id']+'/documents/'+doc['id'],subject='user_smoke_other',status=404)
        passed('linux_isolated_txt_parser_idempotence_and_original_integrity')

        phase = 'linux_docx_parser'
        buffer = io.BytesIO()
        with zipfile.ZipFile(buffer,'w',zipfile.ZIP_DEFLATED) as archive:
            archive.writestr('[Content_Types].xml','<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"/>')
            archive.writestr('word/document.xml','<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>Associazione di promozione sociale. Sport ed educazione.</w:t></w:r></w:p></w:body></w:document>')
        first,_ = upload(first,'statuto.docx',buffer.getvalue())
        assert first['documents'][-1]['extractionStatus'] == 'extracted'
        passed('linux_isolated_docx_parser')

        phase = 'linux_pdf_parser'
        import pymupdf
        with pymupdf.open() as document:
            page = document.new_page()
            page.insert_text((72,72),'Associazione di promozione sociale. Sport e cultura.')
            pdf = document.tobytes()
        first,_ = upload(first,'statuto.pdf',pdf)
        pdf_document = first['documents'][-1]
        assert pdf_document['extractionStatus'] == 'extracted'
        proposals = pdf_document['proposals']
        activity = next(value for value in proposals if value['key'] == 'statutory_activities')
        assert 'sport' in activity['values'] and activity['evidence']['page'] == 1
        activity = {**activity,'status':'confirmed'}
        first = request('PUT','/associations/'+first['id'],{'expectedVersion':first['version'],'name':first['name'],'taxCode':None,'facts':[activity]},subject='user_smoke_editor')
        passed('linux_memory_bounded_pdf_parser_and_cited_confirmation')

        phase = 'financial_period'
        financial = b'Bilancio 2025: entrate complessive 12000 euro.'
        first,_ = upload(first,'bilancio.txt',financial,'financial')
        economic = first['documents'][-1]
        assert economic['proposals'] == []
        finance = {'key':'annual_revenue_eur','values':['12000'],'status':'confirmed','referencePeriod':'2025',
                   'evidence':{'documentId':economic['id'],'quote':financial.decode()}}
        first = request('PUT','/associations/'+first['id'],{'expectedVersion':first['version'],'name':first['name'],'taxCode':None,'facts':[activity,finance]},subject='user_smoke_editor')
        assert next(f for f in first['facts'] if f['key']=='annual_revenue_eur')['referencePeriod'] == '2025'
        request('PUT','/associations/'+first['id'],{'expectedVersion':1,'name':first['name'],'taxCode':None,'facts':[]},subject='user_smoke_editor',status=409)
        page = request('GET','/associations/'+first['id']+'/matches',subject='user_smoke_editor')
        assert page['items'][0]['assessment'] is None and page['items'][0]['requirementsState']=='not_extracted'
        requirement = {'id':'sport','label':'Attivita sportive','role':'lead','mandatory':True,
                       'alternatives':[{'allOf':[{'key':'statutory_activities','operator':'all','values':['sport']},
                           {'key':'annual_revenue_eur','operator':'minimum','values':['10000'],'referencePeriod':'2025'}]}],
                       'sourceUrl':'https://example.org/call','quote':'Attivita sportive e almeno 10000 euro di entrate nel 2025.'}
        rules = {'sourceRevision':'a'*64,'inputHash':None,'completeRoles':['lead'],'requirements':[requirement]}
        request('PUT','/association-rules/'+'c'*64,{'expectedDigest':None,'rules':rules},subject='user_smoke_editor',status=403)
        request('PUT','/association-rules/'+'c'*64,{'expectedDigest':None,'rules':rules})
        result = request('GET','/associations/'+first['id']+'/matches',subject='user_smoke_editor')
        assert result['items'][0]['assessment']['state']=='compatible'
        passed('manual_financial_period_and_public_rules_match_without_ai')

        phase = 'revocation'
        first = member(first,'user_smoke_viewer','none')
        request('GET','/associations/'+first['id'],subject='user_smoke_viewer',status=404)
        request('GET','/associations/'+first['id']+'/documents/'+doc['id'],subject='user_smoke_viewer',status=404)
        assert request('GET','/associations/'+first['id']+'/documents/'+doc['id'])['sha256'] == hashlib.sha256(text).hexdigest()
        with psycopg.connect(runtime) as connection:
            for statement in (
                'UPDATE bandi_portal.association_profile_documents SET filename=filename WHERE id=%s',
                'DELETE FROM bandi_portal.association_profile_documents WHERE id=%s',
            ):
                try:
                    with connection.transaction(): connection.execute(statement,(doc['id'],))
                except psycopg.errors.InsufficientPrivilege: pass
                else: raise AssertionError('Original immutability guard failed')
        passed('revocation_preserves_immutable_originals')
        assert not any(name == 'agno' or name.startswith('apps.api.local_app') for name in sys.modules)
        passed('no_local_ai_or_shared_native_runtime')
    print(json.dumps({'status':'succeeded','checks':checks,'migrationChecksums':{str(k):v for k,v in actual.items()},
                      'isolatedFormats':['txt','docx','pdf'],'privateTables':len(tables),'providerCalls':0}),flush=True)
except BaseException as error:
    print(json.dumps({'status':'failed','stage':phase,'error':type(error).__name__,'checks':checks}),flush=True)
    sys.exit(1)
"""


def runner(path: Path) -> None:
    import psycopg
    from psycopg import sql

    data = json.loads(path.read_text())
    save(
        path, runner_pid=os.getpid(), runner_birth=birth(os.getpid()), stage="preflight"
    )
    image = json.loads(docker("image", "inspect", data["image"]))[0]
    docker("image", "inspect", PG_IMAGE)  # Never pull a database image implicitly.
    save(path, image_id=image["Id"])
    password = secrets.token_urlsafe(30)
    # Do not propagate any production token or configured database variable.
    environment = {
        k: v
        for k, v in os.environ.items()
        if k.upper()
        in {
            "PATH",
            "SYSTEMROOT",
            "WINDIR",
            "TEMP",
            "TMP",
            "USERPROFILE",
            "APPDATA",
            "LOCALAPPDATA",
            "PROGRAMDATA",
            "PROGRAMFILES",
            "PROGRAMFILES(X86)",
            "COMSPEC",
            "PATHEXT",
            "DOCKER_HOST",
            "DOCKER_CONTEXT",
            "DOCKER_CONFIG",
            "DOCKER_CERT_PATH",
            "DOCKER_TLS_VERIFY",
        }
    }
    environment["POSTGRES_PASSWORD"] = password
    database_id = docker(
        "run",
        "--detach",
        "--pull=never",
        "--name",
        data["database_name"],
        "--label",
        LABEL + "=" + data["run_id"],
        "--label",
        LABEL + ".kind=database",
        "--memory=512m",
        "--cpus=1",
        "--publish",
        "127.0.0.1::5432",
        "-e",
        "POSTGRES_PASSWORD",
        PG_IMAGE,
        environment=environment,
    )
    save(path, database_id=database_id, stage="database_starting")
    inspected = json.loads(docker("inspect", database_id))[0]
    mapping = inspected["NetworkSettings"]["Ports"]["5432/tcp"][0]
    if mapping["HostIp"] != "127.0.0.1":
        raise RuntimeError("Fixture database must bind literal loopback")
    port = int(mapping["HostPort"])
    deadline = time.monotonic() + 35
    while True:
        try:
            connection = psycopg.connect(
                host="127.0.0.1",
                port=port,
                user="postgres",
                password=password,
                dbname="postgres",
                connect_timeout=2,
                autocommit=True,
            )
            break
        except psycopg.OperationalError:
            if time.monotonic() >= deadline:
                raise RuntimeError("Fixture PostgreSQL did not become ready") from None
            save(path, stage="database_starting")
            time.sleep(0.3)
    with connection:
        for role in ("bandi_portal_owner", "bandi_portal_app"):
            connection.execute(
                sql.SQL("CREATE ROLE {} LOGIN PASSWORD {}").format(
                    sql.Identifier(role), sql.Literal(password)
                )
            )
        for role in ("anon", "authenticated"):
            connection.execute(
                sql.SQL("CREATE ROLE {} NOLOGIN").format(sql.Identifier(role))
            )
        connection.execute(
            "CREATE SCHEMA bandi_portal AUTHORIZATION bandi_portal_owner"
        )
        connection.execute(
            "REVOKE ALL ON SCHEMA bandi_portal FROM PUBLIC,anon,authenticated"
        )
        connection.execute("GRANT USAGE ON SCHEMA bandi_portal TO bandi_portal_app")
        connection.execute(
            "ALTER DEFAULT PRIVILEGES FOR ROLE bandi_portal_owner IN SCHEMA bandi_portal GRANT SELECT,INSERT,UPDATE,DELETE ON TABLES TO bandi_portal_app"
        )
        connection.execute(
            "ALTER DEFAULT PRIVILEGES FOR ROLE bandi_portal_owner IN SCHEMA bandi_portal GRANT USAGE,SELECT ON SEQUENCES TO bandi_portal_app"
        )
    source = PROGRAM.replace("__CONFIG__", repr({"password": password}))
    command = [
        "docker",
        "run",
        "--interactive",
        "--pull=never",
        "--name",
        data["app_name"],
        "--label",
        LABEL + "=" + data["run_id"],
        "--label",
        LABEL + ".kind=app",
        "--network",
        "container:" + database_id,
        "--memory=768m",
        "--cpus=1",
        "--read-only",
        "--tmpfs",
        "/tmp:rw,nosuid,nodev,size=32m",
        "--cap-drop=ALL",
        "--security-opt=no-new-privileges",
        "--entrypoint=python",
        data["image"],
        "-",
    ]
    child = subprocess.Popen(
        command,
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        env=environment,
        creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0,
    )
    save(
        path,
        stage="linux_gateway_smoke",
        child_pid=child.pid,
        child_birth=birth(child.pid),
        database_port=port,
    )
    output_lines, error_lines = [], []

    def capture(stream, lines, filename):
        for raw in iter(stream.readline, b""):
            line = raw.decode("utf-8", errors="replace").replace(
                password, "[synthetic-password]"
            )
            lines.append(line)
            try:
                with (path.parent / filename).open("a", encoding="utf-8") as log:
                    log.write(line)
            except OSError:
                pass

    readers = [
        threading.Thread(
            target=capture,
            args=(child.stdout, output_lines, "smoke.stdout.log"),
            daemon=True,
        ),
        threading.Thread(
            target=capture,
            args=(child.stderr, error_lines, "smoke.stderr.log"),
            daemon=True,
        ),
    ]
    for reader in readers:
        reader.start()
    child.stdin.write(source.encode())
    child.stdin.close()
    deadline = time.monotonic() + 175
    while child.poll() is None:
        save(path, stage="linux_gateway_smoke", output_lines=len(output_lines))
        if time.monotonic() >= deadline:
            child.terminate()
            child.wait(timeout=10)
            raise RuntimeError("Linux image smoke exceeded its deadline")
        time.sleep(1)
    for reader in readers:
        reader.join(timeout=5)
    results = []
    for line in output_lines:
        try:
            value = json.loads(line)
            if isinstance(value, dict) and "status" in value:
                results.append(value)
        except ValueError:
            pass
    final = results[-1] if results else {}
    if child.returncode != 0 or final.get("status") != "succeeded":
        save(path, smoke_result=final, exit_code=child.returncode)
        raise RuntimeError("Linux gateway smoke failed; inspect sanitized evidence")
    save(
        path,
        smoke_result=final,
        exit_code=0,
        status="succeeded",
        stage="verified",
        source_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--image")
    parser.add_argument("--run-directory", type=Path)
    parser.add_argument("--runner", type=Path)
    args = parser.parse_args()
    if args.runner:
        path = args.runner.resolve(strict=True)
        try:
            runner(path)
        except BaseException as error:
            save(
                path, status="failed", stage="runner_failed", error=type(error).__name__
            )
        finally:
            try:
                cleanup(path)
            except BaseException as error:
                save(
                    path,
                    status="failed",
                    stage="cleanup_failed",
                    error=type(error).__name__,
                )
        return 0 if json.loads(path.read_text())["status"] == "succeeded" else 1
    if not args.image or not re.fullmatch(
        r"(?:sha256:[a-f0-9]{64}|[a-z0-9./_-]+@sha256:[a-f0-9]{64})", args.image
    ):
        parser.error(
            "Use one immutable local image ID or registry digest, never a mutable tag"
        )
    if args.run_directory is None:
        parser.error("--run-directory is required")
    directory = args.run_directory.resolve()
    allowed = (ROOT / "deployment/.artifacts").resolve()
    if not directory.is_relative_to(allowed) or directory == allowed:
        parser.error("Smoke output must be a new child of deployment/.artifacts")
    directory.mkdir(parents=True, exist_ok=False)
    run_id = uuid4().hex
    path = directory / "state.json"
    save(
        path,
        status="running",
        stage="watchdog_starting",
        runner_pid=None,
        supervisor_pid=os.getpid(),
        run_id=run_id,
        image=args.image,
        app_name="codex-association-app-" + run_id,
        database_name="codex-association-pg-" + run_id,
    )
    print(
        json.dumps({"state": str(path), "log": str(directory / "smoke.stdout.log")}),
        flush=True,
    )
    child = subprocess.Popen(
        [sys.executable, __file__, "--runner", str(path)],
        creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0,
    )
    identity = birth(child.pid)
    deadline = time.monotonic() + DEADLINE
    while child.poll() is None and time.monotonic() < deadline:
        save(
            directory / "watchdog.json",
            status="running",
            stage="monitoring",
            runner_pid=child.pid,
            runner_birth=identity,
        )
        time.sleep(1)
    if child.poll() is None and birth(child.pid) == identity:
        if os.name == "nt":
            subprocess.run(
                [
                    str(Path(os.environ["SYSTEMROOT"]) / "System32/taskkill.exe"),
                    "/PID",
                    str(child.pid),
                    "/T",
                    "/F",
                ],
                capture_output=True,
                timeout=15,
            )
        else:
            child.kill()
        child.wait(timeout=15)
        save(path, status="failed", stage="watchdog_timeout")
    data = json.loads(path.read_text())
    if data["status"] == "running":
        save(path, status="failed", stage="missing_terminal_state")
    try:
        cleanup(path)
    except BaseException as error:
        save(path, status="failed", stage="cleanup_failed", error=type(error).__name__)
    result = json.loads(path.read_text())
    save(
        directory / "watchdog.json",
        status=result["status"],
        stage="complete",
        runner_pid=child.pid,
        exit_code=child.returncode,
    )
    print(
        json.dumps(
            {
                "status": result["status"],
                "state": str(path),
                "cleanup_complete": result.get("cleanup_complete", False),
            }
        )
    )
    return 0 if result["status"] == "succeeded" else 1


if __name__ == "__main__":
    raise SystemExit(main())
