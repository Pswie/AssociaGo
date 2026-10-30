"""Prepare private, verified owner parameters; never connects to a database."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import uuid

from backup_cloud_schemas import protect_directory

ROOT = Path(__file__).resolve().parent


def verified_identity(identity, public):
    if (
        identity.get("userId") != public["clerk"]["ownerSubject"]
        or identity.get("emailVerified") is not True
    ):
        raise ValueError("The exact verified owner identity is required")
    for key, maximum in (("firstName", 255), ("lastName", 255), ("primaryEmail", 100)):
        value = identity.get(key)
        if (
            not isinstance(value, str)
            or not value.strip()
            or len(value) > maximum
            or any(c in value for c in "\r\n\x00")
        ):
            raise ValueError("Verified owner identity is incomplete")
    if "@" not in identity["primaryEmail"]:
        raise ValueError("Verified owner email is invalid")
    if len(identity["firstName"] + " " + identity["lastName"]) > 100:
        raise ValueError("The verified display name exceeds the application field")
    return {
        "ADF_OWNER_SUBJECT": identity["userId"],
        "ADF_OWNER_FIRST_NAME": identity["firstName"],
        "ADF_OWNER_LAST_NAME": identity["lastName"],
        "ADF_OWNER_EMAIL": identity["primaryEmail"],
        "ADF_OWNER_EMAIL_VERIFIED": "true",
    }


def parameters(identity, public):
    return {
        **verified_identity(identity, public),
        "ADF_LIBREPM_OWNER_ID": str(uuid.uuid4()),
        "ADF_SUITE_ORGANIZATION_ID": str(uuid.uuid4()),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--identity", type=Path, required=True)
    parser.add_argument(
        "--public-settings", type=Path, default=ROOT / "suite-public-settings.json"
    )
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        raise SystemExit(
            "Refusing to replace previously generated bootstrap identities"
        )
    values = parameters(
        json.loads(args.identity.read_text()),
        json.loads(args.public_settings.read_text()),
    )
    protect_directory(args.output)
    (args.output / "owner-bootstrap.env").write_text(
        "\n".join(key + "=" + value for key, value in values.items()) + "\n",
        encoding="utf-8",
    )
    (args.output / "parameters.private.json").write_text(
        json.dumps(values, indent=2) + "\n", encoding="utf-8"
    )
    print(
        "PASS private bootstrap prepared; no database connection or personal data printed"
    )


if __name__ == "__main__":
    main()
