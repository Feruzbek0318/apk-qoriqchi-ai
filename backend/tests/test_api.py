import io
import time
import zipfile
from types import SimpleNamespace

from app.ai.analyst import AiAnalysis, Analyst
from tests.conftest import auth

SHA = "b" * 64
VT = "https://www.virustotal.com/api/v3"
SB = "https://safebrowsing.googleapis.com/v5/urls:search"


def wait(client, headers, scan_id, timeout=5.0):
    end = time.time() + timeout
    while time.time() < end:
        body = client.get(f"/api/v1/scans/{scan_id}", headers=headers).json()
        if body["status"] not in ("queued", "running"):
            return body
        time.sleep(0.02)
    raise AssertionError("scan did not finish")


def vt_stats(mal):
    return {"data": {"attributes": {"last_analysis_stats": {"malicious": mal, "suspicious": 0, "undetected": 60, "harmless": 5},
                                    "last_analysis_results": {}, "last_analysis_date": int(time.time())}}}


def test_health_and_ready(client):
    assert client.get("/health").json() == {"status": "ok"}
    r = client.get("/ready")
    assert r.status_code == 200 and r.json()["database"] == "ok"
    assert r.headers["x-request-id"]


def test_auth_required(client):
    assert client.get("/api/v1/history").status_code == 401
    assert client.get("/api/v1/history", headers={"Authorization": "Bearer nope"}).status_code == 401


def test_registration_secret(make_client):
    with make_client(registration_secret="s3cret") as c:
        assert c.post("/api/v1/auth/register", json={}).status_code == 403
        assert c.post("/api/v1/auth/register", json={"registration_secret": "s3cret"}).status_code == 201


def test_url_scan_end_to_end_and_minimized_history(client, upstream):
    upstream.add("GET", SB, json={"threats": [{"url": "x", "threatTypes": ["SOCIAL_ENGINEERING"]}]})
    upstream.add("GET", f"{VT}/urls/", json=vt_stats(8))
    h = auth(client)
    r = client.post("/api/v1/scans/url", json={"url": "https://paypa1-login.xyz/verify?session=SECRET#x"}, headers=h)
    assert r.status_code == 202
    scan = wait(client, h, r.json()["scan_id"])
    assert scan["status"] == "completed" and scan["risk_level"] == "CRITICAL"
    assert scan["target"] == "https://paypa1-login.xyz/verify"
    assert "SECRET" not in str(scan)
    assert scan["provider_status"]["safe_browsing"]["status"] == "ok"


def test_url_scan_never_contacts_target_host(client, upstream):
    """SSRF guard: only reputation API hosts are contacted, never the user's URL."""
    h = auth(client)
    sid = client.post("/api/v1/scans/url", json={"url": "http://169.254.169.254/latest/meta-data"}, headers=h).json()["scan_id"]
    wait(client, h, sid)
    assert set(upstream.hosts) <= {"www.virustotal.com", "safebrowsing.googleapis.com"}


def test_url_missing_reports_unknown(client, upstream):
    upstream.add("GET", SB, status=503)
    h = auth(client)
    sid = client.post("/api/v1/scans/url", json={"url": "https://example.org/"}, headers=h).json()["scan_id"]
    scan = wait(client, h, sid)
    assert scan["risk_level"] == "UNKNOWN" and scan["findings"]["incomplete"] is True


def test_invalid_url_422(client):
    assert client.post("/api/v1/scans/url", json={"url": "javascript:alert(1)"}, headers=auth(client)).status_code == 422


def test_apk_metadata_scan_not_found_is_unknown(client, upstream):
    h = auth(client)
    sid = client.post("/api/v1/scans/apk", headers=h, json={
        "sha256": SHA, "file_name": "bank.apk", "size_bytes": 1000, "package_name": "com.example.bank",
        "permissions": ["android.permission.READ_SMS"], "language": "uz"}).json()["scan_id"]
    scan = wait(client, h, sid)
    assert scan["risk_level"] == "UNKNOWN"
    assert scan["provider_status"]["virustotal"]["error"] == "No existing report found."


def test_apk_metadata_validation(client):
    h = auth(client)
    bad = {"sha256": "xyz", "file_name": "a.apk", "size_bytes": 1}
    assert client.post("/api/v1/scans/apk", json=bad, headers=h).status_code == 422
    bad2 = {"sha256": SHA, "file_name": "a.exe", "size_bytes": 1}
    assert client.post("/api/v1/scans/apk", json=bad2, headers=h).status_code == 422
    bad3 = {"sha256": SHA, "file_name": "a.apk", "size_bytes": 1, "permissions": ["<script>"]}
    assert client.post("/api/v1/scans/apk", json=bad3, headers=h).status_code == 422


def test_signer_change_detected(client, upstream):
    upstream.add("GET", f"{VT}/files/", json=vt_stats(0))
    h = auth(client)
    base = {"file_name": "a.apk", "size_bytes": 10, "package_name": "com.example.app"}
    s1 = client.post("/api/v1/scans/apk", headers=h, json={**base, "sha256": "1" * 64, "signer_sha256": "c" * 64}).json()["scan_id"]
    assert wait(client, h, s1)["risk_level"] == "LOW"
    s2 = client.post("/api/v1/scans/apk", headers=h, json={**base, "sha256": "2" * 64, "signer_sha256": "d" * 64}).json()["scan_id"]
    scan = wait(client, h, s2)
    assert scan["risk_level"] == "HIGH" and scan["findings"]["signer_changed"] is True


