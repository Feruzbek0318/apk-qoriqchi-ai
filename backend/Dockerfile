FROM python:3.12-slim AS base
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 PIP_NO_CACHE_DIR=1
WORKDIR /srv
RUN useradd --create-home --uid 10001 app
COPY pyproject.toml ./
RUN pip install --upgrade pip && pip install "fastapi>=0.143" "uvicorn[standard]>=0.54" "pydantic>=2.14" \
    "pydantic-settings>=2.15" "httpx>=0.28" "sqlalchemy>=2.0.55" "alembic>=1.20" "psycopg[binary]>=3.3" \
    "anthropic>=1.15" "python-multipart>=0.0.32"
COPY alembic.ini ./
COPY alembic ./alembic
COPY app ./app
USER app
EXPOSE 8000
HEALTHCHECK --interval=30s --timeout=5s CMD python -c "import urllib.request,sys; sys.exit(0 if urllib.request.urlopen('http://127.0.0.1:8000/health').status==200 else 1)"
# Migrations run before the server starts; uvicorn handles SIGTERM gracefully (lifespan cancels in-flight scans).
CMD ["sh", "-c", "alembic upgrade head && exec uvicorn app.main:app --host 0.0.0.0 --port 8000 --proxy-headers --timeout-graceful-shutdown 20"]
