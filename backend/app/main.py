import logging
import uuid
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

import httpx
from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from sqlalchemy import text

from app import db as db_module
from app.ai.analyst import Analyst
from app.api.routes import router
from app.config import Settings, get_settings
from app.logging_setup import configure_logging, request_id_var
from app.providers.registry import build_providers
from app.scans.service import ScanService
from app.security import RateLimiter

log = logging.getLogger("app")


def create_app(settings: Settings | None = None, transport: httpx.AsyncBaseTransport | None = None,
               analyst: Analyst | None = None, session_factory=None) -> FastAPI:
    settings = settings or get_settings()
    session_factory = session_factory or db_module.SessionLocal
    configure_logging()

    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        app.state.providers = build_providers(settings, transport, analyst)
        app.state.scans = ScanService(app.state.providers, settings, session_factory)
        log.info("startup", extra={"fields": {"env": settings.app_env, "fixture_mode": settings.provider_fixture_mode}})
        yield
        await app.state.scans.shutdown()  # graceful shutdown: cancel in-flight jobs
        await app.state.providers.aclose()

    app = FastAPI(title="APK Qo'riqchi AI API", version="1.0.0", lifespan=lifespan,
                  docs_url="/docs", openapi_url="/openapi.json")
    app.state.settings = settings
    app.state.rate_limiter = RateLimiter(settings.rate_limit_per_minute)
    app.state.session_factory = session_factory
    if settings.cors_origins:
        app.add_middleware(CORSMiddleware, allow_origins=settings.cors_origins, allow_methods=["GET", "POST", "DELETE"],
                           allow_headers=["Authorization", "Content-Type"], allow_credentials=False)

    @app.middleware("http")
    async def correlation_and_limits(request: Request, call_next):
        rid = request.headers.get("x-request-id") or str(uuid.uuid4())
        rid = rid[:64]
        token = request_id_var.set(rid)
        try:
            length = int(request.headers.get("content-length") or 0)
            is_upload = request.url.path.endswith("/scans/apk/upload")
            limit = settings.max_upload_bytes + 1024 * 1024 if is_upload else settings.max_json_body_kb * 1024
            if length > limit:
                return JSONResponse({"detail": "Request body too large"}, status_code=413, headers={"x-request-id": rid})
            response = await call_next(request)
            response.headers["x-request-id"] = rid
            response.headers["x-content-type-options"] = "nosniff"
            response.headers["cache-control"] = "no-store"
            log.info("request", extra={"fields": {"method": request.method, "path": request.url.path, "status": response.status_code}})
            return response
        finally:
            request_id_var.reset(token)

    @app.get("/health", tags=["ops"])
    def health() -> dict:
        return {"status": "ok"}

    @app.get("/ready", tags=["ops"])
    def ready() -> JSONResponse:
        try:
            with session_factory() as s:
                s.execute(text("SELECT 1"))
        except Exception:
            return JSONResponse({"status": "not_ready", "database": "unavailable"}, status_code=503)
        return JSONResponse({"status": "ready", "database": "ok", "fixture_mode": settings.provider_fixture_mode})

    app.include_router(router)
    return app


app = create_app()