def make_apk() -> bytes:
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as z:
        z.writestr("AndroidManifest.xml", b"x")
        z.writestr("classes.dex", b"x")
    return buf.getvalue()


def test_upload_without_consent_never_uploads(client, upstream):
    h = auth(client)
    r = client.post("/api/v1/scans/apk/upload", headers=h,
                    files={"file": ("a.apk", make_apk(), "application/vnd.android.package-archive")})
    scan = wait(client, h, r.json()["scan_id"])
    assert scan["risk_level"] == "UNKNOWN"
    assert not any(c.method == "POST" for c in upstream.calls)


def test_upload_with_consent_polls_analysis(client, upstream):
    upstream.add("POST", f"{VT}/files", json={"data": {"id": "an-1", "type": "analysis"}})
    upstream.add("GET", f"{VT}/analyses/an-1", json={"data": {"attributes": {
        "status": "completed", "stats": {"malicious": 0, "suspicious": 0, "undetected": 60, "harmless": 0}, "results": {}}}})
    h = auth(client)
    r = client.post("/api/v1/scans/apk/upload", headers=h, data={"consent_upload_to_virustotal": "true"},
                    files={"file": ("a.apk", make_apk(), "application/vnd.android.package-archive")})
    scan = wait(client, h, r.json()["scan_id"])
    assert scan["status"] == "completed" and scan["risk_level"] == "LOW"
    assert any(c.method == "POST" for c in upstream.calls)


def test_upload_rejects_non_apk(client):
    h = auth(client)
    r = client.post("/api/v1/scans/apk/upload", headers=h, files={"file": ("a.apk", b"MZ not zip", "application/octet-stream")})
    assert r.status_code == 422


def test_ownership_and_deletion(client, upstream):
    a, b = auth(client), auth(client)
    sid = client.post("/api/v1/scans/url", json={"url": "https://example.com"}, headers=a).json()["scan_id"]
    wait(client, a, sid)
    assert client.get(f"/api/v1/scans/{sid}", headers=b).status_code == 404
    assert client.delete(f"/api/v1/history/{sid}", headers=b).status_code == 404
    assert client.get("/api/v1/history", headers=b).json()["total"] == 0
    assert client.get("/api/v1/history", headers=a).json()["total"] == 1
    assert client.delete(f"/api/v1/history/{sid}", headers=a).status_code == 204
    assert client.get(f"/api/v1/scans/{sid}", headers=a).status_code == 404


def test_clear_history(client):
    h = auth(client)
    for u in ("https://a.com", "https://b.com"):
        wait(client, h, client.post("/api/v1/scans/url", json={"url": u}, headers=h).json()["scan_id"])
    assert client.delete("/api/v1/history", headers=h).status_code == 204
    assert client.get("/api/v1/history", headers=h).json()["total"] == 0


def test_rate_limit(make_client):
    with make_client(rate_limit_per_minute=2) as c:
        codes = [c.post("/api/v1/auth/register", json={}).status_code for _ in range(3)]
        assert codes == [201, 201, 429]


def test_body_size_limit(client):
    r = client.post("/api/v1/scans/url", content=b"{" + b" " * 300_000 + b"}",
                    headers={**auth(client), "content-type": "application/json"})
    assert r.status_code == 413


def test_providers_status(client):
    names = {p["name"]: p for p in client.get("/api/v1/providers/status", headers=auth(client)).json()}
    assert names["virustotal"]["configured"] and names["safe_browsing"]["configured"]
    assert not names["ai_analyst"]["configured"]


def test_ai_attached_and_grounded(make_client, upstream):
    upstream.add("GET", SB, json={})
    upstream.add("GET", f"{VT}/urls/", json=vt_stats(0))
    out = AiAnalysis(risk_level="HIGH", confidence="low", verdict="v", confirmed_detections=["Made-up engine"],
                     suspicious_indicators=[], benign_or_explanatory_findings=[], engine_disagreements=[], evidence=[],
                     limitations=[], recommended_actions=["a"], localized_summary="s")

    class Msgs:
        async def parse(self, **kw):
            return SimpleNamespace(stop_reason="end_turn", parsed_output=out)

    analyst = Analyst(None, "claude-opus-5-5", client=SimpleNamespace(beta=SimpleNamespace(messages=Msgs())))
    with make_client(analyst=analyst) as c:
        h = auth(c)
        scan = wait(c, h, c.post("/api/v1/scans/url", json={"url": "https://example.com"}, headers=h).json()["scan_id"])
    assert scan["risk_level"] == "LOW"
    assert scan["ai_analysis"]["risk_level"] == "LOW"  # policy wins over AI
    assert scan["ai_analysis"]["confirmed_detections"] == []


def test_fixture_mode_refused_in_production():
    import pytest
    from app.config import Settings
    with pytest.raises(ValueError):
        Settings(app_env="production", provider_fixture_mode=True)


def test_fixture_mode_labels_results(make_client):
    with make_client(provider_fixture_mode=True) as c:
        h = auth(c)
        scan = wait(c, h, c.post("/api/v1/scans/url", json={"url": "https://bad.example"}, headers=h).json()["scan_id"])
    assert scan["fixture"] is True
