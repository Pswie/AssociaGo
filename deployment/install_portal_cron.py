"""Export or explicitly install one narrowly scoped cron-job.org maintenance job.

Default mode writes a private JSON export and makes no network request.
API and cron secrets are read from private files, environment or getpass.
"""

from __future__ import annotations

import argparse
from datetime import datetime, timezone
import getpass
import ipaddress
import json
import os
from pathlib import Path
import re
import ssl
import sys
import tempfile
import time
import warnings
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import (
    HTTPSHandler,
    HTTPRedirectHandler,
    ProxyHandler,
    Request,
    build_opener,
)

API_ORIGIN = "https://api.cron-job.org"
MAINTENANCE = "/api/collaboration/v1/maintenance/heartbeat"
DEFAULT_TITLE = "Bandi portal maintenance"


class CronError(Exception):
    pass


def hidden_secret(prompt: str) -> str:
    # getpass may fall back to echoed input when no real terminal is available.
    # In that case stop before reading a secret into an ordinary log/pipeline.
    try:
        with warnings.catch_warnings():
            warnings.simplefilter("error", getpass.GetPassWarning)
            return getpass.getpass(prompt)
    except getpass.GetPassWarning:
        raise CronError(
            "Apri una console locale interattiva: il prompt nascosto non è disponibile."
        ) from None


def gateway_origin(value: str) -> str:
    try:
        parsed = urlsplit(value)
        if (
            parsed.scheme != "https"
            or not parsed.hostname
            or parsed.username is not None
            or parsed.password is not None
            or parsed.query
            or parsed.fragment
            or parsed.path not in {"", "/"}
            or any(ord(char) < 33 for char in value)
            or parsed.port is not None
            and not 1 <= parsed.port <= 65535
        ):
            raise ValueError
        hostname = parsed.hostname.lower().rstrip(".")
        if hostname == "localhost" or hostname.endswith((".localhost", ".local")):
            raise ValueError
        try:
            if not ipaddress.ip_address(hostname).is_global:
                raise ValueError
        except ValueError as error:
            # A DNS name is accepted; an invalid/non-public literal is not.
            if hostname.replace(".", "").isdigit() or ":" in hostname:
                raise error
        return value.rstrip("/")
    except ValueError:
        raise CronError(
            "Serve l'origine HTTPS pubblica del gateway, senza percorsi o segreti."
        ) from None


def secret(value: str, *, minimum: int) -> str:
    if (
        not isinstance(value, str)
        or len(value) < minimum
        or any(ord(char) < 33 or ord(char) > 126 for char in value)
    ):
        raise CronError("La chiave fornita non soddisfa i requisiti minimi.")
    return value


def specification(origin: str, cron_key: str, title: str = DEFAULT_TITLE) -> dict:
    if not title.strip() or len(title) > 120 or any(ord(char) < 32 for char in title):
        raise CronError("Titolo del controllo non valido.")
    return {
        "job": {
            "title": title.strip(),
            "url": gateway_origin(origin) + MAINTENANCE,
            "enabled": True,
            "saveResponses": False,
            "requestMethod": 0,
            "requestTimeout": 30,
            "redirectSuccess": False,
            "type": 0,
            "auth": {"enable": False, "user": "", "password": ""},
            "extendedData": {
                "headers": {"X-Cron-Key": secret(cron_key, minimum=32)},
                "body": "",
            },
            "schedule": {
                "timezone": "Europe/Rome",
                "expiresAt": 0,
                "hours": [-1],
                "mdays": [-1],
                "minutes": [0, 30],
                "months": [-1],
                "wdays": [-1],
            },
        }
    }


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, *_args, **_kwargs):
        return None


