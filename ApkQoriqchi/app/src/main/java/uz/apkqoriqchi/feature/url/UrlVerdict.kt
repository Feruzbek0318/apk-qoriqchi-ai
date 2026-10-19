package uz.apkqoriqchi.feature.url

import uz.apkqoriqchi.core.model.ProviderResult
import uz.apkqoriqchi.core.model.RiskLevel

/**
 * Verdict policy. There is no "safe" outcome:
 * - malicious: any Safe Browsing match or VT malicious >= 2 -> HIGH / CRITICAL
 * - suspicious: VT malicious == 1 / suspicious >= 2, or heuristics -> LOW / MEDIUM
 * - NO_DETECTIONS ("clean according to available sources") only if at least one reputation
 *   provider actually completed (Success) without a match
 * - UNKNOWN when no provider completed (no keys, offline, VT 404 ...), even if heuristics are clean.
 *   Missing data is never turned into "clean".
 */
object UrlVerdict {
    private val SEVERE_THREATS = setOf("MALWARE", "SOCIAL_ENGINEERING")

    fun combine(
        findings: List<HeuristicFinding>,
        reputation: ProviderResult<List<ThreatMatch>>?,
        vt: ProviderResult<VtUrlReport>?,
    ): RiskLevel {
        val levels = mutableListOf(UrlHeuristics.riskOf(findings))

        val threats = (reputation as? ProviderResult.Success)?.data.orEmpty().flatMap { it.threatTypes }
        if (threats.isNotEmpty()) levels += if (threats.any { it in SEVERE_THREATS }) RiskLevel.CRITICAL else RiskLevel.HIGH

        (vt as? ProviderResult.Success)?.data?.stats?.let { s ->
            levels += when {
                s.malicious >= 5 -> RiskLevel.CRITICAL
                s.malicious >= 2 -> RiskLevel.HIGH
                s.malicious == 1 || s.suspicious >= 2 -> RiskLevel.MEDIUM
                s.suspicious == 1 -> RiskLevel.LOW
                else -> RiskLevel.NO_DETECTIONS
            }
        }
        val worst = levels.maxBy { it.rank }
        val anyCompleted = reputation is ProviderResult.Success || vt is ProviderResult.Success
        return if (worst == RiskLevel.NO_DETECTIONS && !anyCompleted) RiskLevel.UNKNOWN else worst
    }

    /** True when at least one provider did not produce a result (verdict is based on partial data). */
    fun incomplete(reputation: ProviderResult<*>?, vt: ProviderResult<*>?) =
        reputation !is ProviderResult.Success || vt !is ProviderResult.Success

    private val RiskLevel.rank: Int
        get() = when (this) {
            RiskLevel.UNKNOWN -> -1
            RiskLevel.NO_DETECTIONS -> 0
            RiskLevel.LOW -> 1
            RiskLevel.MEDIUM -> 2
            RiskLevel.HIGH -> 3
            RiskLevel.CRITICAL -> 4
        }

    /** Compact English evidence list handed to the AI explainer (it answers in the UI language). */
    fun facts(
        url: NormalizedUrl,
        risk: RiskLevel,
        findings: List<HeuristicFinding>,
        reputation: ProviderResult<List<ThreatMatch>>?,
        vt: ProviderResult<VtUrlReport>?,
    ): String = buildString {
        appendLine("Scan type: URL / phishing link check")
        appendLine("URL (query removed): ${UrlPrivacy.historyTarget(url)}")
        appendLine("Verdict: $risk (never claim the link is safe)")
        appendLine("Google Safe Browsing: " + when (reputation) {
            is ProviderResult.Success -> if (reputation.data.isEmpty()) "no match" else reputation.data.flatMap { it.threatTypes }.distinct().joinToString()
            else -> statusText(reputation)
        })
        append("VirusTotal: ")
        when (vt) {
            is ProviderResult.Success -> with(vt.data) {
                appendLine("${stats.malicious} malicious, ${stats.suspicious} suspicious of ${stats.total} engines")
                if (hits.isNotEmpty()) appendLine("Flagged by: " + hits.take(6).joinToString { "${it.engine} (${it.result ?: it.category})" })
                if (categories.isNotEmpty()) appendLine("Categories: ${categories.take(4).joinToString()}")
                finalUrl?.let { UrlPrivacy.minimize(it) }?.takeIf { it != UrlPrivacy.historyTarget(url) }?.let { appendLine("Final URL after redirects: $it") }
                this.reputation?.let { appendLine("VT community reputation: $it") }
            }
            else -> appendLine(statusText(vt))
        }
        if (findings.isEmpty()) appendLine("Local heuristics: nothing notable")
        else findings.forEach { appendLine("Heuristic ${it.type} [${it.severity}]" + if (it.type == HeuristicType.AT_IN_AUTHORITY) "" else ": ${it.evidence}") }
    }.trim()

    private fun statusText(r: ProviderResult<*>?) = when (r) {
        null -> "pending"
        ProviderResult.NotFound -> "no existing report (unknown, not clean)"
        ProviderResult.NotConfigured -> "not configured"
        is ProviderResult.Failed -> "failed (${r.reason})"
        is ProviderResult.Success -> "ok"
    }
}
