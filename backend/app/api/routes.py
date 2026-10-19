import tempfile
from pathlib import Path

from fastapi import APIRouter, Depends, File, Form, HTTPException, Request, UploadFile, status
from sqlalchemy import delete, func, select
from sqlalchemy.orm import Session

from app.analysis import apk as apk_analysis
from app.analysis import url as url_analysis
from app.db import get_db
from app.models import Client, Scan
from app.schemas import (ApkScanRequest, HistoryPage, ProviderStatusOut, RegisterRequest, RegisterResponse,
                         ScanAccepted, ScanOut, UrlScanRequest)
from app.security import current_client, new_token, rate_limit_key, secrets_equal

router = APIRouter(prefix="/api/v1")


def _limit(request: Request) -> None:
    request.app.state.rate_limiter.check(rate_limit_key(request))


def _owned_scan(db: Session, client: Client, scan_id: str) -> Scan:
    scan = db.get(Scan, scan_id)
    # 404 (not 403) for other clients' scans so IDs can't be probed.
    if scan is None or scan.client_id != client.id:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Scan not found")
    return scan


@router.post("/auth/register", response_model=RegisterResponse, status_code=201, dependencies=[Depends(_limit)])
def register(body: RegisterRequest, request: Request, db: Session = Depends(get_db)) -> RegisterResponse:
    expected = request.app.state.settings.registration_secret
    if expected is not None and not secrets_equal(body.registration_secret or "", expected.get_secret_value()):
        raise HTTPException(status.HTTP_403_FORBIDDEN, "Registration not allowed")
    token, token_hash = new_token()
    client = Client(token_hash=token_hash)
    db.add(client)
    db.commit()
    return RegisterResponse(client_id=client.id, token=token)


@router.post("/scans/url", response_model=ScanAccepted, status_code=202, dependencies=[Depends(_limit)])
async def scan_url(body: UrlScanRequest, request: Request, client: Client = Depends(current_client),
                   db: Session = Depends(get_db)) -> ScanAccepted:
    try:
        url = url_analysis.normalize(body.url)
    except url_analysis.InvalidUrl as e:
        raise HTTPException(422, str(e)) from e
    scan = Scan(client_id=client.id, scan_type="url", target=url_analysis.minimize(url))
    db.add(scan)
    db.commit()
    request.app.state.scans.start(scan.id, request.app.state.scans.run_url(scan.id, url, body.language, body.ai, body.submit_if_unknown))
    return ScanAccepted(scan_id=scan.id, status="queued")


@router.post("/scans/apk", response_model=ScanAccepted, status_code=202, dependencies=[Depends(_limit)])
async def scan_apk_metadata(body: ApkScanRequest, request: Request, client: Client = Depends(current_client),
                            db: Session = Depends(get_db)) -> ScanAccepted:
    """Hash + on-device metadata lookup. Preferred: no file leaves the device."""
    meta = body.model_dump()
    meta["sha256"] = body.sha256.lower()
    scan = Scan(client_id=client.id, scan_type="apk", target=body.file_name, sha256=meta["sha256"])
    db.add(scan)
    db.commit()
    request.app.state.scans.start(scan.id, request.app.state.scans.run_apk(scan.id, client.id, meta, None, False, body.language, body.ai))
    return ScanAccepted(scan_id=scan.id, status="queued")


