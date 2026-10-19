package uz.apkqoriqchi.feature.url

/** History minimization: only scheme + host + path are persisted; userinfo, port, query and fragment are dropped. */
object UrlPrivacy {
    fun historyTarget(u: NormalizedUrl): String = "${u.scheme}://${u.host}${u.path}"

    /** Same for an arbitrary URL string (e.g. VT's last_final_url); null if it cannot be parsed. */
    fun minimize(url: String): String? = (UrlNormalizer.parse(url) as? UrlParseResult.Valid)?.url?.let(::historyTarget)
}
