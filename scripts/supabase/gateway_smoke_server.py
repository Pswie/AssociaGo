"""Synthetic loopback gateway child for run_gateway_smoke.py; never a deployment entrypoint."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", type=Path, required=True)
    parser.add_argument("--bandi-repo", type=Path, required=True)
    args = parser.parse_args()
    values = json.loads(args.config.read_text(encoding="utf-8"))
    if values["listenHost"] != "127.0.0.1":
        raise ValueError("Only loopback smoke listeners are allowed")
    sys.path.insert(0, str(args.bandi_repo.resolve()))
    import uvicorn
    from cryptography.hazmat.primitives.serialization import load_pem_public_key
    from sqlalchemy import make_url
    from apps.api.collaboration.gateway import create_gateway_app
    from apps.api.collaboration.security import TokenVerifier
    from apps.api.config import Settings

    if make_url(values["settings"]["collab_database_url"]).host != "127.0.0.1":
        raise ValueError("Only the synthetic loopback database is allowed")
    settings = Settings(_env_file=None, **values["settings"])
    public_key = load_pem_public_key(values["publicKey"].encode())
    app = create_gateway_app(settings, verifier=TokenVerifier(settings, key=public_key))
    uvicorn.run(
        app,
        host="127.0.0.1",
        port=values["listenPort"],
        log_level="warning",
        access_log=False,
    )


if __name__ == "__main__":
    main()