@router.post("/scans/apk/upload", response_model=ScanAccepted, status_code=202, dependencies=[Depends(_limit)])
async def scan_apk_upload(request: Request, file: UploadFile = File(...),
                          consent_upload_to_virustotal: bool = Form(False), language: str = Form("en"),
                          ai: bool = Form(True), client: Client = Depends(current_client),
                          db: Session = Depends(get_db)) -> ScanAccepted:
    """Uploads the APK to this backend for hashing and structural checks. It is forwarded to VirusTotal
    ONLY if the hash is unknown AND consent_upload_to_virustotal is true. The file is deleted afterwards."""
    settings = request.app.state.settings
    name = Path(file.filename or "upload.apk").name
    try:
        apk_analysis.validate_name_and_mime(name, file.content_type)
    except apk_analysis.InvalidApk as e:
        raise HTTPException(422, str(e)) from e
    workdir = Path(tempfile.mkdtemp(prefix="apkscan-"))
    dest = workdir / "sample.apk"
    try:
        sha256, size = apk_analysis.stream_to_file(file.file, dest, settings.max_upload_bytes)
    except apk_analysis.InvalidApk as e:
        import shutil
        shutil.rmtree(workdir, ignore_errors=True)
        code = status.HTTP_413_REQUEST_ENTITY_TOO_LARGE if "limit" in str(e) else 422
        raise HTTPException(code, str(e)) from e
    lang = language if language in ("uz", "ru", "en") else "en"
    meta = {"sha256": sha256, "file_name": name, "size_bytes": size, "permissions": []}
    scan = Scan(client_id=client.id, scan_type="apk", target=name, sha256=sha256)
    db.add(scan)
    db.commit()
    request.app.state.scans.start(scan.id, request.app.state.scans.run_apk(scan.id, client.id, meta, dest, consent_upload_to_virustotal, lang, ai))
    return ScanAccepted(scan_id=scan.id, status="queued")


@router.get("/scans/{scan_id}", response_model=ScanOut)
def get_scan(scan_id: str, client: Client = Depends(current_client), db: Session = Depends(get_db)) -> Scan:
    return _owned_scan(db, client, scan_id)


@router.post("/scans/{scan_id}/cancel", response_model=ScanOut)
def cancel_scan(scan_id: str, request: Request, client: Client = Depends(current_client), db: Session = Depends(get_db)) -> Scan:
    scan = _owned_scan(db, client, scan_id)
    if scan.status in ("queued", "running") and request.app.state.scans.cancel(scan_id):
        scan.status = "canceled"
        scan.error = "Scan canceled"
        db.commit()
    return scan


@router.get("/history", response_model=HistoryPage)
def history(limit: int = 50, offset: int = 0, client: Client = Depends(current_client), db: Session = Depends(get_db)) -> HistoryPage:
    limit = max(1, min(limit, 200))
    q = select(Scan).where(Scan.client_id == client.id)
    total = db.scalar(select(func.count()).select_from(q.subquery())) or 0
    items = db.scalars(q.order_by(Scan.created_at.desc()).limit(limit).offset(max(0, offset))).all()
    return HistoryPage(items=[ScanOut.model_validate(i) for i in items], total=total)


@router.delete("/history/{scan_id}", status_code=204)
def delete_scan(scan_id: str, request: Request, client: Client = Depends(current_client), db: Session = Depends(get_db)) -> None:
    scan = _owned_scan(db, client, scan_id)
    request.app.state.scans.cancel(scan_id)
    db.delete(scan)
    db.commit()


@router.delete("/history", status_code=204)
def clear_history(request: Request, client: Client = Depends(current_client), db: Session = Depends(get_db)) -> None:
    for sid in [s for s in db.scalars(select(Scan.id).where(Scan.client_id == client.id))]:
        request.app.state.scans.cancel(sid)
    db.execute(delete(Scan).where(Scan.client_id == client.id))
    db.commit()


@router.get("/providers/status", response_model=list[ProviderStatusOut])
def providers_status(request: Request, _: Client = Depends(current_client)) -> list[ProviderStatusOut]:
    p = request.app.state.providers
    rep_note = ("Safe Browsing API is for non-commercial use; set URL_REPUTATION_PROVIDER=webrisk for commercial deployments"
                if p.url_reputation.name == "safe_browsing" else None)
    return [
        ProviderStatusOut(name="virustotal", configured=p.vt.configured, fixture=p.fixture,
                          note="Public API: 4 requests/min, 500/day; not for commercial use"),
        ProviderStatusOut(name=p.url_reputation.name, configured=p.url_reputation.configured, fixture=p.fixture, note=rep_note),
        ProviderStatusOut(name="ai_analyst", configured=p.analyst.configured, fixture=False),
    ]
