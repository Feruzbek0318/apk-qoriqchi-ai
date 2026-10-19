import re
from datetime import datetime
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator

SHA256 = r"^[a-fA-F0-9]{64}$"
Language = Literal["uz", "ru", "en"]


class RegisterRequest(BaseModel):
    registration_secret: str | None = Field(default=None, max_length=256)


class RegisterResponse(BaseModel):
    client_id: str
    token: str = Field(description="Bearer token. Shown once; only its hash is stored.")


class ApkScanRequest(BaseModel):
    """Metadata extracted on-device. Treated as untrusted input."""

    model_config = ConfigDict(extra="forbid")
    sha256: str = Field(pattern=SHA256)
    file_name: str = Field(min_length=1, max_length=255)
    size_bytes: int = Field(ge=1, le=650 * 1024 * 1024)
    package_name: str | None = Field(default=None, max_length=255, pattern=r"^[A-Za-z][\w]*(\.[A-Za-z][\w]*)+$")
    version_name: str | None = Field(default=None, max_length=100)
    version_code: int | None = Field(default=None, ge=0)
    min_sdk: int | None = Field(default=None, ge=1, le=100)
    target_sdk: int | None = Field(default=None, ge=1, le=100)
    permissions: list[str] = Field(default_factory=list, max_length=500)
    signer_sha256: str | None = Field(default=None, pattern=SHA256)
    debug_certificate: bool = False
    language: Language = "en"
    ai: bool = True

    @field_validator("permissions")
    @classmethod
    def _perm_format(cls, v: list[str]) -> list[str]:
        bad = [p for p in v if len(p) > 200 or not re.fullmatch(r"[\w.]+", p)]
        if bad:
            raise ValueError("invalid permission names")
        return v

    @field_validator("file_name")
    @classmethod
    def _apk_name(cls, v: str) -> str:
        if not v.lower().endswith(".apk"):
            raise ValueError("file_name must end with .apk")
        return v.replace("/", "_").replace("\\", "_")


class UrlScanRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    url: str = Field(min_length=1, max_length=2048)
    language: Language = "en"
    ai: bool = True
    submit_if_unknown: bool = False


class ScanAccepted(BaseModel):
    scan_id: str
    status: str


class ScanOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: str
    scan_type: str
    status: str
    target: str
    sha256: str | None
    risk_level: str | None
    confidence: str | None
    provider_status: dict
    findings: dict
    ai_analysis: dict | None
    error: str | None
    fixture: bool
    created_at: datetime
    completed_at: datetime | None


class HistoryPage(BaseModel):
    items: list[ScanOut]
    total: int


class ProviderStatusOut(BaseModel):
    name: str
    configured: bool
    fixture: bool
    note: str | None = None
