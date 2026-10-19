package uz.apkqoriqchi.feature.url

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test
import uz.apkqoriqchi.core.model.ProviderResult
import uz.apkqoriqchi.core.model.RiskLevel

private fun n(s: String) = (UrlNormalizer.parse(s) as UrlParseResult.Valid).url
private fun types(s: String) = UrlHeuristics.analyze(n(s)).map { it.type }.toSet()

class UrlNormalizerTest {
    @Test fun addsHttpsAndLowercasesHost() {
        val u = n("  Example.COM/Path?A=1 ")
        assertThat(u.url).isEqualTo("https://example.com/Path?A=1")
    }

    @Test fun keepsHttpAndPort() {
        assertThat(n("http://ex.com:8080/x").url).isEqualTo("http://ex.com:8080/x")
        assertThat(n("ex.com:8080").port).isEqualTo(8080)
    }

    @Test fun rejectsNonHttpSchemes() {
        assertThat(UrlNormalizer.parse("javascript:alert(1)")).isEqualTo(UrlParseResult.UnsupportedScheme("javascript"))
        assertThat(UrlNormalizer.parse("ftp://x.com")).isEqualTo(UrlParseResult.UnsupportedScheme("ftp"))
        assertThat(UrlNormalizer.parse("intent://x#Intent;end")).isEqualTo(UrlParseResult.UnsupportedScheme("intent"))
    }

    @Test fun rejectsEmptyAndMalformed() {
        assertThat(UrlNormalizer.parse("   ")).isEqualTo(UrlParseResult.Empty)
        assertThat(UrlNormalizer.parse("hello")).isEqualTo(UrlParseResult.Malformed)
        assertThat(UrlNormalizer.parse("https://ex.com:99999")).isEqualTo(UrlParseResult.Malformed)
    }

    @Test fun splitsUserInfo() {
        val u = n("https://paypal.com@evil.xyz/login")
        assertThat(u.host).isEqualTo("evil.xyz")
        assertThat(u.userInfo).isEqualTo("paypal.com")
    }
}

class UrlHeuristicsTest {
    @Test fun cleanUrlHasNoFindings() {
        assertThat(types("https://www.google.com/search?q=x")).isEmpty()
        assertThat(types("https://kapitalbank.uz")).isEmpty()
        assertThat(types("https://my.click.uz/pay")).isEmpty()
    }

    @Test fun plainHttpAndPort() {
        assertThat(types("http://example.com:8081/")).containsExactly(HeuristicType.PLAIN_HTTP, HeuristicType.NON_STANDARD_PORT)
    }

    @Test fun ipHost() {
        assertThat(types("http://185.199.110.23/login")).contains(HeuristicType.IP_HOST)
        assertThat(types("http://[::1]/")).contains(HeuristicType.IP_HOST)
    }

    @Test fun atSign() = assertThat(types("https://google.com@phish.example/")).contains(HeuristicType.AT_IN_AUTHORITY)

    @Test fun punycodeAndMixedScript() {
        // "аpple.com" with a Cyrillic "а" mixed with Latin.
        val t = types("https://xn--pple-43d.com/")
        assertThat(t).containsAtLeast(HeuristicType.PUNYCODE, HeuristicType.MIXED_SCRIPT, HeuristicType.LOOKALIKE_DOMAIN)
        assertThat(types("https://аpple.com/")).contains(HeuristicType.MIXED_SCRIPT)
    }

    @Test fun shortenerSubdomainsLength() {
        assertThat(types("https://bit.ly/3Hy277a")).contains(HeuristicType.SHORTENER)
        assertThat(types("https://a.b.c.d.example.com/")).contains(HeuristicType.MANY_SUBDOMAINS)
        assertThat(types("https://ex.com/" + "a".repeat(200))).contains(HeuristicType.LONG_URL)
    }

    @Test fun suspiciousTldBrandAndApk() {
        val f = UrlHeuristics.analyze(n("https://payme-bonus.top/app.apk"))
        assertThat(f.map { it.type }).containsAtLeast(HeuristicType.SUSPICIOUS_TLD, HeuristicType.BRAND_IMPERSONATION, HeuristicType.APK_DOWNLOAD)
        assertThat(f.first { it.type == HeuristicType.APK_DOWNLOAD }.evidence).isEqualTo("app.apk")
        assertThat(types("https://dl.example.com/get?file=update.apk")).contains(HeuristicType.APK_DOWNLOAD)
        assertThat(types("https://bank.secure-login.com/")).contains(HeuristicType.BRAND_IMPERSONATION)
    }

