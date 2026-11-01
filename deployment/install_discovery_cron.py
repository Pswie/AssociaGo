"""Prepare or install the bounded daily discovery trigger on cron-job.org.

Default mode only exports a private job definition. Credentials are never
printed; the Worker independently enforces the 15-minute/24-hour limits.
"""

from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path

import install_portal_cron as cron

ORIGIN = "https://angeli-del-futuro-bandi-quotidiani.render-keepalive.workers.dev"
TITLE = "Angeli del Futuro - ricerca quotidiana bandi"


def specification(cron_key: str) -> dict:
    payload = cron.specification(ORIGIN, cron_key, TITLE)
    job = payload["job"]
    job["url"] = ORIGIN + "/v1/check"
    job["requestMethod"] = 1
    job["requestTimeout"] = 30
    # The Worker keeps its 15-minute gate; polling every 5 minutes prevents
    # a few seconds of scheduler jitter from delaying a source by a full slot.
    job["schedule"]["minutes"] = list(range(0, 60, 5))
    job["extendedData"]["headers"]["User-Agent"] = "AngeliDelFuturo-Discovery/1.0"
    return payload


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--worker-secrets-file", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    output = args.output.resolve()
    if any((ancestor / ".git").exists() for ancestor in [output, *output.parents]):
        raise SystemExit("Private cron output must be outside repositories")
    output.mkdir(mode=0o700, parents=True, exist_ok=False)
    try:
        keys = json.loads(args.worker_secrets_file.read_text(encoding="utf-8"))
        payload = specification(keys["DISCOVERY_CRON_KEY"])
        (output / "job.private.json").write_text(
            json.dumps(payload, indent=2), encoding="utf-8"
        )
        result = None
        if args.apply:
            api_key = os.environ.get("CRONJOB_API_KEY") or cron.hidden_secret(
                "API key cron-job.org: "
            )
            result = cron.apply(cron.Client(api_key), payload)
        receipt = {
            "status": "installed" if result else "prepared",
            "provider": "cron-job.org",
            "checkedAt": datetime.now(timezone.utc).isoformat(),
            "jobId": result["jobId"] if result else None,
            "configurationVerified": result is not None,
            "executionVerified": False,
            "scheduleMinutes": list(range(0, 60, 5)),
            "workerOrigin": ORIGIN,
            "dataDeleted": False,
            "apiKeyPersisted": False,
        }
        (output / "receipt.json").write_text(
            json.dumps(receipt, indent=2), encoding="utf-8"
        )
        print(json.dumps(receipt))
    except Exception:
        raise SystemExit(
            "Cron installation stopped; inspect retained state before retrying."
        ) from None


if __name__ == "__main__":
    main()
