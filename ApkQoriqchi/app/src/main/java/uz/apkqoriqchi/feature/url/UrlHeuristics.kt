package uz.apkqoriqchi.feature.url

import java.net.IDN
import kotlinx.serialization.Serializable
import uz.apkqoriqchi.core.model.RiskLevel

enum class Severity { LOW, MEDIUM, HIGH }

enum class HeuristicType(val severity: Severity) {
    PLAIN_HTTP(Severity.LOW),
    IP_HOST(Severity.HIGH),
    PUNYCODE(Severity.MEDIUM),
    MIXED_SCRIPT(Severity.HIGH),
    AT_IN_AUTHORITY(Severity.HIGH),
    MANY_SUBDOMAINS(Severity.MEDIUM),
    LONG_URL(Severity.LOW),
    SHORTENER(Severity.MEDIUM),
    SUSPICIOUS_TLD(Severity.MEDIUM),
    BRAND_IMPERSONATION(Severity.HIGH),
    LOOKALIKE_DOMAIN(Severity.HIGH),
    APK_DOWNLOAD(Severity.HIGH),
    NON_STANDARD_PORT(Severity.MEDIUM),
}

/** One local finding. [evidence] is the raw fragment of the URL that triggered it (not localized). */
@Serializable
data class HeuristicFinding(val type: HeuristicType, val evidence: String) {
    val severity: Severity get() = type.severity
}

/** Offline, key-less URL checks. Pure Kotlin so it can be unit tested on the JVM. */
object UrlHeuristics {
    const val LONG_URL_THRESHOLD = 120
    const val MAX_LABELS = 4

    val SHORTENERS = setOf(
        "bit.ly", "tinyurl.com", "t.ly", "goo.gl", "is.gd", "cutt.ly", "ow.ly", "buff.ly", "rb.gy", "t.co",
        "tiny.cc", "shorturl.at", "rebrand.ly", "bl.ink", "v.gd", "s.id", "clck.ru", "u.to", "qps.ru", "lnkd.in",
        "surl.li", "shorturl.asia", "x.gd", "short.io", "tny.im", "bitly.com", "trib.al",
    )

    val SUSPICIOUS_TLDS = setOf(
        "zip", "mov", "top", "xyz", "click", "link", "work", "gq", "tk", "ml", "cf", "ga", "rest", "cam", "icu",
        "buzz", "monster", "country", "kim", "loan", "win", "bid", "download", "racing", "review", "stream",
        "support", "quest", "sbs", "cfd", "lat", "beauty", "hair", "autos", "boats", "mom", "live",
    )

    /** Brand keyword -> official registrable domains. [strict] brands must match a whole host token. */
    private data class Brand(val keyword: String, val official: Set<String>, val strict: Boolean = false)

    private val BRANDS = listOf(
        Brand("paypal", setOf("paypal.com", "paypal.me", "paypalobjects.com")),
        Brand("payme", setOf("payme.uz", "paycom.uz")),
        Brand("click", setOf("click.uz"), strict = true),
        Brand("uzcard", setOf("uzcard.uz")),
        Brand("humo", setOf("humocard.uz", "humo.uz"), strict = true),
        Brand("telegram", setOf("telegram.org", "telegram.me", "t.me", "telegram.dog", "telegra.ph")),
        Brand("google", setOf("google.com", "google.uz", "google.ru", "googleapis.com", "googleusercontent.com", "goo.gl", "youtube.com", "gstatic.com")),
        Brand("apple", setOf("apple.com", "icloud.com", "apple.news"), strict = true),
        Brand("icloud", setOf("icloud.com", "apple.com")),
        Brand("facebook", setOf("facebook.com", "fb.com", "fbcdn.net", "meta.com")),
        Brand("instagram", setOf("instagram.com", "cdninstagram.com")),
        Brand("whatsapp", setOf("whatsapp.com", "whatsapp.net", "wa.me")),
        Brand("microsoft", setOf("microsoft.com", "live.com", "office.com", "microsoftonline.com")),
        Brand("netflix", setOf("netflix.com")),
        Brand("steam", setOf("steampowered.com", "steamcommunity.com"), strict = true),
        Brand("olx", setOf("olx.uz", "olx.ua", "olx.kz", "olx.pl")),
        Brand("uzum", setOf("uzum.uz", "uzumbank.uz")),
        Brand("kapitalbank", setOf("kapitalbank.uz")),
        Brand("gosuslugi", setOf("gosuslugi.ru")),
        Brand("sberbank", setOf("sberbank.ru", "sber.ru")),
        Brand("bank", emptySet(), strict = true),
    )

