"""APK file validation, streaming hashing, and permission categorization.

The APK is treated as untrusted input: it is never executed or installed, only hashed and inspected as a ZIP.
"""
import hashlib
import re
import zipfile
from collections.abc import Iterable
from dataclasses import dataclass
from pathlib import Path
from typing import BinaryIO

from app.analysis.findings import Finding

APK_MIME_TYPES = {"application/vnd.android.package-archive", "application/octet-stream", "application/zip"}
ZIP_MAGIC = b"PK\x03\x04"
SHA256_RE = re.compile(r"^[a-f0-9]{64}$")
MAX_ZIP_ENTRIES = 50_000
CHUNK = 1024 * 1024


class InvalidApk(ValueError):
    pass


def validate_name_and_mime(file_name: str, mime: str | None) -> None:
    if not file_name.lower().endswith(".apk") and (mime or "") != "application/vnd.android.package-archive":
        raise InvalidApk("File must be an .apk")
    if mime and mime not in APK_MIME_TYPES:
        raise InvalidApk(f"Unexpected MIME type {mime}")


def stream_to_file(src: BinaryIO, dest: Path, max_bytes: int) -> tuple[str, int]:
    """Copies src → dest while computing SHA-256, enforcing max_bytes. Returns (sha256, size)."""
    digest, size, first = hashlib.sha256(), 0, True
    with dest.open("wb") as out:
        while chunk := src.read(CHUNK):
            if first:
                if not chunk.startswith(ZIP_MAGIC):
                    raise InvalidApk("Not an APK/ZIP file (bad magic bytes)")
                first = False
            size += len(chunk)
            if size > max_bytes:
                raise InvalidApk(f"File exceeds the {max_bytes // (1024 * 1024)} MB limit")
            digest.update(chunk)
            out.write(chunk)
    if size == 0:
        raise InvalidApk("Empty file")
    return digest.hexdigest(), size


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as fh:
        while chunk := fh.read(CHUNK):
            digest.update(chunk)
    return digest.hexdigest()


@dataclass
class ZipInspection:
    entries: int
    has_manifest: bool
    dex_files: int
    native_abis: list[str]
    v1_signature_files: list[str]


def inspect_zip(path: Path) -> ZipInspection:
    """Structural inspection only (no decompression of contents → no zip-bomb exposure)."""
    try:
        with zipfile.ZipFile(path) as zf:
            infos = zf.infolist()
    except (zipfile.BadZipFile, OSError) as e:
        raise InvalidApk("Corrupt APK archive") from e
    if len(infos) > MAX_ZIP_ENTRIES:
        raise InvalidApk("APK has an implausible number of entries")
    names = [i.filename for i in infos]
    return ZipInspection(
        entries=len(names),
        has_manifest="AndroidManifest.xml" in names,
        dex_files=sum(1 for n in names if re.fullmatch(r"classes\d*\.dex", n)),
        native_abis=sorted({n.split("/")[1] for n in names if n.startswith("lib/") and n.count("/") >= 2}),
        v1_signature_files=[n for n in names if n.startswith("META-INF/") and n.upper().endswith((".RSA", ".DSA", ".EC"))],
    )


