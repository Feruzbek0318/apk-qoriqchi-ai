from dataclasses import dataclass
from typing import Any

import httpx

from app.ai.analyst import Analyst
from app.config import Settings
from app.providers.fixtures import FixtureUrlReputation, FixtureVirusTotal
from app.providers.url_reputation import SafeBrowsingProvider, WebRiskProvider
from app.providers.virustotal import VirusTotalProvider


def _secret(v: Any) -> str | None:
    return v.get_secret_value() if v else None


@dataclass
class Providers:
    vt: Any
    url_reputation: Any
    analyst: Analyst
    http: httpx.AsyncClient | None = None
    fixture: bool = False

    async def aclose(self) -> None:
        if self.http:
            await self.http.aclose()


def build_providers(settings: Settings, transport: httpx.AsyncBaseTransport | None = None, analyst: Analyst | None = None) -> Providers:
    analyst = analyst or Analyst(_secret(settings.anthropic_api_key), settings.ai_model)
    if settings.provider_fixture_mode:
        return Providers(FixtureVirusTotal(), FixtureUrlReputation(), analyst, fixture=True)
    http = httpx.AsyncClient(timeout=settings.provider_timeout_s, transport=transport, follow_redirects=False,
                             headers={"user-agent": "apk-qoriqchi-backend/1.0"})
    kw = {"max_retries": settings.provider_max_retries}
    vt = VirusTotalProvider(http, _secret(settings.virustotal_api_key), **kw)
    if settings.url_reputation_provider == "webrisk":
        rep: Any = WebRiskProvider(http, _secret(settings.web_risk_api_key), **kw)
    else:
        rep = SafeBrowsingProvider(http, _secret(settings.safe_browsing_api_key), **kw)
    return Providers(vt, rep, analyst, http=http)
