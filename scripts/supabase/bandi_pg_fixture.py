"""Disposable smoke fixture using real PostgreSQL sessions and real local operator auth."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys
import uuid


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, required=True)
    parser.add_argument("--config", type=Path, required=True)
    parser.add_argument("--port", type=int, required=True)
    args = parser.parse_args()
    config = json.loads(args.config.read_text(encoding="utf-8"))
    sys.path.insert(0, str(args.repo.resolve()))

    import uvicorn
    from alembic import command
    from alembic.config import Config
    from sqlalchemy.orm import Session
    from apps.api.applications.preparation_models import ProjectPreparation
    from apps.api.auth import hash_password
    from apps.api.config import Settings
    from apps.api.database import create_database_engine
    from apps.api.main import create_app

    settings = Settings(
        _env_file=None,
        app_env="test",
        log_level="WARNING",
        agno_enabled=False,
        database_url=config["databaseUrl"],
        database_schema="bandi",
        database_extension_schema="extensions",
        database_migration_url=config["migrationUrl"],
        database_pool_size=2,
        database_max_overflow=1,
        data_root=args.config.parent / "bandi-data",
        cors_origins="http://127.0.0.1",
        auth_username="suite-smoke",
        auth_password_hash=hash_password(config["password"]),
        session_secret=config["sessionSecret"],
        session_cookie_secure=False,
        suite_enabled=True,
        suite_organization_id=config["organizationId"],
        suite_bandi_profile="aps",
        suite_bandi_key=config["key"],
        suite_librepm_api_url=config["registryUrl"],
        suite_timeout_seconds=3,
    )
    migration = Config(str(args.repo.resolve() / "apps/api/alembic.ini"))
    migration.attributes["settings"] = settings
    command.upgrade(migration, "head")
    engine = create_database_engine(settings)
    try:
        with Session(engine) as session:
            session.add(
                ProjectPreparation(
                    id=uuid.UUID(config["preparationId"]),
                    target_key="call:" + str(uuid.uuid4()),
                    organization_key="aps",
                    version=1,
                    status="draft",
                    brief={
                        "project_title": "PostgreSQL suite smoke",
                        "objective": "Synthetic local fixture",
                        "total_budget": "1234.50",
                        "requested_grant": "1000.00",
                    },
                    binding={},
                    review={"decision": "pending"},
                    delivery={},
                )
            )
            session.commit()
        # The web service receives only the runtime role after the migration and fixture stage.
        settings.database_migration_url = None
        application = create_app(settings)
        uvicorn.run(
            application,
            host="127.0.0.1",
            port=args.port,
            log_level="warning",
            access_log=False,
        )
    finally:
        engine.dispose()


if __name__ == "__main__":
    main()
