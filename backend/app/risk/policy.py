"""Deterministic, testable risk policy. The AI never decides the level; it only explains this output.

Levels: LOW (no significant threat detected by the checks performed), MEDIUM (suspicious, needs review),
HIGH (strong evidence of a threat), CRITICAL (confirmed severe threat), UNKNOWN (insufficient evidence).
"""
import time
from dataclasses import dataclass, field
from enum import Enum

from app.analysis.findings import Finding
from app.providers.base import ProviderOutcome, Status

STALE_AFTER_S = 180 * 24 * 3600
MIN_ENGINES = 5


class Risk(str, Enum):
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"
    CRITICAL = "CRITICAL"
    UNKNOWN = "UNKNOWN"


_ORDER = [Risk.UNKNOWN, Risk.LOW, Risk.MEDIUM, Risk.HIGH, Risk.CRITICAL]


@dataclass
class Assessment:
    level: Risk
    confidence: str  # low | medium | high
    reasons: list[str] = field(default_factory=list)
    limitations: list[str] = field(default_factory=list)

    def to_dict(self) -> dict:
        return {"level": self.level.value, "confidence": self.confidence, "reasons": self.reasons, "limitations": self.limitations}


def _max(a: Risk, b: Risk) -> Risk:
    return a if _ORDER.index(a) >= _ORDER.index(b) else b


def _vt_level(vt: ProviderOutcome, reasons: list[str], limitations: list[str]) -> Risk | None:
    """None = VT gave no usable verdict."""
    if vt.status == Status.NOT_FOUND:
        limitations.append("VirusTotal: No existing report found.")
        return None
    if vt.status != Status.OK:
        limitations.append(f"VirusTotal unavailable ({vt.status.value})")
        return None
    s = vt.data.get("stats", {})
    mal, sus = s.get("malicious", 0), s.get("suspicious", 0)
    engines = mal + sus + s.get("undetected", 0) + s.get("harmless", 0)
    label = vt.data.get("threat_label")
    if engines < MIN_ENGINES:
        limitations.append(f"Only {engines} engines produced a verdict")
        return None
    date = vt.data.get("last_analysis_date")
    if isinstance(date, (int, float)) and time.time() - date > STALE_AFTER_S:
        limitations.append("VirusTotal analysis is older than 180 days; results may be outdated")
    ratio = f"{mal}/{engines} engines flagged it as malicious"
    if mal >= 10 or (mal >= 5 and label):
        reasons.append(ratio + (f" (label: {label})" if label else ""))
        return Risk.CRITICAL if mal >= 10 and label else Risk.HIGH if mal < 10 else Risk.CRITICAL
    if mal >= 5 or (mal >= 3 and label):
        reasons.append(ratio)
        return Risk.HIGH
    if mal >= 1 or sus >= 3:
        reasons.append(f"{ratio}, {sus} suspicious — a minority of engines; could be a false positive")
        return Risk.MEDIUM
    reasons.append(f"0/{engines} engines flagged it (this does not prove safety)")
    return Risk.LOW


def _heuristic_level(findings: list[Finding], reasons: list[str]) -> Risk:
    high = [f for f in findings if f.severity == "high"]
    med = [f for f in findings if f.severity == "medium"]
    if high or len(med) >= 2:
        reasons.extend(f"Indicator: {f.evidence}" for f in (high + med)[:5])
        # Heuristics alone are capped at MEDIUM: a permission or a domain shape is not proof of malware.
        return Risk.MEDIUM
    return Risk.LOW


def assess_apk(vt: ProviderOutcome, findings: list[Finding], signer_changed: bool = False) -> Assessment:
    reasons: list[str] = []
    limitations = ["Static metadata and reputation only; the APK was not executed or decompiled."]
    vt_level = _vt_level(vt, reasons, limitations)
    heur = _heuristic_level(findings, reasons)
    if signer_changed:
        reasons.append("Signing certificate differs from a previously seen version of this package")
        heur = Risk.HIGH
    if vt_level is None:
        level = heur if heur != Risk.LOW else Risk.UNKNOWN
        return Assessment(level, "low", reasons, limitations)
    level = _max(vt_level, heur)
    confidence = "high" if vt_level in (Risk.CRITICAL, Risk.HIGH) or (vt_level == Risk.LOW and heur == Risk.LOW) else "medium"
    return Assessment(level, confidence, reasons, limitations)


def assess_url(reputation: ProviderOutcome, vt: ProviderOutcome, findings: list[Finding]) -> Assessment:
    reasons: list[str] = []
    limitations = ["The link was not opened; only reputation services and lexical checks were used."]
    levels: list[Risk] = []
    rep_hit = False
    if reputation.status == Status.OK:
        types = reputation.data.get("threat_types", [])
        if types:
            rep_hit = True
            reasons.append(f"{reputation.provider} lists this URL as {', '.join(types)}")
            levels.append(Risk.HIGH)
        else:
            levels.append(Risk.LOW)
    else:
        limitations.append(f"{reputation.provider} unavailable ({reputation.status.value})")
    vt_level = _vt_level(vt, reasons, limitations)
    if vt_level is not None:
        levels.append(vt_level)
    heur = _heuristic_level(findings, reasons)
    if not levels:
        return Assessment(heur if heur != Risk.LOW else Risk.UNKNOWN, "low", reasons, limitations)
    level = Risk.LOW
    for lv in levels + [heur]:
        level = _max(level, lv)
    if rep_hit and vt_level in (Risk.HIGH, Risk.CRITICAL):
        level = Risk.CRITICAL  # independent sources agree
    agree = len(levels) >= 2 and len(set(levels)) == 1
    return Assessment(level, "high" if agree else "medium", reasons, limitations)
