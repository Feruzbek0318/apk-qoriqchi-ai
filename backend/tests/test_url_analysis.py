import pytest

from app.analysis.url import InvalidUrl, heuristics, lookalike_of, minimize, normalize


@pytest.mark.parametrize("raw,expected", [
    ("example.com", "https://example.com/"),
    ("  HTTPS://Example.COM/a?b=1  ", "https://example.com/a?b=1"),
    ("http://example.com:8080/x", "http://example.com:8080/x"),
])
def test_normalize(raw, expected):
    assert normalize(raw) == expected


@pytest.mark.parametrize("raw", ["javascript:alert(1)", "ftp://x.com", "", "http://", "http://exa mple.com", "a" * 3000])
def test_normalize_rejects(raw):
    with pytest.raises(InvalidUrl):
        normalize(raw)


def test_minimize_drops_query_fragment_userinfo():
    assert minimize("https://user:pw@bank.uz/login?token=SECRET#frag") == "https://bank.uz/login"


@pytest.mark.parametrize("host,brand", [
    ("paypa1.com", "paypal.com"), ("g00gle.com", "google.com"), ("telegrarn.org", "telegram.org"),
    ("paypa1-login.xyz", "paypal.com"), ("secure.g00gle-verify.top", "google.com"),
])
def test_lookalikes(host, brand):
    assert lookalike_of(host) == brand


@pytest.mark.parametrize("host", ["paypal.com", "google.com", "apply.com", "doodle.com", "my.gov.uz"])
def test_not_lookalikes(host):
    assert lookalike_of(host) is None


def codes(url):
    return {f.code for f in heuristics(url)}


def test_heuristic_signals():
    assert "ip_host" in codes("http://192.168.1.1/login")
    assert "userinfo" in codes("https://google.com@evil.com/")
    assert "punycode" in codes("https://xn--pypal-4ve.com/")
    assert "mixed_script" in codes("https://xn--pypal-4ve.com/")  # Cyrillic 'а' in "pаypal"
    assert "shortener" in codes("https://bit.ly/abc")
    assert "apk_download" in codes("https://files.example.com/app.apk")
    assert "brand_keyword" in codes("https://payme-bonus.xyz/")
    assert codes("https://www.google.com/") == set()
