package uz.apkqoriqchi.feature.settings

import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import uz.apkqoriqchi.core.data.ApiKey
import uz.apkqoriqchi.core.data.SettingsRepository
import uz.apkqoriqchi.feature.ai.GeminiProtocol

enum class TestOutcome { OK, NOT_CONFIGURED, BAD_KEY, QUOTA, OFFLINE, SERVER, OTHER }

data class TestResult(val outcome: TestOutcome, val httpCode: Int? = null, val message: String? = null)

/** Makes one cheap, real request per provider to verify the key and connectivity. */
@Singleton
class ConnectionTester @Inject constructor(
    private val client: OkHttpClient,
    private val settings: SettingsRepository,
) {
    suspend fun test(provider: ApiKey): TestResult {
        val key = settings.apiKey(provider) ?: return TestResult(TestOutcome.NOT_CONFIGURED)
        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request(provider, key)).execute().use { resp ->
                    val body = runCatching { resp.body.string() }.getOrDefault("")
                    TestResult(classify(provider, resp.code), resp.code, errorMessage(body))
                }
            } catch (e: IOException) {
                TestResult(TestOutcome.OFFLINE, message = e.javaClass.simpleName)
            }
        }
    }

    private fun request(provider: ApiKey, key: String): Request = when (provider) {
        // Hash of the empty file: 200 (known) or 404 (unknown) both prove the key works.
        ApiKey.VIRUSTOTAL -> Request.Builder()
            .url("https://www.virustotal.com/api/v3/files/$EMPTY_SHA256")
            .header("x-apikey", key).get().build()
        ApiKey.SAFE_BROWSING -> Request.Builder()
            .url("https://safebrowsing.googleapis.com/v5/urls:search".toHttpUrl().newBuilder()
                .addQueryParameter("urls", "https://example.com/").build())
            .header("X-Goog-Api-Key", key).get().build()
        // Model metadata lookup: proves the key works without spending generation quota.
        ApiKey.GEMINI -> Request.Builder()
            .url("${GeminiProtocol.BASE}/${GeminiProtocol.MODEL}")
            .header("x-goog-api-key", key).get().build()
    }

    companion object {
        const val EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        private val json = Json { ignoreUnknownKeys = true }

        fun classify(provider: ApiKey, code: Int): TestOutcome = when {
            code in 200..299 -> TestOutcome.OK
            code == 404 && provider == ApiKey.VIRUSTOTAL -> TestOutcome.OK
            code == 401 || code == 403 -> TestOutcome.BAD_KEY
            code == 400 && provider != ApiKey.VIRUSTOTAL -> TestOutcome.BAD_KEY // Google: "API key not valid"
            code == 429 -> TestOutcome.QUOTA
            code >= 500 -> TestOutcome.SERVER
            else -> TestOutcome.OTHER
        }

        /** Pulls a short human-readable message out of the common {"error":{"message":...}} shape. */
        fun errorMessage(body: String): String? = runCatching {
            val err = json.parseToJsonElement(body).jsonObject["error"] ?: return null
            (runCatching { err.jsonObject["message"]?.jsonPrimitive?.contentOrNull }.getOrNull() ?: err.jsonPrimitive.contentOrNull)
                ?.take(200)
        }.getOrNull()
    }
}
