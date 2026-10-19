import hashlib
import io
import zipfile

import pytest

from app.analysis.apk import (InvalidApk, categorize_permissions, inspect_zip, permission_findings,
                              stream_to_file, validate_name_and_mime)


def make_apk(entries=("AndroidManifest.xml", "classes.dex", "META-INF/CERT.RSA")) -> bytes:
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as z:
        for name in entries:
            z.writestr(name, b"x")
    return buf.getvalue()


def test_streaming_sha256_matches_hashlib(tmp_path):
    data = make_apk() + b"\0" * 3_000_000  # > 1 chunk
    sha, size = stream_to_file(io.BytesIO(data), tmp_path / "a.apk", max_bytes=10 * 1024 * 1024)
    assert sha == hashlib.sha256(data).hexdigest()
    assert size == len(data)


def test_rejects_non_zip(tmp_path):
    with pytest.raises(InvalidApk, match="magic"):
        stream_to_file(io.BytesIO(b"MZ\x90\x00 not an apk"), tmp_path / "a.apk", max_bytes=1024)


def test_rejects_oversize(tmp_path):
    with pytest.raises(InvalidApk, match="limit"):
        stream_to_file(io.BytesIO(make_apk() + b"\0" * 5000), tmp_path / "a.apk", max_bytes=1000)


def test_rejects_empty(tmp_path):
    with pytest.raises(InvalidApk):
        stream_to_file(io.BytesIO(b""), tmp_path / "a.apk", max_bytes=1000)


@pytest.mark.parametrize("name,mime,ok", [
    ("app.apk", "application/vnd.android.package-archive", True),
    ("app.apk", "application/octet-stream", True),
    ("app.exe", "application/octet-stream", False),
    ("app.apk", "text/html", False),
])
def test_name_and_mime(name, mime, ok):
    if ok:
        validate_name_and_mime(name, mime)
    else:
        with pytest.raises(InvalidApk):
            validate_name_and_mime(name, mime)


def test_inspect_zip(tmp_path):
    p = tmp_path / "a.apk"
    p.write_bytes(make_apk(("AndroidManifest.xml", "classes.dex", "classes2.dex", "lib/arm64-v8a/libx.so", "META-INF/CERT.RSA")))
    z = inspect_zip(p)
    assert z.has_manifest and z.dex_files == 2 and z.native_abis == ["arm64-v8a"] and z.v1_signature_files


def test_inspect_corrupt(tmp_path):
    p = tmp_path / "a.apk"
    p.write_bytes(b"PK\x03\x04garbage")
    with pytest.raises(InvalidApk):
        inspect_zip(p)


def test_permission_categories_explain_context():
    cats = categorize_permissions(["android.permission.READ_SMS", "android.permission.CAMERA", "com.example.CUSTOM"])
    assert {c["category"] for c in cats} == {"sms", "camera"}
    assert all(c["legitimate_use"] and c["suspicious_use"] for c in cats)


def test_single_permission_is_not_high_severity():
    f = permission_findings(["android.permission.READ_SMS"])
    assert all(x.severity != "high" for x in f)


def test_banking_trojan_combo_is_high():
    f = permission_findings(["android.permission.READ_SMS", "android.permission.BIND_ACCESSIBILITY_SERVICE"])
    assert any(x.code.startswith("perm_combo") and x.severity == "high" for x in f)


def test_old_target_and_debug_cert():
    codes = {x.code for x in permission_findings([], target_sdk=22, debug_cert=True)}
    assert {"old_target_sdk", "debug_certificate"} <= codes
