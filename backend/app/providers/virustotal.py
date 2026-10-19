"""VirusTotal API v3 adapter (https://docs.virustotal.com/reference/overview)."""
import base64
from pathlib import Path
from typing import Any

import httpx

from app.providers.base import HttpProvider, ProviderOutcome, Status

BASE = "https://www.virustotal.com/api/v3"
DIRECT_UPLOAD_LIMIT = 32 * 1024 * 1024
MAX_UPLOAD = 650 * 1024 * 1024


def url_id(url: str) -> str:
    """VT URL identifier: unpadded URL-safe base64 of the URL."""
    return base64.urlsafe_b64encode(url.encode()).decode().rstrip("=")


def parse_analysis_attributes(attrs: dict[str, Any]) -> dict[str, Any]:
    stats = attrs.get("last_analysis_stats") or attrs.get("stats") or {}
    results = attrs.get("last_analysis_results") or attrs.get("results") or {}
    flagged = [
        {"engine": r.get("engine_name") or name, "category": r.get("category"), "result": r.get("result")}
        for name, r in results.items()
        if isinstance(r, dict) and r.get("category") in ("malicious", "suspicious")
    ]
    ptc = attrs.get("popular_threat_classification") or {}
    return {
        "stats": {k: int(stats.get(k, 0)) for k in ("malicious", "suspicious", "undetected", "harmless", "timeout", "type-unsupported", "failure")},
        "flagged_engines": sorted(flagged, key=lambda f: (f["category"] != "malicious", f["engine"]))[:40],
        "last_analysis_date": attrs.get("last_analysis_date"),
        "threat_label": ptc.get("suggested_threat_label"),
        "reputation": attrs.get("reputation"),
        "last_final_url": attrs.get("last_final_url"),
        "categories": attrs.get("categories"),
    }


class VirusTotalProvider(HttpProvider):
    name = "virustotal"

    def __init__(self, client: httpx.AsyncClient, api_key: str | None, **kw: Any) -> None:
        super().__init__(client, **kw)
        self.api_key = api_key

    @property
    def configured(self) -> bool:
        return bool(self.api_key)

    def _headers(self) -> dict[str, str]:
        return {"x-apikey": self.api_key or "", "accept": "application/json"}

    def _error(self, resp: httpx.Response) -> ProviderOutcome:
        try:
            code = resp.json().get("error", {}).get("code", "")
        except ValueError:
            code = ""
        if resp.status_code == 404 or code == "NotFoundError":
            return ProviderOutcome(self.name, Status.NOT_FOUND, error="No existing report found.")
        if resp.status_code == 429 or code == "QuotaExceededError":
            return ProviderOutcome(self.name, Status.QUOTA, error="VirusTotal quota exceeded; try again later")
        if resp.status_code in (401, 403):
            return ProviderOutcome(self.name, Status.AUTH_FAILED, error=f"VirusTotal rejected the API key ({code or resp.status_code})")
        return ProviderOutcome(self.name, Status.FAILED, error=f"VirusTotal error {resp.status_code} {code}".strip())

    async def _get_object(self, path: str) -> ProviderOutcome:
        if not self.configured:
            return ProviderOutcome(self.name, Status.NOT_CONFIGURED)
        resp = await self.request("GET", f"{BASE}{path}", headers=self._headers())
        if isinstance(resp, ProviderOutcome):
            return resp
        if resp.status_code != 200:
            return self._error(resp)
        attrs = (resp.json().get("data") or {}).get("attributes") or {}
        return ProviderOutcome(self.name, Status.OK, data=parse_analysis_attributes(attrs))

    async def file_report(self, sha256: str) -> ProviderOutcome:
        return await self._get_object(f"/files/{sha256}")

    async def url_report(self, url: str) -> ProviderOutcome:
        return await self._get_object(f"/urls/{url_id(url)}")

    async def upload_file(self, path: Path) -> ProviderOutcome:
        """Returns data={"analysis_id": ...}. Only called after explicit user consent."""
        if not self.configured:
            return ProviderOutcome(self.name, Status.NOT_CONFIGURED)
        size = path.stat().st_size
        if size > MAX_UPLOAD:
            return ProviderOutcome(self.name, Status.FAILED, error="File exceeds VirusTotal's 650 MB upload limit")
        target = f"{BASE}/files"
        if size > DIRECT_UPLOAD_LIMIT:
            r = await self.request("GET", f"{BASE}/files/upload_url", headers=self._headers())
            if isinstance(r, ProviderOutcome):
                return r
            if r.status_code != 200:
                return self._error(r)
            target = r.json()["data"]
        with path.open("rb") as fh:
            resp = await self.request("POST", target, headers=self._headers(), files={"file": (path.name, fh)})
        if isinstance(resp, ProviderOutcome):
            return resp
        if resp.status_code != 200:
            return self._error(resp)
        return ProviderOutcome(self.name, Status.OK, data={"analysis_id": resp.json()["data"]["id"]})

    async def submit_url(self, url: str) -> ProviderOutcome:
        if not self.configured:
            return ProviderOutcome(self.name, Status.NOT_CONFIGURED)
        resp = await self.request("POST", f"{BASE}/urls", headers=self._headers(), data={"url": url})
        if isinstance(resp, ProviderOutcome):
            return resp
        if resp.status_code != 200:
            return self._error(resp)
        return ProviderOutcome(self.name, Status.OK, data={"analysis_id": resp.json()["data"]["id"]})

    async def analysis(self, analysis_id: str) -> ProviderOutcome:
        """data = {"status": queued|in-progress|completed, ...parsed stats}"""
        if not self.configured:
            return ProviderOutcome(self.name, Status.NOT_CONFIGURED)
        resp = await self.request("GET", f"{BASE}/analyses/{analysis_id}", headers=self._headers())
        if isinstance(resp, ProviderOutcome):
            return resp
        if resp.status_code != 200:
            return self._error(resp)
        attrs = (resp.json().get("data") or {}).get("attributes") or {}
        data = parse_analysis_attributes(attrs)
        data["status"] = attrs.get("status")
        return ProviderOutcome(self.name, Status.OK, data=data)
