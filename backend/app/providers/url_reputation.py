"""URL reputation adapters: Google Safe Browsing v5 (non-commercial) and Google Web Risk (commercial)."""
from typing import Any, Protocol

import httpx

from app.providers.base import HttpProvider, ProviderOutcome, Status

SB_URL = "https://safebrowsing.googleapis.com/v5/urls:search"
WR_URL = "https://webrisk.googleapis.com/v1/uris:search"
WR_THREAT_TYPES = ("MALWARE", "SOCIAL_ENGINEERING", "UNWANTED_SOFTWARE")


class UrlReputationProvider(Protocol):
    name: str
    configured: bool

    async def lookup(self, url: str) -> ProviderOutcome: ...


def _google_error(name: str, resp: httpx.Response) -> ProviderOutcome:
    if resp.status_code == 429:
        return ProviderOutcome(name, Status.QUOTA, error="Quota exceeded")
    if resp.status_code in (401, 403):
        return ProviderOutcome(name, Status.AUTH_FAILED, error="API key rejected or API not enabled")
    return ProviderOutcome(name, Status.FAILED, error=f"HTTP {resp.status_code}")


class SafeBrowsingProvider(HttpProvider):
    name = "safe_browsing"

    def __init__(self, client: httpx.AsyncClient, api_key: str | None, **kw: Any) -> None:
        super().__init__(client, **kw)
        self.api_key = api_key

    @property
    def configured(self) -> bool:
        return bool(self.api_key)

    async def lookup(self, url: str) -> ProviderOutcome:
        if not self.configured:
            return ProviderOutcome(self.name, Status.NOT_CONFIGURED)
        resp = await self.request("GET", SB_URL, params={"key": self.api_key, "urls": url})
        if isinstance(resp, ProviderOutcome):
            return resp
        if resp.status_code != 200:
            return _google_error(self.name, resp)
        threats = resp.json().get("threats") or []
        types = sorted({t for th in threats for t in th.get("threatTypes", [])})
        return ProviderOutcome(self.name, Status.OK, data={"match": bool(types), "threat_types": types})


class WebRiskProvider(HttpProvider):
    name = "web_risk"

    def __init__(self, client: httpx.AsyncClient, api_key: str | None, **kw: Any) -> None:
        super().__init__(client, **kw)
        self.api_key = api_key

    @property
    def configured(self) -> bool:
        return bool(self.api_key)

    async def lookup(self, url: str) -> ProviderOutcome:
        if not self.configured:
            return ProviderOutcome(self.name, Status.NOT_CONFIGURED)
        params = [("threatTypes", t) for t in WR_THREAT_TYPES] + [("uri", url), ("key", self.api_key or "")]
        resp = await self.request("GET", WR_URL, params=params)
        if isinstance(resp, ProviderOutcome):
            return resp
        if resp.status_code != 200:
            return _google_error(self.name, resp)
        types = sorted((resp.json().get("threat") or {}).get("threatTypes", []))
        return ProviderOutcome(self.name, Status.OK, data={"match": bool(types), "threat_types": types})
