"""Scan orchestration. Jobs run as in-process asyncio tasks.

Redis/a task queue is intentionally not used: a scan is a few HTTP calls plus optional polling, and one
instance comfortably handles that. Horizontal scaling would require moving jobs to a queue (see README).
"""
import asyncio
import logging
import shutil
from collections.abc import Callable
from contextlib import suppress
from pathlib import Path
from typing import Any

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.ai.analyst import AiUnavailable
from app.analysis import url as url_analysis
from app.analysis.apk import categorize_permissions, inspect_zip, permission_findings
from app.analysis.findings import Finding
from app.config import Settings
from app.models import Scan, utcnow
from app.providers.base import ProviderOutcome, Status
from app.providers.registry import Providers
from app.risk.policy import Assessment, assess_apk, assess_url

log = logging.getLogger(__name__)


class ScanService:
    def __init__(self, providers: Providers, settings: Settings, session_factory: Callable[[], Session]) -> None:
        self.p = providers
        self.settings = settings
        self.session_factory = session_factory
        self.tasks: dict[str, asyncio.Task] = {}

    # ---- lifecycle -------------------------------------------------------
    def start(self, scan_id: str, coro: Any) -> None:
        task = asyncio.create_task(self._guard(scan_id, coro))
        self.tasks[scan_id] = task
        task.add_done_callback(lambda _: self.tasks.pop(scan_id, None))

    def cancel(self, scan_id: str) -> bool:
        task = self.tasks.get(scan_id)
        if task and not task.done():
            task.cancel()
            return True
        return False

    async def shutdown(self) -> None:
        for t in list(self.tasks.values()):
            t.cancel()
        with suppress(Exception):
            await asyncio.gather(*self.tasks.values(), return_exceptions=True)

    async def _guard(self, scan_id: str, coro: Any) -> None:
        self._update(scan_id, status="running")
        try:
            await coro
        except asyncio.CancelledError:
            self._update(scan_id, status="canceled", error="Scan canceled", completed_at=utcnow())
            raise
        except Exception:
            log.exception("scan failed", extra={"fields": {"scan_id": scan_id}})
            self._update(scan_id, status="failed", error="Internal error while scanning", completed_at=utcnow())

    def _update(self, scan_id: str, **fields: Any) -> None:
        with self.session_factory() as db:
            scan = db.get(Scan, scan_id)
            if scan is None:  # deleted by the user mid-scan
                return
            for k, v in fields.items():
                setattr(scan, k, v)
            db.commit()

    # ---- shared ------------------------------------------------------------
    async def _poll_vt(self, analysis_id: str) -> ProviderOutcome:
        for _ in range(self.settings.vt_poll_max_attempts):
            await asyncio.sleep(self.settings.vt_poll_interval_s)
            res = await self.p.vt.analysis(analysis_id)
            if res.status != Status.OK:
                return res
            if res.data.get("status") == "completed":
                return res
        return ProviderOutcome("virustotal", Status.FAILED, error="Timed out waiting for VirusTotal analysis")

    async def _finish(self, scan_id: str, evidence: dict[str, Any], assessment: Assessment,
                      outcomes: list[ProviderOutcome], language: str, want_ai: bool) -> None:
        ai: dict | None = None
        if want_ai and self.p.analyst.configured:
            try:
                ai = (await self.p.analyst.explain(evidence, language)).model_dump()
            except AiUnavailable as e:
                ai = {"error": str(e)}
        incomplete = any(not o.completed for o in outcomes if o.status != Status.NOT_CONFIGURED) or assessment.level.value == "UNKNOWN"
        self._update(
            scan_id,
            status="completed",
            risk_level=assessment.level.value,
            confidence=assessment.confidence,
            provider_status={o.provider: o.summary() for o in outcomes},
            findings={**{k: v for k, v in evidence.items() if k != "policy_verdict"}, "assessment": assessment.to_dict(), "incomplete": incomplete},
            ai_analysis=ai,
            fixture=any(o.fixture for o in outcomes),
            completed_at=utcnow(),
        )

    # ---- URL ----------------------------------------------------------------
    async def run_url(self, scan_id: str, url: str, language: str, want_ai: bool, submit_if_unknown: bool) -> None:
        findings = url_analysis.heuristics(url)
        rep, vt = await asyncio.gather(self.p.url_reputation.lookup(url), self.p.vt.url_report(url))
        if vt.status == Status.NOT_FOUND and submit_if_unknown:
            sub = await self.p.vt.submit_url(url)
            if sub.status == Status.OK:
                polled = await self._poll_vt(sub.data["analysis_id"])
                vt = polled if polled.status == Status.OK else vt
        assessment = assess_url(rep, vt, findings)
        evidence = {
            "scan_type": "url",
            "target": url_analysis.minimize(url),
            "policy_verdict": assessment.to_dict(),
            "provider_status": {o.provider: o.status.value for o in (rep, vt)},
            "reputation_threat_types": rep.data.get("threat_types", []),
            "virustotal": {k: vt.data.get(k) for k in ("stats", "threat_label", "last_analysis_date", "categories")} if vt.status == Status.OK else None,
            "flagged_engines": vt.data.get("flagged_engines", []) if vt.status == Status.OK else [],
            "final_url": url_analysis.minimize(vt.data["last_final_url"]) if vt.data.get("last_final_url") else None,
            "heuristic_findings": [f.to_dict() for f in findings],
        }
        await self._finish(scan_id, evidence, assessment, [rep, vt], language, want_ai)

    # ---- APK ----------------------------------------------------------------
    def _signer_changed(self, client_id: str, scan_id: str, package: str | None, signer: str | None) -> bool:
        if not package or not signer:
            return False
        with self.session_factory() as db:
            rows = db.scalars(select(Scan).where(Scan.client_id == client_id, Scan.scan_type == "apk",
                                                 Scan.status == "completed", Scan.id != scan_id)
                              .order_by(Scan.created_at.desc()).limit(100)).all()
        for r in rows:
            meta = (r.findings or {}).get("metadata") or {}
            if meta.get("package_name") == package and meta.get("signer_sha256") and meta["signer_sha256"] != signer:
                return True
        return False

    async def run_apk(self, scan_id: str, client_id: str, meta: dict[str, Any], file_path: Path | None,
                      consent_upload: bool, language: str, want_ai: bool) -> None:
        try:
            findings: list[Finding] = permission_findings(meta.get("permissions", []), meta.get("target_sdk"), meta.get("debug_certificate", False))
            zip_info = None
            if file_path is not None:
                z = await asyncio.wait_for(asyncio.to_thread(inspect_zip, file_path), timeout=30)
                zip_info = z.__dict__
                if not z.has_manifest:
                    findings.append(Finding("no_manifest", "high", "Archive has no AndroidManifest.xml (not a valid APK)", kind="integrity"))
                if not z.v1_signature_files:
                    findings.append(Finding("no_v1_signature", "info", "No JAR (v1) signature files; v2+/v3 signatures are not verified by the backend", kind="integrity"))
            vt = await self.p.vt.file_report(meta["sha256"])
            if vt.status == Status.NOT_FOUND and file_path is not None and consent_upload:
                up = await self.p.vt.upload_file(file_path)
                if up.status == Status.OK:
                    polled = await self._poll_vt(up.data["analysis_id"])
                    vt = polled if polled.status == Status.OK else ProviderOutcome("virustotal", polled.status, error=polled.error)
                else:
                    vt = up
            signer_changed = self._signer_changed(client_id, scan_id, meta.get("package_name"), meta.get("signer_sha256"))
            assessment = assess_apk(vt, findings, signer_changed)
            evidence = {
                "scan_type": "apk",
                "target": meta["file_name"],
                "metadata": {k: meta.get(k) for k in ("package_name", "version_name", "version_code", "min_sdk", "target_sdk", "signer_sha256", "size_bytes")},
                "policy_verdict": assessment.to_dict(),
                "provider_status": {"virustotal": vt.status.value},
                "virustotal": {k: vt.data.get(k) for k in ("stats", "threat_label", "last_analysis_date", "reputation")} if vt.status == Status.OK else None,
                "flagged_engines": vt.data.get("flagged_engines", []) if vt.status == Status.OK else [],
                "permissions": categorize_permissions(meta.get("permissions", [])),
                "heuristic_findings": [f.to_dict() for f in findings],
                "signer_changed": signer_changed,
                "archive": zip_info,
            }
            await self._finish(scan_id, evidence, assessment, [vt], language, want_ai)
        finally:
            if file_path is not None:
                shutil.rmtree(file_path.parent, ignore_errors=True)  # never retain APK contents
