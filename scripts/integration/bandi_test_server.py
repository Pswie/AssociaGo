"""Private smoke-test fixture: native Bandi API with an isolated SQLite database.

Launched only by run_native_smoke.py. Never reads a repository .env file.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys
import uuid


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", type=Path, required=True)
    parser.add_argument("--config", type=Path, required=True)
    parser.add_argument("--port", type=int, required=True)
    args = parser.parse_args()
    config = json.loads(args.config.read_text(encoding="utf-8"))
    sys.path.insert(0, str(args.repo.resolve()))

    import uvicorn
    from sqlalchemy import create_engine, event
    from sqlalchemy.orm import Session
    from sqlalchemy.pool import StaticPool

    from apps.api.applications.preparation_models import ProjectPreparation
    from apps.api.auth import hash_password
    from apps.api.config import Settings
    from apps.api.database import Base, get_session
    from apps.api.main import create_app

    engine = create_engine(
        "sqlite+pysqlite://",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )

    @event.listens_for(engine, "connect")
    def foreign_keys(connection, _):
        connection.execute("PRAGMA foreign_keys=ON")

    Base.metadata.create_all(engine)
    with Session(engine) as session:
        for candidate_id, profile in [(config["preparationId"], "aps"), (config["foreignPreparationId"], "cooperativa")]:
            session.add(ProjectPreparation(
                id=uuid.UUID(candidate_id), target_key="call:" + str(uuid.uuid4()),
                organization_key=profile, version=1, status="draft",
                brief={"project_title": "Suite native smoke", "objective": "Synthetic fixture only",
                       "total_budget": "1234.50", "requested_grant": "1000.00"},
                review={"decision": "pending"},
            ))
        session.commit()
    settings = Settings(
        _env_file=None, app_env="test", log_level="WARNING", agno_enabled=False,
        database_url="postgresql+psycopg://127.0.0.1/unused_by_fixture_override",
        data_root=args.config.parent / "bandi-data", cors_origins="http://127.0.0.1",
        auth_username="suite-smoke", auth_password_hash=hash_password(config["password"]),
        session_secret=config["sessionSecret"], session_cookie_secure=False,
        suite_enabled=True, suite_organization_id=config["organizationId"],
        suite_bandi_profile="aps", suite_bandi_key=config["key"],
        suite_librepm_api_url=config["registryUrl"], suite_timeout_seconds=2,
    )
    application = create_app(settings)

    @application.middleware("http")
    async def capture_synthetic_projection(request, call_next):
        if request.method == "PUT" and request.url.path.startswith("/api/integrations/v1/peer/projects/"):
            body = await request.body()
            # Only the synthetic projection, never request headers or cookies.
            (args.config.parent / "bandi-received-projection.json").write_bytes(body)
        return await call_next(request)

    def isolated_session():
        with Session(engine) as session:
            yield session

    application.dependency_overrides[get_session] = isolated_session
    try:
        uvicorn.run(application, host="127.0.0.1", port=args.port, log_level="warning", access_log=False)
    finally:
        engine.dispose()


if __name__ == "__main__":
    main()