    /** Second-level labels under which registrations happen (approximation of the Public Suffix List). */
    private val SECOND_LEVEL = setOf("co", "com", "net", "org", "gov", "edu", "ac", "msk", "spb")

    private val ipv4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
    private val numericHost = Regex("""^(0x[0-9a-f]+|\d+)$""")

    fun analyze(u: NormalizedUrl): List<HeuristicFinding> = buildList {
        val host = u.host
        val ascii = u.asciiHost
        val isIp = host.startsWith("[") || ipv4.matches(host) || numericHost.matches(host)

        if (u.scheme == "http") add(HeuristicFinding(HeuristicType.PLAIN_HTTP, "http://$host"))
        if (isIp) add(HeuristicFinding(HeuristicType.IP_HOST, host))
        if (u.userInfo != null) add(HeuristicFinding(HeuristicType.AT_IN_AUTHORITY, "${u.userInfo}@$host"))

        if (!isIp) {
            val labels = host.split('.')
            // Decode punycode first so "xn--..." hosts are checked as the user would see them.
            val unicodeLabels = runCatching { IDN.toUnicode(ascii, IDN.ALLOW_UNASSIGNED) }.getOrDefault(host).lowercase().split('.')
            unicodeLabels.firstOrNull { mixedScript(it) }?.let { add(HeuristicFinding(HeuristicType.MIXED_SCRIPT, "$it → ${scriptsOf(it).joinToString("+")}")) }
            if (ascii.split('.').any { it.startsWith("xn--") }) {
                val unicode = runCatching { IDN.toUnicode(ascii) }.getOrDefault(host)
                add(HeuristicFinding(HeuristicType.PUNYCODE, if (unicode != ascii) "$ascii ($unicode)" else ascii))
            }
            if (labels.size > MAX_LABELS) add(HeuristicFinding(HeuristicType.MANY_SUBDOMAINS, "$host (${labels.size})"))

            val domain = registrableDomain(ascii)
            if (domain in SHORTENERS || ascii in SHORTENERS) add(HeuristicFinding(HeuristicType.SHORTENER, domain))
            val tld = labels.last()
            if (tld in SUSPICIOUS_TLDS) add(HeuristicFinding(HeuristicType.SUSPICIOUS_TLD, ".$tld"))
            brandImpersonation(ascii, domain)?.let { add(HeuristicFinding(HeuristicType.BRAND_IMPERSONATION, it)) }
            lookalikeOf(unicodeLabels.joinToString("."))?.let { add(HeuristicFinding(HeuristicType.LOOKALIKE_DOMAIN, "${registrableDomain(unicodeLabels.joinToString("."))} ≈ $it")) }
        }

        if (u.url.length > LONG_URL_THRESHOLD) add(HeuristicFinding(HeuristicType.LONG_URL, "${u.url.length}"))
        val path = u.path.lowercase()
        val apk = path.substringAfterLast('/').takeIf { it.endsWith(".apk") }
            ?: Regex("""[^/?&=]+\.apk\b""").find(u.rest.substringAfter('?', "").lowercase())?.value
        if (apk != null) add(HeuristicFinding(HeuristicType.APK_DOWNLOAD, apk))
        if (u.port != null && u.port != 80 && u.port != 443) add(HeuristicFinding(HeuristicType.NON_STANDARD_PORT, ":${u.port}"))
    }

    /** Heuristics alone can raise the verdict to LOW or MEDIUM, never higher. */
    fun riskOf(findings: List<HeuristicFinding>): RiskLevel = when {
        findings.isEmpty() -> RiskLevel.NO_DETECTIONS
        findings.any { it.severity == Severity.HIGH } || findings.count { it.severity >= Severity.MEDIUM } >= 2 || findings.size >= 3 -> RiskLevel.MEDIUM
        else -> RiskLevel.LOW
    }

    fun registrableDomain(host: String): String {
        val labels = host.split('.')
        if (labels.size <= 2) return host
        val take = if (labels[labels.size - 2] in SECOND_LEVEL && labels.last().length == 2) 3 else 2
        return labels.takeLast(take).joinToString(".")
    }

