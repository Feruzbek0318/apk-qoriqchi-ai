import httpx
import pytest

from app.providers.base import Status
from app.providers.url_reputation import SafeBrowsingProvider, WebRiskProvider
from app.providers.virustotal import VirusTotalProvider, url_id

SHA = "a" * 64
VT_FILE = {"data": {"attributes": {
    "last_analysis_stats": {"malicious": 3, "suspicious": 1, "undetected": 60, "harmless": 0},
    "last_analysis_results": {
        "EngA": {"category": "malicious", "engine_name": "EngA", "result": "Trojan.X"},
        "EngB": {"category": "undetected", "engine_name": "EngB", "result": None}},
    "last_analysis_date": 1700000000,
    "popular_threat_classification": {"suggested_threat_label": "trojan.banker"}}}}


def vt_with(handler, key="k"):
    return VirusTotalProvider(httpx.AsyncClient(transport=httpx.MockTransport(handler)), key, max_retries=1, backoff_s=0)


async def test_vt_file_report_parsed():
    seen = {}

    def h(req):
        seen["key"] = req.headers.get("x-apikey")
        assert req.url.path == f"/api/v3/files/{SHA}"
        return httpx.Response(200, json=VT_FILE)

    out = await vt_with(h).file_report(SHA)
    assert out.status == Status.OK and seen["key"] == "k"
    assert out.data["stats"]["malicious"] == 3
    assert out.data["threat_label"] == "trojan.banker"
    assert [e["engine"] for e in out.data["flagged_engines"]] == ["EngA"]


@pytest.mark.parametrize("code,err,status", [
    (404, "NotFoundError", Status.NOT_FOUND),
    (429, "QuotaExceededError", Status.QUOTA),
    (401, "WrongCredentialsError", Status.AUTH_FAILED),
    (400, "BadRequestError", Status.FAILED),
])
async def test_vt_errors(code, err, status):
    out = await vt_with(lambda r: httpx.Response(code, json={"error": {"code": err}})).file_report(SHA)
    assert out.status == status
    if status == Status.NOT_FOUND:
        assert out.error == "No existing report found."


async def test_vt_not_configured_makes_no_request():
    def h(_):
        raise AssertionError("must not call network")

    assert (await vt_with(h, key=None).file_report(SHA)).status == Status.NOT_CONFIGURED


async def test_vt_retries_5xx_then_fails():
    calls = []

    def h(r):
        calls.append(1)
        return httpx.Response(503)

    out = await vt_with(h).file_report(SHA)
    assert out.status == Status.FAILED and len(calls) == 2


async def test_vt_timeout_is_failed():
    def h(r):
        raise httpx.ReadTimeout("slow", request=r)

    out = await vt_with(h).file_report(SHA)
    assert out.status == Status.FAILED and out.error == "timeout"


def test_vt_url_id_unpadded():
    assert url_id("http://www.example.com/") == "aHR0cDovL3d3dy5leGFtcGxlLmNvbS8"


async def test_safe_browsing_match_and_clean():
    def h(r):
        assert r.url.path == "/v5/urls:search" and r.url.params["urls"]
        if "evil" in r.url.params["urls"]:
            return httpx.Response(200, json={"threats": [{"url": "x", "threatTypes": ["SOCIAL_ENGINEERING"]}], "cacheDuration": "300s"})
        return httpx.Response(200, json={"cacheDuration": "300s"})

    sb = SafeBrowsingProvider(httpx.AsyncClient(transport=httpx.MockTransport(h)), "k", max_retries=0)
    hit = await sb.lookup("https://evil.example/")
    clean = await sb.lookup("https://good.example/")
    assert hit.data == {"match": True, "threat_types": ["SOCIAL_ENGINEERING"]}
    assert clean.status == Status.OK and clean.data["match"] is False


async def test_safe_browsing_quota():
    sb = SafeBrowsingProvider(httpx.AsyncClient(transport=httpx.MockTransport(lambda r: httpx.Response(429))), "k", max_retries=0)
    assert (await sb.lookup("https://a.b/")).status == Status.QUOTA


async def test_web_risk_parsing():
    def h(r):
        assert r.url.params.get_list("threatTypes") == ["MALWARE", "SOCIAL_ENGINEERING", "UNWANTED_SOFTWARE"]
        return httpx.Response(200, json={"threat": {"threatTypes": ["MALWARE"], "expireTime": "2026-10-18T00:00:00Z"}})

    wr = WebRiskProvider(httpx.AsyncClient(transport=httpx.MockTransport(h)), "k", max_retries=0)
    assert (await wr.lookup("https://a.b/")).data == {"match": True, "threat_types": ["MALWARE"]}
