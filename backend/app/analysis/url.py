"""URL parsing, normalization, minimization and local phishing heuristics.

Nothing here ever fetches the URL: all checks are lexical.
"""
import ipaddress
import re
import unicodedata
from urllib.parse import urlsplit, urlunsplit

from app.analysis.findings import Finding

MAX_URL_LENGTH = 2048
SHORTENERS = {"bit.ly", "tinyurl.com", "t.ly", "goo.gl", "is.gd", "cutt.ly", "ow.ly", "t.co", "rb.gy", "shorturl.at", "clck.ru"}
SUSPICIOUS_TLDS = {"zip", "mov", "top", "xyz", "click", "loan", "work", "gq", "tk", "ml", "cf", "ga", "rest", "cam", "icu"}
BRAND_DOMAINS = {
    "google.com", "paypal.com", "telegram.org", "apple.com", "microsoft.com", "facebook.com", "instagram.com",
    "whatsapp.com", "payme.uz", "click.uz", "uzcard.uz", "humocard.uz", "kapitalbank.uz", "gov.uz", "my.gov.uz",
}
BRAND_KEYWORDS = {"paypal", "payme", "click", "uzcard", "humo", "telegram", "google", "apple", "bank", "kapital", "whatsapp", "instagram"}
_HOMOGLYPHS = str.maketrans({"0": "o", "1": "l", "3": "e", "5": "s", "@": "a", "$": "s"})


def _skeleton(label: str) -> str:
    return label.translate(_HOMOGLYPHS).replace("rn", "m").replace("vv", "w")


class InvalidUrl(ValueError):
    pass


def normalize(raw: str) -> str:
    raw = raw.strip()
    if not raw or len(raw) > MAX_URL_LENGTH or any(c.isspace() for c in raw):
        raise InvalidUrl("URL is empty, too long, or contains whitespace")
    explicit = re.match(r"^([A-Za-z][A-Za-z0-9+.\-]*):(?!\d)", raw)
    if explicit and explicit.group(1).lower() not in ("http", "https"):
        raise InvalidUrl(f"Unsupported scheme: {explicit.group(1)}")
    if "://" not in raw:
        raw = "https://" + raw
    parts = urlsplit(raw)
    if parts.scheme.lower() not in ("http", "https"):
        raise InvalidUrl(f"Unsupported scheme: {parts.scheme}")
    if not parts.hostname:
        raise InvalidUrl("URL has no host")
    host = parts.hostname.lower().rstrip(".")
    try:
        host.encode("idna")
    except UnicodeError as e:
        raise InvalidUrl("Invalid international domain name") from e
    netloc = host
    if parts.username:
        netloc = f"{parts.username}@{netloc}"  # kept for analysis; dropped by minimize()
    try:
        port = parts.port
    except ValueError as e:
        raise InvalidUrl("Invalid port") from e
    if port:
        netloc = f"{netloc}:{port}"
    return urlunsplit((parts.scheme.lower(), netloc, parts.path or "/", parts.query, ""))


def minimize(url: str) -> str:
    """History-safe form: scheme://host[:port]/path. Drops userinfo, query and fragment."""
    p = urlsplit(url)
    host = p.hostname or ""
    if p.port:
        host = f"{host}:{p.port}"
    return urlunsplit((p.scheme, host, p.path or "/", "", ""))


def registrable_domain(host: str) -> str:
    labels = host.split(".")
    if len(labels) >= 3 and labels[-1] == "uz" and labels[-2] in {"gov", "com", "org", "net"}:
        return ".".join(labels[-3:])
    return ".".join(labels[-2:])


def levenshtein(a: str, b: str) -> int:
    prev = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        cur = [i]
        for j, cb in enumerate(b, 1):
            cur.append(min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (ca != cb)))
        prev = cur
    return prev[-1]


def _scripts(text: str) -> set[str]:
    out = set()
    for ch in text:
        if ch.isalpha():
            name = unicodedata.name(ch, "")
            out.add(name.split(" ")[0] if name else "UNKNOWN")
    return out


def lookalike_of(host: str) -> str | None:
    reg = registrable_domain(host)
    if reg in BRAND_DOMAINS:
        return None
    reg_label = reg.split(".")[0]
    normalized = _skeleton(reg_label)
    # Tokens catch brands embedded in longer hosts: "paypa1-login.xyz", "secure.g00gle-verify.top".
    tokens = [t for t in re.split(r"[.\-_]", host) if len(t) >= 4]
    for brand in sorted(BRAND_DOMAINS):
        brand_label = brand.split(".")[0]
        if len(brand_label) < 4:
            continue
        if any(t != brand_label and _skeleton(t) == brand_label for t in tokens):
            return brand
        if reg_label == brand_label:
            continue  # same name, other TLD: covered by the brand-keyword check
        # Edit distance only for longer names; short ones collide with real words (apple/apply).
        if normalized == brand_label or (len(brand_label) >= 6 and levenshtein(reg_label, brand_label) <= (1 if len(brand_label) <= 7 else 2)):
            return brand
    return None


def heuristics(url: str) -> list[Finding]:
    p = urlsplit(url)
    host = (p.hostname or "").lower()
    out: list[Finding] = []
    if p.scheme == "http":
        out.append(Finding("plain_http", "low", "Connection is not encrypted (http://)"))
    try:
        ipaddress.ip_address(host.strip("[]"))
        out.append(Finding("ip_host", "medium", f"Host is a raw IP address: {host}"))
        is_ip = True
    except ValueError:
        is_ip = False
    if p.username or p.password:
        out.append(Finding("userinfo", "high", "URL contains '@' credentials section that can disguise the real host"))
    if p.port and p.port not in (80, 443):
        out.append(Finding("odd_port", "low", f"Non-standard port {p.port}"))
    if not is_ip:
        unicode_host = host
        if "xn--" in host:
            try:
                unicode_host = host.encode().decode("idna")
            except UnicodeError:
                pass
            out.append(Finding("punycode", "medium", f"Internationalized domain: {host} → {unicode_host}"))
        scripts = _scripts(unicode_host.replace(".", ""))
        if len(scripts) > 1:
            out.append(Finding("mixed_script", "high", f"Domain mixes scripts {sorted(scripts)} (homograph risk)"))
        if host.count(".") >= 4:
            out.append(Finding("many_subdomains", "medium", f"Unusually deep subdomain chain: {host}"))
        tld = host.rsplit(".", 1)[-1]
        if tld in SUSPICIOUS_TLDS:
            out.append(Finding("suspicious_tld", "low", f"TLD .{tld} is frequently abused"))
        if registrable_domain(host) in SHORTENERS:
            out.append(Finding("shortener", "medium", "Shortened link hides its real destination; it was NOT followed"))
        brand = lookalike_of(host)
        if brand:
            out.append(Finding("lookalike", "high", f"Domain resembles {brand}"))
        elif registrable_domain(host) not in BRAND_DOMAINS:
            hits = sorted(k for k in BRAND_KEYWORDS if k in host)
            if hits:
                out.append(Finding("brand_keyword", "medium", f"Brand keyword(s) {hits} in an unrelated domain"))
    if len(url) > 200:
        out.append(Finding("long_url", "low", f"Very long URL ({len(url)} chars)"))
    if p.path.lower().endswith(".apk"):
        out.append(Finding("apk_download", "medium", "Link downloads an APK file directly"))
    return out