    private fun brandImpersonation(host: String, domain: String): String? {
        val tokens = host.split('.', '-', '_')
        return BRANDS.firstOrNull { b ->
            val official = b.official.any { host == it || host.endsWith(".$it") }
            if (official) return@firstOrNull false
            if (b.official.isEmpty()) {
                // Generic keyword: only suspicious outside the registrable domain (e.g. "bank.secure-login.xyz").
                host.removeSuffix(domain).split('.', '-').any { it == b.keyword }
            } else if (b.strict) tokens.any { it == b.keyword } else b.keyword in host
        }?.let { "${it.keyword} → $host" }
    }

    /** Popular domains people get phished with; compared by registrable domain. */
    val POPULAR_DOMAINS = listOf(
        "google.com", "paypal.com", "telegram.org", "apple.com", "microsoft.com", "facebook.com", "instagram.com",
        "whatsapp.com", "youtube.com", "netflix.com", "amazon.com", "icloud.com", "payme.uz", "click.uz", "uzcard.uz",
        "humocard.uz", "kapitalbank.uz", "uzum.uz", "olx.uz", "my.gov.uz", "gosuslugi.ru", "sberbank.ru",
    )

    /** Cyrillic/Greek confusables and digit tricks folded to Latin ("0"→o, "1"→l, "rn"→m, ...). */
    private val CONFUSABLES = mapOf(
        'а' to "a", 'е' to "e", 'о' to "o", 'р' to "p", 'с' to "c", 'у' to "y", 'х' to "x", 'і' to "i", 'ӏ' to "l",
        'ј' to "j", 'ѕ' to "s", 'ԁ' to "d", 'һ' to "h", 'ԛ' to "q", 'ԝ' to "w", 'κ' to "k", 'ο' to "o", 'α' to "a",
        '0' to "o", '1' to "l", '3' to "e", '5' to "s", '7' to "t", '@' to "a", '$' to "s",
    )

    fun skeleton(s: String): String =
        s.lowercase().map { CONFUSABLES[it] ?: it.toString() }.joinToString("").replace("rn", "m").replace("vv", "w")

    /** Returns the popular domain [host] imitates, or null. Official domains and their subdomains never match. */
    fun lookalikeOf(host: String): String? {
        val h = host.lowercase().trimEnd('.')
        if (POPULAR_DOMAINS.any { h == it || h.endsWith(".$it") }) return null
        if (BRANDS.any { b -> b.official.any { h == it || h.endsWith(".$it") } }) return null
        val domain = registrableDomain(h)
        val label = domain.substringBefore('.')
        val skel = skeleton(label)
        // Tokens catch disguised brands embedded in longer hosts, e.g. "paypa1-login.xyz" or "secure.g00gle-verify.top".
        val tokens = h.split('.', '-', '_').filter { it.length >= 4 }
        return POPULAR_DOMAINS.firstOrNull { p ->
            val brand = registrableDomain(p).substringBefore('.')
            when {
                tokens.any { it != brand && skeleton(it) == skeleton(brand) } -> true
                label == brand -> false // same name, other TLD: handled by the brand keyword check
                skel == skeleton(brand) -> true
                // Edit distance only for longer names: short ones ("apple"/"apply") collide with real words.
                brand.length >= 6 -> minOf(levenshtein(label, brand), levenshtein(skel, skeleton(brand))) <= maxEdits(brand)
                else -> false
            }
        }
    }

    /** ≤2 edits for names of 8+ letters, ≤1 for 6–7 letters (avoids e.g. google ≈ doodle). */
    private fun maxEdits(brand: String) = if (brand.length >= 8) 2 else 1

    fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1).also { it[0] = i }
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            prev = cur
        }
        return prev[b.length]
    }

    private fun scriptsOf(label: String): Set<Character.UnicodeScript> = label.codePoints().toArray()
        .filter { Character.isLetter(it) }
        .map { Character.UnicodeScript.of(it) }
        .filter { it != Character.UnicodeScript.COMMON && it != Character.UnicodeScript.INHERITED }
        .toSet()

    private fun mixedScript(label: String) = label.any { it.code >= 128 } && scriptsOf(label).size > 1
}