# category, severity, when legitimate, when suspicious
PERMISSIONS: dict[str, tuple[str, str, str, str]] = {
    "android.permission.READ_SMS": ("sms", "high", "SMS apps, OTP autofill", "banking trojans read OTP codes"),
    "android.permission.RECEIVE_SMS": ("sms", "high", "SMS apps, OTP autofill", "intercepting bank OTPs"),
    "android.permission.SEND_SMS": ("sms", "high", "SMS apps", "premium-SMS fraud, spreading via SMS"),
    "android.permission.READ_CONTACTS": ("contacts", "medium", "messengers, dialers", "harvesting contacts for spam/fraud"),
    "android.permission.READ_CALL_LOG": ("call_log", "high", "dialers", "surveillance of calls"),
    "android.permission.CALL_PHONE": ("phone", "medium", "dialers", "calling premium numbers"),
    "android.permission.READ_PHONE_STATE": ("phone", "low", "call-aware apps", "device fingerprinting"),
    "android.permission.ACCESS_FINE_LOCATION": ("location", "medium", "maps, delivery", "tracking the user"),
    "android.permission.ACCESS_BACKGROUND_LOCATION": ("location", "high", "fitness, family safety", "covert tracking"),
    "android.permission.RECORD_AUDIO": ("microphone", "medium", "voice messages, calls", "covert recording"),
    "android.permission.CAMERA": ("camera", "medium", "camera, QR scanners", "covert photos"),
    "android.permission.BIND_ACCESSIBILITY_SERVICE": ("accessibility", "high", "assistive tools", "screen reading, auto-clicking, stealing credentials"),
    "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE": ("notification_access", "high", "smartwatch companions", "reading OTPs from notifications"),
    "android.permission.REQUEST_INSTALL_PACKAGES": ("package_install", "high", "app stores, updaters", "dropping additional malware"),
    "android.permission.SYSTEM_ALERT_WINDOW": ("overlay", "high", "chat heads, screen filters", "fake login overlays over bank apps"),
    "android.permission.BIND_DEVICE_ADMIN": ("device_admin", "high", "enterprise MDM", "preventing uninstall, locking device"),
    "android.permission.QUERY_ALL_PACKAGES": ("package_visibility", "low", "launchers, security tools", "finding bank apps to target"),
    "android.permission.READ_EXTERNAL_STORAGE": ("storage", "low", "media apps", "reading private files"),
    "android.permission.MANAGE_EXTERNAL_STORAGE": ("storage", "medium", "file managers", "bulk file access/exfiltration"),
    "android.permission.RECEIVE_BOOT_COMPLETED": ("persistence", "info", "alarms, sync", "auto-start persistence"),
}

COMBOS: list[tuple[set[str], str, str]] = [
    ({"sms", "accessibility"}, "high", "SMS access + Accessibility is a common banking-trojan pattern"),
    ({"sms", "overlay"}, "high", "SMS access + screen overlay enables OTP theft with fake login screens"),
    ({"accessibility", "package_install"}, "high", "Accessibility + package installation can silently install further apps"),
    ({"notification_access", "overlay"}, "medium", "Notification access + overlay can capture codes and spoof screens"),
    ({"microphone", "location", "contacts"}, "medium", "Microphone + location + contacts resembles stalkerware capability sets"),
]


def categorize_permissions(permissions: Iterable[str]) -> list[dict]:
    out = []
    for perm in sorted(set(permissions)):
        meta = PERMISSIONS.get(perm)
        if meta:
            cat, sev, legit, susp = meta
            out.append({"permission": perm, "category": cat, "severity": sev, "legitimate_use": legit, "suspicious_use": susp})
    return out


def permission_findings(permissions: Iterable[str], target_sdk: int | None = None, debug_cert: bool = False) -> list[Finding]:
    cats = categorize_permissions(permissions)
    present = {c["category"] for c in cats}
    findings = [
        Finding(f"perm_{c['category']}", "low" if c["severity"] == "high" else "info",
                f"{c['permission']}: legitimate for {c['legitimate_use']}; suspicious if used for {c['suspicious_use']}")
        for c in cats if c["severity"] in ("high", "medium")
    ]
    for needed, sev, why in COMBOS:
        if needed <= present:
            findings.append(Finding("perm_combo_" + "_".join(sorted(needed)), sev, why))  # type: ignore[arg-type]
    if target_sdk is not None and target_sdk < 26:
        findings.append(Finding("old_target_sdk", "medium", f"targetSdk {target_sdk} < 26 bypasses modern runtime protections"))
    if debug_cert:
        findings.append(Finding("debug_certificate", "medium", "Signed with an Android debug certificate (not a release build)", kind="integrity"))
    return findings
