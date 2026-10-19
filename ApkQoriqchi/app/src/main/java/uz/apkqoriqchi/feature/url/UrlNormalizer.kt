package uz.apkqoriqchi.feature.url

import java.net.IDN

/** A validated http(s) URL split into the parts the heuristics need. [host] is lowercase (may be Unicode). */
data class NormalizedUrl(
    val scheme: String,
    val userInfo: String?,
    val host: String,
    val port: Int?,
    /** Everything after the authority: path, query and fragment (may be empty). */
    val rest: String,
) {
    val url: String
        get() = buildString {
            append(scheme).append("://")
            userInfo?.let { append(it).append('@') }
            append(host)
            port?.let { append(':').append(it) }
            append(rest)
        }

    /** Same URL with an IDNA (punycode) host, for sending to reputation APIs. */
    val asciiUrl: String
        get() = copy(host = asciiHost).url

    val asciiHost: String
        get() = if (host.all { it.code < 128 }) host
        else runCatching { IDN.toASCII(host, IDN.ALLOW_UNASSIGNED) }.getOrDefault(host).lowercase()

    val path: String get() = rest.substringBefore('?').substringBefore('#')
}

sealed interface UrlParseResult {
    data class Valid(val url: NormalizedUrl) : UrlParseResult
    data object Empty : UrlParseResult
    data class UnsupportedScheme(val scheme: String) : UrlParseResult
    data object Malformed : UrlParseResult
}

object UrlNormalizer {
    private val schemeRegex = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):(.*)$", RegexOption.DOT_MATCHES_ALL)

    fun parse(raw: String): UrlParseResult {
        val input = raw.filterNot { it.isWhitespace() }.trim('"', '\'', '<', '>')
        if (input.isEmpty()) return UrlParseResult.Empty

        val m = schemeRegex.find(input)
        val (scheme, afterScheme) = when {
            m == null -> "https" to input.removePrefix("//")
            m.groupValues[2].startsWith("//") -> m.groupValues[1].lowercase() to m.groupValues[2].substring(2)
            // "example.com:8080/x" looks like a scheme but is host:port.
            m.groupValues[2].firstOrNull()?.isDigit() == true && '.' in m.groupValues[1] -> "https" to input
            m.groupValues[2].firstOrNull()?.isDigit() == true && m.groupValues[1].equals("localhost", true) -> "https" to input
            else -> return UrlParseResult.UnsupportedScheme(m.groupValues[1].lowercase())
        }
        if (scheme != "http" && scheme != "https") return UrlParseResult.UnsupportedScheme(scheme)

        val authorityEnd = afterScheme.indexOfFirst { it == '/' || it == '?' || it == '#' }.let { if (it < 0) afterScheme.length else it }
        val authority = afterScheme.substring(0, authorityEnd)
        val rest = afterScheme.substring(authorityEnd)
        val at = authority.lastIndexOf('@')
        val userInfo = if (at >= 0) authority.substring(0, at) else null
        val hostPort = authority.substring(at + 1)

        val (hostRaw, portStr) = if (hostPort.startsWith("[")) {
            val close = hostPort.indexOf(']')
            if (close < 0) return UrlParseResult.Malformed
            hostPort.substring(0, close + 1) to hostPort.substring(close + 1).removePrefix(":").ifEmpty { null }
        } else {
            val colon = hostPort.lastIndexOf(':')
            if (colon >= 0) hostPort.substring(0, colon) to hostPort.substring(colon + 1).ifEmpty { null }
            else hostPort to null
        }
        val host = hostRaw.lowercase().trimEnd('.')
        if (host.isEmpty() || host.startsWith('.') || ".." in host) return UrlParseResult.Malformed
        if (host.any { it in "\\%^{}|`\"<> " }) return UrlParseResult.Malformed
        val port = portStr?.let { p -> p.toIntOrNull()?.takeIf { it in 1..65535 } ?: return UrlParseResult.Malformed }
        // A bare word like "hello" is not a URL: require a dot, IPv6 literal or localhost.
        if ('.' !in host && !host.startsWith("[") && host != "localhost") return UrlParseResult.Malformed

        return UrlParseResult.Valid(NormalizedUrl(scheme, userInfo?.ifEmpty { null }, host, port, rest))
    }
}
