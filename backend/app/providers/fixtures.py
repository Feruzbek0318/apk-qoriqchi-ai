"""Clearly-labelled FIXTURE providers for local development without API keys.

Every outcome carries fixture=True and the API marks the scan as fixture data. Never enabled in production.
"""
from pathlib import Path

from app.providers.base import ProviderOutcome, Status

# EICAR-like convention: hashes/hosts containing these markers produce a "malicious" fixture.
BAD_MARKER = "bad"


class FixtureVirusTotal:
    name = "virustotal"
    configured = True

    def _stats(self, bad: bool) -> ProviderOutcome:
        stats = {"malicious": 12 if bad else 0, "suspicious": 0, "undetected": 58 if bad else 70, "harmless": 0,
                 "timeout": 0, "type-unsupported": 0, "failure": 0}
        flagged = [{"engine": f"FixtureEngine{i}", "category": "malicious", "result": "Fixture.Trojan"} for i in range(12)] if bad else []
        return ProviderOutcome(self.name, Status.OK, fixture=True, data={
            "stats": stats, "flagged_engines": flagged, "last_analysis_date": None,
            "threat_label": "fixture.trojan" if bad else None, "reputation": None,
            "last_final_url": None, "categories": None})

    async def file_report(self, sha256: str) -> ProviderOutcome:
        if sha256.startswith("0000"):
            return ProviderOutcome(self.name, Status.NOT_FOUND, fixture=True, error="No existing report found.")
        return self._stats(BAD_MARKER in sha256 or sha256.startswith("bad"))

    async def url_report(self, url: str) -> ProviderOutcome:
        return self._stats(BAD_MARKER in url)

    async def upload_file(self, path: Path) -> ProviderOutcome:
        return ProviderOutcome(self.name, Status.OK, fixture=True, data={"analysis_id": "fixture-analysis"})

    async def submit_url(self, url: str) -> ProviderOutcome:
        return ProviderOutcome(self.name, Status.OK, fixture=True, data={"analysis_id": "fixture-analysis"})

    async def analysis(self, analysis_id: str) -> ProviderOutcome:
        out = self._stats(False)
        out.data["status"] = "completed"
        return out


class FixtureUrlReputation:
    name = "safe_browsing"
    configured = True

    async def lookup(self, url: str) -> ProviderOutcome:
        bad = BAD_MARKER in url
        return ProviderOutcome(self.name, Status.OK, fixture=True,
                               data={"match": bad, "threat_types": ["SOCIAL_ENGINEERING"] if bad else []})