    @Test fun lookalikes() {
        assertThat(UrlHeuristics.lookalikeOf("paypa1.com")).isEqualTo("paypal.com")
        assertThat(UrlHeuristics.lookalikeOf("g00gle.com")).isEqualTo("google.com")
        assertThat(UrlHeuristics.lookalikeOf("rnicrosoft.com")).isEqualTo("microsoft.com")
        assertThat(UrlHeuristics.lookalikeOf("telegrarn.org")).isEqualTo("telegram.org")
        assertThat(UrlHeuristics.lookalikeOf("instagran.com")).isEqualTo("instagram.com")
        // Disguised brand embedded in a longer host.
        assertThat(UrlHeuristics.lookalikeOf("paypa1-login.xyz")).isEqualTo("paypal.com")
        assertThat(UrlHeuristics.lookalikeOf("secure.g00gle-verify.top")).isEqualTo("google.com")
        assertThat(UrlHeuristics.lookalikeOf("paypal-help.net")).isNull()
        assertThat(UrlHeuristics.lookalikeOf("kapitalbnk.uz")).isEqualTo("kapitalbank.uz")
        assertThat(UrlHeuristics.lookalikeOf("humocrad.uz")).isEqualTo("humocard.uz")
        assertThat(UrlHeuristics.lookalikeOf("c1ick.uz")).isEqualTo("click.uz")
        assertThat(UrlHeuristics.lookalikeOf("аррӏе.com")).isEqualTo("apple.com") // all-Cyrillic
    }

    @Test fun lookalikeIgnoresOfficialAndUnrelated() {
        assertThat(UrlHeuristics.lookalikeOf("mail.google.com")).isNull()
        assertThat(UrlHeuristics.lookalikeOf("payme.uz")).isNull()
        assertThat(UrlHeuristics.lookalikeOf("doodle.com")).isNull()
        assertThat(UrlHeuristics.lookalikeOf("apply.com")).isNull()
        assertThat(UrlHeuristics.lookalikeOf("wikipedia.org")).isNull()
        assertThat(UrlHeuristics.levenshtein("kitten", "sitting")).isEqualTo(3)
    }

    @Test fun heuristicsNeverExceedMedium() {
        val f = UrlHeuristics.analyze(n("http://paypal.com@1.2.3.4:8080/x.apk"))
        assertThat(UrlHeuristics.riskOf(f)).isEqualTo(RiskLevel.MEDIUM)
        assertThat(UrlHeuristics.riskOf(UrlHeuristics.analyze(n("http://example.com")))).isEqualTo(RiskLevel.LOW)
    }
}

class UrlVerdictTest {
    private fun vt(mal: Int, sus: Int = 0) = ProviderResult.Success(VtUrlReport(VtStats(malicious = mal, suspicious = sus, harmless = 60, undetected = 20)))
    private val sbClean = ProviderResult.Success(emptyList<ThreatMatch>())

    @Test fun vtBoundaries() {
        assertThat(UrlVerdict.combine(emptyList(), sbClean, vt(0))).isEqualTo(RiskLevel.NO_DETECTIONS)
        assertThat(UrlVerdict.combine(emptyList(), sbClean, vt(1))).isEqualTo(RiskLevel.MEDIUM)
        assertThat(UrlVerdict.combine(emptyList(), sbClean, vt(2))).isEqualTo(RiskLevel.HIGH)
        assertThat(UrlVerdict.combine(emptyList(), sbClean, vt(5))).isEqualTo(RiskLevel.CRITICAL)
    }

    @Test fun safeBrowsingMatch() {
        val phishing = ProviderResult.Success(listOf(ThreatMatch("u", listOf("SOCIAL_ENGINEERING"))))
        val pha = ProviderResult.Success(listOf(ThreatMatch("u", listOf("POTENTIALLY_HARMFUL_APPLICATION"))))
        assertThat(UrlVerdict.combine(emptyList(), phishing, ProviderResult.NotFound)).isEqualTo(RiskLevel.CRITICAL)
        assertThat(UrlVerdict.combine(emptyList(), pha, vt(0))).isEqualTo(RiskLevel.HIGH)
    }

    @Test fun noProviderCompletedIsUnknownNotClean() {
        assertThat(UrlVerdict.combine(emptyList(), ProviderResult.Failed(FailCode.NETWORK), ProviderResult.Failed(FailCode.QUOTA)))
            .isEqualTo(RiskLevel.UNKNOWN)
        assertThat(UrlVerdict.combine(emptyList(), ProviderResult.NotConfigured, ProviderResult.NotFound)).isEqualTo(RiskLevel.UNKNOWN)
        // Heuristic signals still surface without providers.
        val f = UrlHeuristics.analyze(n("https://bit.ly/x"))
        assertThat(UrlVerdict.combine(f, ProviderResult.NotConfigured, ProviderResult.NotConfigured)).isEqualTo(RiskLevel.LOW)
    }