class Client:
    def __init__(self, api_key: str):
        self.api_key = secret(api_key, minimum=16)
        self.opener = build_opener(
            ProxyHandler({}),
            NoRedirect(),
            HTTPSHandler(context=ssl.create_default_context()),
        )

    def request(self, method: str, path: str, payload=None):
        if not (
            (method == "GET" and re.fullmatch(r"/jobs(?:/[1-9][0-9]*)?", path))
            or method == "PUT"
            and path == "/jobs"
            or method == "PATCH"
            and re.fullmatch(r"/jobs/[1-9][0-9]*", path)
        ):
            raise CronError("Operazione cron non consentita.")
        body = json.dumps(payload).encode() if payload is not None else None
        request = Request(
            API_ORIGIN + path,
            data=body,
            method=method,
            headers={
                "Authorization": "Bearer " + self.api_key,
                "Content-Type": "application/json",
                "Accept": "application/json",
            },
        )
        try:
            started = time.monotonic()
            with self.opener.open(request, timeout=8) as response:
                if not 200 <= response.status < 300:
                    raise CronError(
                        "Risposta API cron non valida; nessun retry automatico."
                    )
                result = bytearray()
                while True:
                    if time.monotonic() - started > 12:
                        raise CronError(
                            "Tempo massimo della risposta API cron superato."
                        )
                    block = response.read1(65536)
                    if not block:
                        break
                    result.extend(block)
                    if len(result) > 2097152:
                        raise CronError(
                            "Risposta API troppo grande: elenco non utilizzabile."
                        )
                value = json.loads(result)
                if not isinstance(value, dict):
                    raise CronError("Formato API cron non riconosciuto.")
                return value
        except (HTTPError, URLError, TimeoutError, OSError, ValueError):
            # Never print request headers, response bodies or a secret-bearing exception.
            raise CronError(
                "API cron non disponibile o risposta rifiutata; nessun redirect o retry automatico."
            ) from None


def complete_listing(value: dict) -> list[dict]:
    if (
        not isinstance(value, dict)
        or value.get("someFailed") is not False
        or not isinstance(value.get("jobs"), list)
    ):
        raise CronError("Elenco cron incompleto: operazione interrotta.")
    jobs = value["jobs"]
    if any(value.get(key) for key in ("hasMore", "nextPage", "next")):
        raise CronError("Elenco cron paginato o incompleto: operazione interrotta.")
    if any(
        key in value and value[key] != len(jobs)
        for key in ("total", "totalCount", "totalJobs")
    ):
        raise CronError("Il conteggio dell'elenco cron non corrisponde ai risultati.")
    identifiers = set()
    for job in jobs:
        if (
            not isinstance(job, dict)
            or type(job.get("jobId")) is not int
            or job["jobId"] <= 0
            or not isinstance(job.get("title"), str)
            or not isinstance(job.get("url"), str)
            or job["jobId"] in identifiers
        ):
            raise CronError("Elenco cron ambiguo o non valido.")
        identifiers.add(job["jobId"])
    return jobs


def matching_job(jobs: list[dict], wanted: dict):
    candidates = [
        job
        for job in jobs
        if job["title"] == wanted["title"] or job["url"] == wanted["url"]
    ]
    if not candidates:
        return None
    if (
        len(candidates) != 1
        or candidates[0]["title"] != wanted["title"]
        or candidates[0]["url"] != wanted["url"]
    ):
        raise CronError(
            "Titolo o URL già presenti in modo ambiguo: nessun job modificato."
        )
    return candidates[0]


def comparable(value):
    if isinstance(value, dict):
        return {key: comparable(item) for key, item in value.items()}
    return value


def changes(current: dict, wanted: dict) -> dict:
    result = {}
    for key, value in wanted.items():
        existing = current.get(key)
        if (
            key == "auth"
            and isinstance(existing, dict)
            and existing.get("enable") is False
        ):
            # Providers may mask unused basic-auth passwords in GET responses.
            continue
        if key == "schedule" and isinstance(existing, dict):
            existing = {
                name: sorted(item) if isinstance(item, list) else item
                for name, item in existing.items()
            }
        if key == "extendedData" and isinstance(existing, dict):
            existing = dict(existing)
            headers = existing.get("headers")
            if isinstance(headers, dict):
                if len({name.lower() for name in headers}) != len(headers):
                    raise CronError(
                        "Header cron duplicati: verifica manuale necessaria."
                    )
                existing["headers"] = {
                    name.lower(): item for name, item in headers.items()
                }
                value = dict(
                    value,
                    headers={
                        name.lower(): item for name, item in value["headers"].items()
                    },
                )
        if json.dumps(comparable(existing), sort_keys=True) != json.dumps(
            comparable(value), sort_keys=True
        ):
            result[key] = wanted[key]
    return result


