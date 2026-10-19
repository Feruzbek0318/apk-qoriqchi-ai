import time

import pytest

from app.analysis.findings import Finding
from app.providers.base import ProviderOutcome, Status
from app.risk.policy import Risk, assess_apk, assess_url


def vt(mal=0, sus=0, undetected=60, label=None, date=None):
    return ProviderOutcome("virustotal", Status.OK, data={
        "stats": {"malicious": mal, "suspicious": sus, "undetected": undetected, "harmless": 0},
        "threat_label": label, "last_analysis_date": date or time.time()})


HIGH_HEUR = [Finding("perm_combo_x", "high", "combo")]


@pytest.mark.parametrize("mal,label,expected", [
    (0, None, Risk.LOW),
    (1, None, Risk.MEDIUM),
    (2, None, Risk.MEDIUM),
    (3, None, Risk.MEDIUM),
    (3, "trojan.x", Risk.HIGH),
    (5, None, Risk.HIGH),
    (5, "trojan.x", Risk.HIGH),
    (10, None, Risk.CRITICAL),
    (20, "trojan.x", Risk.CRITICAL),
])
def test_apk_detection_boundaries(mal, label, expected):
    assert assess_apk(vt(mal, label=label), []).level == expected


def test_missing_report_is_unknown_not_clean():
    a = assess_apk(ProviderOutcome("virustotal", Status.NOT_FOUND), [])
    assert a.level == Risk.UNKNOWN
    assert any("No existing report found." in x for x in a.limitations)


@pytest.mark.parametrize("status", [Status.FAILED, Status.QUOTA, Status.NOT_CONFIGURED, Status.AUTH_FAILED])
def test_provider_failure_is_unknown(status):
    assert assess_apk(ProviderOutcome("virustotal", status), []).level == Risk.UNKNOWN


def test_heuristics_alone_cap_at_medium():
    assert assess_apk(ProviderOutcome("virustotal", Status.NOT_FOUND), HIGH_HEUR).level == Risk.MEDIUM
    assert assess_apk(vt(0), HIGH_HEUR).level == Risk.MEDIUM


def test_signer_change_is_high():
    assert assess_apk(vt(0), [], signer_changed=True).level == Risk.HIGH


def test_too_few_engines_is_unknown():
    assert assess_apk(vt(0, undetected=2), []).level == Risk.UNKNOWN


def test_stale_report_noted():
    a = assess_apk(vt(0, date=time.time() - 400 * 86400), [])
    assert a.level == Risk.LOW and any("180 days" in x for x in a.limitations)


def test_zero_detections_never_claims_safe():
    a = assess_apk(vt(0), [])
    assert any("does not prove safety" in r for r in a.reasons)


SB_HIT = ProviderOutcome("safe_browsing", Status.OK, data={"threat_types": ["SOCIAL_ENGINEERING"]})
SB_CLEAN = ProviderOutcome("safe_browsing", Status.OK, data={"threat_types": []})
SB_FAIL = ProviderOutcome("safe_browsing", Status.FAILED)
VT_NF = ProviderOutcome("virustotal", Status.NOT_FOUND)


def test_url_reputation_hit_high():
    assert assess_url(SB_HIT, VT_NF, []).level == Risk.HIGH


def test_url_independent_sources_agree_critical():
    assert assess_url(SB_HIT, vt(6), []).level == Risk.CRITICAL


def test_url_clean_sources_low():
    a = assess_url(SB_CLEAN, vt(0), [])
    assert a.level == Risk.LOW and a.confidence == "high"


def test_url_all_failed_unknown():
    assert assess_url(SB_FAIL, VT_NF, []).level == Risk.UNKNOWN


def test_url_all_failed_but_lookalike_medium():
    assert assess_url(SB_FAIL, VT_NF, [Finding("lookalike", "high", "x")]).level == Risk.MEDIUM


def test_url_conflicting_sources_take_worst():
    assert assess_url(SB_CLEAN, vt(7), []).level == Risk.HIGH
