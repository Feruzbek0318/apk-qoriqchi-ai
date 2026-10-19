import os

os.environ.setdefault("DATABASE_URL", "sqlite://")
os.environ.setdefault("APP_ENV", "test")

import httpx
import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

from app.config import Settings
from app.db import Base
from app.main import create_app


class FakeUpstream:
    """httpx MockTransport router. Records every host contacted (used by the no-SSRF test)."""

    def __init__(self) -> None:
        self.routes: dict[tuple[str, str], httpx.Response] = {}
        self.hosts: list[str] = []
        self.calls: list[httpx.Request] = []

    def add(self, method: str, prefix: str, status: int = 200, json: dict | None = None) -> None:
        self.routes[(method, prefix)] = httpx.Response(status, json=json if json is not None else {})

    def __call__(self, request: httpx.Request) -> httpx.Response:
        self.hosts.append(request.url.host)
        self.calls.append(request)
        url = str(request.url)
        for (method, prefix), resp in sorted(self.routes.items(), key=lambda kv: -len(kv[0][1])):
            if request.method == method and url.startswith(prefix):
                return httpx.Response(resp.status_code, content=resp.content, headers=resp.headers)
        return httpx.Response(404, json={"error": {"code": "NotFoundError"}})


@pytest.fixture
def upstream() -> FakeUpstream:
    return FakeUpstream()


@pytest.fixture
def make_client(upstream):
    def _make(analyst=None, **overrides) -> TestClient:
        engine = create_engine("sqlite://", connect_args={"check_same_thread": False}, poolclass=StaticPool)
        Base.metadata.create_all(engine)
        factory = sessionmaker(bind=engine, expire_on_commit=False)
        defaults = dict(app_env="test", database_url="sqlite://", virustotal_api_key="vt-test-key",
                        safe_browsing_api_key="sb-test-key", provider_max_retries=0,
                        vt_poll_interval_s=0.01, vt_poll_max_attempts=3, rate_limit_per_minute=1000)
        settings = Settings(**{**defaults, **overrides})
        app = create_app(settings, transport=httpx.MockTransport(upstream), session_factory=factory, analyst=analyst)
        from app.db import get_db

        def _db():
            with factory() as s:
                yield s

        app.dependency_overrides[get_db] = _db
        return TestClient(app)

    return _make


@pytest.fixture
def client(make_client):
    with make_client() as c:
        yield c


def auth(client: TestClient) -> dict[str, str]:
    token = client.post("/api/v1/auth/register", json={}).json()["token"]
    return {"Authorization": f"Bearer {token}"}