def apply(client, exported: dict) -> dict:
    wanted = exported["job"]
    existing = matching_job(complete_listing(client.request("GET", "/jobs")), wanted)
    if existing is None:
        response = client.request("PUT", "/jobs", exported)
        identity = response.get("jobId")
        if type(identity) is not int or identity <= 0:
            raise CronError(
                "Creazione senza identificativo verificabile; non riprovare automaticamente."
            )
        action = "created"
    else:
        identity = existing["jobId"]
        details = client.request("GET", f"/jobs/{identity}").get("jobDetails")
        if (
            not isinstance(details, dict)
            or details.get("jobId") != identity
            or details.get("title") != wanted["title"]
            or details.get("url") != wanted["url"]
        ):
            raise CronError(
                "Il job è cambiato durante la verifica: nessuna modifica eseguita."
            )
        delta = changes(details, wanted)
        if not delta:
            return {"action": "unchanged", "jobId": identity}
        client.request("PATCH", f"/jobs/{identity}", {"job": delta})
        action = "updated"
    verified = client.request("GET", f"/jobs/{identity}").get("jobDetails")
    if (
        not isinstance(verified, dict)
        or verified.get("jobId") != identity
        or changes(verified, wanted)
    ):
        raise CronError(
            "La configurazione scritta non è stata verificata: controllare il job indicato nell'account."
        )
    # Concurrent installers must not silently leave duplicate maintenance jobs.
    match = matching_job(complete_listing(client.request("GET", "/jobs")), wanted)
    if match is None or match["jobId"] != identity:
        raise CronError("La verifica conclusiva del job cron è ambigua.")
    return {"action": action, "jobId": identity}


def private_export(path: Path, payload: dict):
    path = path.resolve()
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary = tempfile.mkstemp(
        prefix="cron-export-", suffix=".tmp", dir=path.parent
    )
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as out:
            json.dump(payload, out, indent=2)
            out.write("\n")
        os.chmod(temporary, 0o600)
        os.replace(temporary, path)
    finally:
        Path(temporary).unlink(missing_ok=True)


def install_receipt(path: Path, result: dict | None):
    # Deliberately whitelist public metadata; never serialize a job or HTTP error.
    value = {
        "schema": "cron-job.org-install/v1",
        "provider": "cron-job.org",
        "checkedAt": datetime.now(timezone.utc).isoformat(),
        "configurationVerified": result is not None,
        "executionVerified": False,
        "action": result["action"] if result is not None else "interrupted",
        "jobId": result["jobId"] if result is not None else None,
    }
    private_export(path, value)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--gateway-url", required=True)
    parser.add_argument("--title", default=DEFAULT_TITLE)
    parser.add_argument("--cron-key-file", type=Path)
    parser.add_argument(
        "--prompt-api-key",
        action="store_true",
        help="Ask locally with hidden input instead of reading CRONJOB_API_KEY",
    )
    parser.add_argument("--receipt", type=Path, help="Write a sanitized installation receipt")
    parser.add_argument(
        "--export",
        type=Path,
        default=Path(__file__).parent / ".private" / "portal-maintenance.json",
    )
    parser.add_argument(
        "--apply",
        action="store_true",
        help="Explicitly create/update only the matching job",
    )
    args = parser.parse_args(argv)
    if (args.prompt_api_key or args.receipt) and not args.apply:
        parser.error("--prompt-api-key e --receipt richiedono --apply")
    code = 1
    try:
        # Validate a destination before asking for any secrets.
        origin = gateway_origin(args.gateway_url)
        key = (
            args.cron_key_file.read_text(encoding="utf-8").strip()
            if args.cron_key_file
            else os.environ.get("COLLAB_CRON_KEY")
        )
        if not key:
            key = hidden_secret("Chiave cron del gateway (non verrà mostrata): ")
        payload = specification(origin, key, args.title)
        private_export(args.export, payload)
        print("Esportazione privata pronta: " + str(args.export.resolve()))
        if args.apply:
            api_key = None if args.prompt_api_key else os.environ.get("CRONJOB_API_KEY")
            if not api_key:
                api_key = hidden_secret("API key cron-job.org (non verrà mostrata): ")
            result = apply(Client(api_key), payload)
            if args.receipt:
                install_receipt(args.receipt, result)
            print(f"Job cron {result['action']}, ID {result['jobId']}.")
            if args.receipt:
                print("Ricevuta verificata: " + str(args.receipt.resolve()))
        else:
            print(
                "Nessuna chiamata di rete. Usare --apply per installare esplicitamente."
            )
        return 0
    except CronError as error:
        print(str(error), file=sys.stderr)
    except KeyboardInterrupt:
        print("Operazione cron annullata nella console locale.", file=sys.stderr)
        code = 130
    except Exception:
        print(
            "Operazione cron interrotta; verificare configurazione e file privati.",
            file=sys.stderr,
        )
    if args.apply and args.receipt:
        try:
            install_receipt(args.receipt, None)
        except Exception:
            print("Ricevuta non scritta; nessuna verifica positiva registrata.", file=sys.stderr)
    return code


if __name__ == "__main__":
    raise SystemExit(main())
