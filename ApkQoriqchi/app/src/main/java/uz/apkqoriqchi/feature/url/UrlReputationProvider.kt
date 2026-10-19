package uz.apkqoriqchi.feature.url

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import java.io.InterruptedIOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import uz.apkqoriqchi.core.data.ApiKey
import uz.apkqoriqchi.core.data.SettingsRepository
import uz.apkqoriqchi.core.model.ProviderResult

/** A threat list match. Success(emptyList()) means the URL is on no list. */
@Serializable
data class ThreatMatch(val url: String, val threatTypes: List<String>)

/** Google URL reputation lookup. Safe Browsing (non-commercial) is the default; Web Risk is the commercial variant. */
interface UrlReputationProvider {
    val displayName: String
    suspend fun check(url: String): ProviderResult<List<ThreatMatch>>
}

/** Failure reason codes shared by URL providers; the UI maps them to localized text. */
object FailCode {
    const val AUTH = "auth"
    const val QUOTA = "quota"
    const val NETWORK = "network"
    const val TIMEOUT = "timeout"
    const val PARSE = "parse"
    fun http(code: Int) = "http_$code"
}

/** Runs a blocking OkHttp request off the main thread and maps common HTTP errors. Keys travel in headers only. */
internal suspend fun <T> OkHttpClient.fetch(request: Request, onOk: (Response) -> ProviderResult<T>): ProviderResult<T> =
    withContext(Dispatchers.IO) {
        try {
            newCall(request).execute().use { r ->
                when (r.code) {
                    in 200..299 -> runCatching { onOk(r) }.getOrElse { ProviderResult.Failed(FailCode.PARSE, retryable = false) }
                    401, 403 -> ProviderResult.Failed(FailCode.AUTH, retryable = false)
                    429 -> ProviderResult.Failed(FailCode.QUOTA)
                    404 -> ProviderResult.NotFound
                    else -> ProviderResult.Failed(FailCode.http(r.code))
                }
            }
        } catch (e: InterruptedIOException) { // includes SocketTimeoutException
            ProviderResult.Failed(FailCode.TIMEOUT)
        } catch (e: IOException) { // UnknownHostException, ConnectException, ...: offline
            ProviderResult.Failed(FailCode.NETWORK)
        }
    }

@Singleton
class SafeBrowsingProvider @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val settings: SettingsRepository,
) : UrlReputationProvider {
    override val displayName = "Google Safe Browsing"

    override suspend fun check(url: String): ProviderResult<List<ThreatMatch>> {
        val key = settings.apiKey(ApiKey.SAFE_BROWSING) ?: return ProviderResult.NotConfigured
        val httpUrl = ENDPOINT.toHttpUrl().newBuilder().addQueryParameter("urls", url).build()
        // Key goes in X-Goog-Api-Key (not ?key=) so the debug request-line logger never prints it.
        val req = Request.Builder().url(httpUrl).header("X-Goog-Api-Key", key).get().build()
        return client.fetch(req) { ProviderResult.Success(parse(it.body.bytes())) }
    }

    companion object {
        const val ENDPOINT = "https://safebrowsing.googleapis.com/v5/urls:search"

        /** v5 ThreatType enum numbers (google.security.safebrowsing.v5.ThreatType). */
        private val THREAT_TYPES = mapOf(
            1 to "MALWARE", 2 to "SOCIAL_ENGINEERING", 3 to "UNWANTED_SOFTWARE", 4 to "POTENTIALLY_HARMFUL_APPLICATION",
        )

        /**
         * Parses a v5 urls:search response. v5 only returns protobuf (JSON output is rejected with
         * "Unsupported Output Format"): SearchUrlsResponse { repeated ThreatUrl threats = 1; Duration cache_duration = 2; }
         * ThreatUrl { string url = 1; repeated ThreatType threat_types = 2; }. Empty body means no match.
         */
        fun parse(body: ByteArray): List<ThreatMatch> = Proto(body).fields().filter { it.first == 1 }.map { (_, value) ->
            var url = ""
            val types = mutableListOf<String>()
            Proto(value as ByteArray).fields().forEach { (field, v) ->
                when {
                    field == 1 && v is ByteArray -> url = v.decodeToString()
                    field == 2 && v is ByteArray -> Proto(v).varints().forEach { types += THREAT_TYPES[it.toInt()] ?: "THREAT_$it" }
                    field == 2 && v is Long -> types += THREAT_TYPES[v.toInt()] ?: "THREAT_$v"
                }
            }
            ThreatMatch(url, types)
        }
    }
}

/** Google Web Risk (commercial). Not bound by default; swap the binding below to use it. */
class WebRiskProvider @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val settings: SettingsRepository,
) : UrlReputationProvider {
    override val displayName = "Google Web Risk"

    override suspend fun check(url: String): ProviderResult<List<ThreatMatch>> {
        val key = settings.apiKey(ApiKey.SAFE_BROWSING) ?: return ProviderResult.NotConfigured
        val httpUrl = ENDPOINT.toHttpUrl().newBuilder().apply {
            listOf("MALWARE", "SOCIAL_ENGINEERING", "UNWANTED_SOFTWARE").forEach { addQueryParameter("threatTypes", it) }
            addQueryParameter("uri", url)
        }.build()
        val req = Request.Builder().url(httpUrl).header("X-Goog-Api-Key", key).get().build()
        return client.fetch(req) { ProviderResult.Success(parse(json, url, it.body.string())) }
    }

    companion object {
        const val ENDPOINT = "https://webrisk.googleapis.com/v1/uris:search"

        /** Response is {"threat":{"threatTypes":[...],"expireTime":"..."}} or {} when not listed. */
        fun parse(json: Json, url: String, body: String): List<ThreatMatch> {
            val threat = json.parseToJsonElement(body.ifBlank { "{}" }).jsonObject["threat"] as? JsonObject ?: return emptyList()
            val types = threat["threatTypes"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content }
            return if (types.isEmpty()) emptyList() else listOf(ThreatMatch(url, types))
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class UrlCheckModule {
    @Binds abstract fun reputation(impl: SafeBrowsingProvider): UrlReputationProvider
}

/** Minimal protobuf wire-format reader: varint (0), 64-bit (1), length-delimited (2) and 32-bit (5) fields. */
private class Proto(private val buf: ByteArray) {
    private var pos = 0

    private fun varint(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            if (pos >= buf.size || shift > 63) throw IOException("Malformed protobuf varint")
            val b = buf[pos++].toInt()
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
        }
    }

    /** (field number, value): value is Long for varints, ByteArray for length-delimited, null for skipped fixed widths. */
    fun fields(): List<Pair<Int, Any?>> = buildList {
        while (pos < buf.size) {
            val tag = varint()
            val field = (tag ushr 3).toInt()
            when ((tag and 7).toInt()) {
                0 -> add(field to varint())
                1 -> { pos += 8; add(field to null) }
                2 -> {
                    val len = varint().toInt()
                    if (len < 0 || pos + len > buf.size) throw IOException("Malformed protobuf length")
                    add(field to buf.copyOfRange(pos, pos + len)); pos += len
                }
                5 -> { pos += 4; add(field to null) }
                else -> throw IOException("Unsupported protobuf wire type")
            }
        }
    }

    fun varints(): List<Long> = buildList { while (pos < buf.size) add(varint()) }
}
