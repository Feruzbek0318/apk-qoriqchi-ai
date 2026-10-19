package uz.apkqoriqchi.feature.url

import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import uz.apkqoriqchi.core.data.ApiKey
import uz.apkqoriqchi.core.data.SettingsRepository
import uz.apkqoriqchi.core.model.ProviderResult

@Serializable
data class VtStats(val malicious: Int = 0, val suspicious: Int = 0, val harmless: Int = 0, val undetected: Int = 0, val timeout: Int = 0) {
    val total: Int get() = malicious + suspicious + harmless + undetected + timeout
}

@Serializable
data class VtEngineHit(val engine: String, val category: String, val result: String?)

@Serializable
data class VtUrlReport(
    val stats: VtStats,
    /** Only engines that flagged the URL (malicious/suspicious). */
    val hits: List<VtEngineHit> = emptyList(),
    val categories: List<String> = emptyList(),
    val finalUrl: String? = null,
    val lastAnalysisDate: Long? = null,
    val reputation: Int? = null,
)

/** Minimal VirusTotal v3 URL client (self-contained; the shared VT client is owned by another module). */
@Singleton
class VtUrlClient @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val settings: SettingsRepository,
) {
    private fun key() = settings.apiKey(ApiKey.VIRUSTOTAL)

    suspend fun report(url: String): ProviderResult<VtUrlReport> {
        val key = key() ?: return ProviderResult.NotConfigured
        val req = Request.Builder().url("$BASE/urls/${urlId(url)}").header("x-apikey", key).get().build()
        return client.fetch(req) { ProviderResult.Success(parseUrlReport(json, it.body.string())) }
    }

    /** POST /urls, then poll /analyses/{id} (capped) until completed. */
    suspend fun submitAndWait(url: String, pollDelayMs: Long = 15_000, maxPolls: Int = 8): ProviderResult<VtUrlReport> {
        val key = key() ?: return ProviderResult.NotConfigured
        val post = Request.Builder().url("$BASE/urls").header("x-apikey", key)
            .post(FormBody.Builder().add("url", url).build()).build()
        val analysisId = when (val r = client.fetch(post) { resp ->
            val id = json.parseToJsonElement(resp.body.string()).jsonObject["data"]?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
            if (id == null) ProviderResult.Failed(FailCode.PARSE, false) else ProviderResult.Success(id)
        }) {
            is ProviderResult.Success -> r.data
            is ProviderResult.Failed -> return r
            else -> return ProviderResult.Failed(FailCode.PARSE, false)
        }
        repeat(maxPolls) {
            delay(pollDelayMs)
            val get = Request.Builder().url("$BASE/analyses/$analysisId").header("x-apikey", key).get().build()
            when (val r = client.fetch(get) { ProviderResult.Success(parseAnalysis(json, it.body.string())) }) {
                is ProviderResult.Success -> r.data?.let { done ->
                    // Prefer the full URL object (final URL, categories); fall back to the analysis stats.
                    val full = report(url)
                    return if (full is ProviderResult.Success) full else ProviderResult.Success(done)
                }
                is ProviderResult.Failed -> if (!r.retryable) return r
                else -> Unit
            }
        }
        return ProviderResult.Failed(FailCode.TIMEOUT)
    }

    companion object {
        const val BASE = "https://www.virustotal.com/api/v3"

        /** VT URL identifier: unpadded base64url of the URL. */
        fun urlId(url: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(url.toByteArray(Charsets.UTF_8))

        fun parseUrlReport(json: Json, body: String): VtUrlReport {
            val attrs = json.parseToJsonElement(body).jsonObject["data"]!!.jsonObject["attributes"]!!.jsonObject
            return VtUrlReport(
                stats = stats(attrs["last_analysis_stats"] as? JsonObject),
                hits = hits(attrs["last_analysis_results"] as? JsonObject),
                categories = (attrs["categories"] as? JsonObject)?.values?.mapNotNull { it.jsonPrimitive.contentOrNull }?.distinct().orEmpty(),
                finalUrl = attrs["last_final_url"]?.jsonPrimitive?.contentOrNull,
                lastAnalysisDate = attrs["last_analysis_date"]?.jsonPrimitive?.longOrNull,
                reputation = attrs["reputation"]?.jsonPrimitive?.intOrNull,
            )
        }

        /** Returns null while the analysis is still queued / in progress. */
        fun parseAnalysis(json: Json, body: String): VtUrlReport? {
            val attrs = json.parseToJsonElement(body).jsonObject["data"]!!.jsonObject["attributes"]!!.jsonObject
            if (attrs["status"]?.jsonPrimitive?.contentOrNull != "completed") return null
            return VtUrlReport(
                stats = stats(attrs["stats"] as? JsonObject),
                hits = hits(attrs["results"] as? JsonObject),
                lastAnalysisDate = attrs["date"]?.jsonPrimitive?.longOrNull,
            )
        }

        private fun stats(o: JsonObject?): VtStats {
            fun n(k: String) = o?.get(k)?.jsonPrimitive?.intOrNull ?: 0
            return VtStats(n("malicious"), n("suspicious"), n("harmless"), n("undetected"), n("timeout"))
        }

        private fun hits(o: JsonObject?): List<VtEngineHit> = o.orEmpty().mapNotNull { (engine, v) ->
            val r = v as? JsonObject ?: return@mapNotNull null
            val cat = r["category"]?.jsonPrimitive?.contentOrNull
            if (cat != "malicious" && cat != "suspicious") null
            else VtEngineHit(r["engine_name"]?.jsonPrimitive?.contentOrNull ?: engine, cat, r["result"]?.jsonPrimitive?.contentOrNull)
        }.sortedBy { it.category != "malicious" }
    }
}
