from functools import lru_cache
from typing import Literal

from pydantic import SecretStr, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    app_env: Literal["development", "production", "test"] = "development"
    database_url: str = "sqlite:///./dev.db"
    registration_secret: SecretStr | None = None

    virustotal_api_key: SecretStr | None = None
    safe_browsing_api_key: SecretStr | None = None
    web_risk_api_key: SecretStr | None = None
    url_reputation_provider: Literal["safebrowsing", "webrisk"] = "safebrowsing"

    anthropic_api_key: SecretStr | None = None
    ai_model: str = "claude-opus-5-5"

    provider_fixture_mode: bool = False

    cors_origins: list[str] = []
    rate_limit_per_minute: int = 30
    max_upload_mb: int = 100
    max_json_body_kb: int = 256
    provider_timeout_s: float = 20.0
    provider_max_retries: int = 2
    vt_poll_interval_s: float = 15.0
    vt_poll_max_attempts: int = 40  # ~10 minutes

    @model_validator(mode="after")
    def _no_fixtures_in_prod(self) -> "Settings":
        if self.app_env == "production" and self.provider_fixture_mode:
            raise ValueError("PROVIDER_FIXTURE_MODE must be false in production")
        return self

    @property
    def max_upload_bytes(self) -> int:
        return self.max_upload_mb * 1024 * 1024


@lru_cache
def get_settings() -> Settings:
    return Settings()