    @Test fun oneCompletedProviderAllowsNoDetections() {
        assertThat(UrlVerdict.combine(emptyList(), sbClean, ProviderResult.NotFound)).isEqualTo(RiskLevel.NO_DETECTIONS)
        assertThat(UrlVerdict.incomplete(sbClean, ProviderResult.NotFound)).isTrue()
    }
}

class UrlPrivacyTest {
    @Test fun historyTargetDropsQueryFragmentUserInfo() {
        assertThat(UrlPrivacy.historyTarget(n("https://user:pw@Ex.com:8443/a/b?token=secret#frag"))).isEqualTo("https://ex.com/a/b")
        assertThat(UrlPrivacy.minimize("http://ex.com/?q=1")).isEqualTo("http://ex.com/")
        assertThat(UrlPrivacy.minimize("not a url")).isNull()
    }
}

class UrlProviderParsingTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun vtUrlIdIsUnpaddedBase64Url() {
        assertThat(VtUrlClient.urlId("http://www.example.com/")).isEqualTo("aHR0cDovL3d3dy5leGFtcGxlLmNvbS8")
        assertThat(VtUrlClient.urlId("https://a.com/?x=>>")).doesNotContain("=")
    }

    @Test fun safeBrowsingParse() {
        assertThat(SafeBrowsingProvider.parse(ByteArray(0))).isEmpty()
        // cache_duration only (field 2 { seconds: 300 }) = no match
        assertThat(SafeBrowsingProvider.parse(hex("12030 8ac02"))).isEmpty()
        // Real v5 response bytes for Google's phishing test page (captured 2026-10-18).
        val real = hex("0a310a2c7465737473616665627 26f7773696e672e617070 73706f742e636f6d2f732f7068697368696e672e68746d6c12010212030 8ac02")
        assertThat(SafeBrowsingProvider.parse(real))
            .containsExactly(ThreatMatch("testsafebrowsing.appspot.com/s/phishing.html", listOf("SOCIAL_ENGINEERING")))
        // Two packed threat types: MALWARE(1), UNWANTED_SOFTWARE(3)
        assertThat(SafeBrowsingProvider.parse(hex("0a090a03782f7912020103")))
            .containsExactly(ThreatMatch("x/y", listOf("MALWARE", "UNWANTED_SOFTWARE")))
    }

    @Test fun webRiskParse() {
        assertThat(WebRiskProvider.parse(json, "u", "{}")).isEmpty()
        assertThat(WebRiskProvider.parse(json, "u", """{"threat":{"threatTypes":["MALWARE"],"expireTime":"2026-01-01T00:00:00Z"}}"""))
            .containsExactly(ThreatMatch("u", listOf("MALWARE")))
    }

    @Test fun vtReportParse() {
        val body = """{"data":{"id":"x","type":"url","attributes":{
            "last_analysis_stats":{"malicious":3,"suspicious":1,"harmless":60,"undetected":25,"timeout":0},
            "last_analysis_results":{"EngA":{"category":"malicious","result":"phishing","engine_name":"EngA"},
                                     "EngB":{"category":"harmless","result":"clean","engine_name":"EngB"},
                                     "EngC":{"category":"suspicious","result":"suspicious","engine_name":"EngC"}},
            "categories":{"Forcepoint":"phishing","Sophos":"phishing"},
            "last_final_url":"https://final.example/","last_analysis_date":1700000000,"reputation":-12}}}"""
        val r = VtUrlClient.parseUrlReport(json, body)
        assertThat(r.stats.malicious).isEqualTo(3)
        assertThat(r.stats.total).isEqualTo(89)
        assertThat(r.hits.map { it.engine }).containsExactly("EngA", "EngC").inOrder()
        assertThat(r.categories).containsExactly("phishing")
        assertThat(r.finalUrl).isEqualTo("https://final.example/")
        assertThat(r.reputation).isEqualTo(-12)
    }

    @Test fun vtAnalysisPendingThenCompleted() {
        assertThat(VtUrlClient.parseAnalysis(json, """{"data":{"attributes":{"status":"queued"}}}""")).isNull()
        val done = VtUrlClient.parseAnalysis(json, """{"data":{"attributes":{"status":"completed","stats":{"malicious":2},"results":{}}}}""")
        assertThat(done!!.stats.malicious).isEqualTo(2)
    }
}

private fun hex(s: String): ByteArray = s.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
