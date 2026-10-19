package uz.apkqoriqchi.core.network.virustotal

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import uz.apkqoriqchi.R
import uz.apkqoriqchi.core.data.ApiKey
import uz.apkqoriqchi.core.data.SettingsRepository
import uz.apkqoriqchi.core.model.ProviderResult

/**
 * VirusTotal API v3 client. Uses the user's key from Settings (header `x-apikey`, never logged).
 * Failed reasons are localized, user-presentable messages.
 */
@Singleton
class VirusTotalClient @Inject constructor(
    private val http: OkHttpClient,
    private val json: Json,
    private val settings: SettingsRepository,
    @ApplicationContext private val context: Context,
) {
    private val uploadHttp: OkHttpClient by lazy {
        http.newBuilder().writeTimeout(15, TimeUnit.MINUTES).readTimeout(5, TimeUnit.MINUTES).build()
    }

    val isConfigured: Boolean get() = settings.apiKey(ApiKey.VIRUSTOTAL) != null

    suspend fun fileReport(sha256: String): ProviderResult<VtFileReport> =
        request(get("$BASE/files/${sha256.lowercase()}")) { body ->
            val obj = json.decodeFromString<VtDataDto<VtObjectDto<VtFileAttrsDto>>>(body).data
            val a = obj.attributes ?: VtFileAttrsDto()
            VtFileReport(
                sha256 = a.sha256 ?: sha256.lowercase(),
                stats = a.stats ?: VtStats(),
                results = a.results.toEngineResults(),
                lastAnalysisDate = a.lastAnalysisDate,
                threatLabel = a.threat?.suggestedThreatLabel,
                reputation = a.reputation,
                meaningfulName = a.meaningfulName,
            )
        }

    /** Uploads [file] for analysis and returns the analysis id. Files over 32 MB go through an upload URL. */
    suspend fun uploadFile(file: File): ProviderResult<String> {
        if (file.length() > MAX_UPLOAD_BYTES) return ProviderResult.Failed(context.getString(R.string.apk_vt_err_too_large), retryable = false)
        val target = if (file.length() > DIRECT_UPLOAD_BYTES) {
            when (val r = request(get("$BASE/files/upload_url")) { json.decodeFromString<VtDataDto<String>>(it).data }) {
                is ProviderResult.Success -> r.data
                ProviderResult.NotFound -> return ProviderResult.Failed(context.getString(R.string.apk_vt_err_generic))
                else -> return r
            }
        } else "$BASE/files"
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, file.asRequestBody("application/octet-stream".toMediaType()))
            .build()
        return request({ key -> Request.Builder().url(target).header(HEADER, key).post(body).build() }, uploadHttp) {
            json.decodeFromString<VtDataDto<VtObjectDto<VtAnalysisAttrsDto>>>(it).data.id
        }
    }

    suspend fun analysis(id: String): ProviderResult<VtAnalysis> =
        request(get("$BASE/analyses/$id")) { body ->
            val obj = json.decodeFromString<VtDataDto<VtObjectDto<VtAnalysisAttrsDto>>>(body).data
            val a = obj.attributes ?: VtAnalysisAttrsDto()
            VtAnalysis(id = obj.id.ifEmpty { id }, status = a.status, stats = a.stats ?: VtStats(), results = a.results.toEngineResults())
        }

    private fun get(url: String): (String) -> Request = { key -> Request.Builder().url(url).header(HEADER, key).get().build() }

    private suspend fun <T> request(
        build: (String) -> Request,
        client: OkHttpClient = http,
        parse: (String) -> T,
    ): ProviderResult<T> {
        val key = settings.apiKey(ApiKey.VIRUSTOTAL) ?: return ProviderResult.NotConfigured
        return try {
            client.newCall(build(key)).await().use { resp ->
                val body = resp.body.string()
                if (resp.isSuccessful) ProviderResult.Success(parse(body)) else mapError(resp.code, body)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            ProviderResult.Failed(context.getString(R.string.apk_vt_err_network))
        } catch (e: SerializationException) {
            ProviderResult.Failed(context.getString(R.string.apk_vt_err_generic))
        } catch (e: IllegalArgumentException) {
            ProviderResult.Failed(context.getString(R.string.apk_vt_err_generic))
        }
    }

    private fun mapError(code: Int, body: String): ProviderResult<Nothing> {
        val errCode = runCatching { json.decodeFromString<VtErrorEnvelope>(body).error?.code }.getOrNull()
        return when {
            code == 404 || errCode == "NotFoundError" -> ProviderResult.NotFound
            code == 429 || errCode == "QuotaExceededError" || errCode == "TooManyRequestsError" ->
                ProviderResult.Failed(context.getString(R.string.apk_vt_err_quota))
            code == 401 || errCode == "WrongCredentialsError" || errCode == "AuthenticationRequiredError" ->
                ProviderResult.Failed(context.getString(R.string.apk_vt_err_bad_key), retryable = false)
            code == 403 -> ProviderResult.Failed(context.getString(R.string.apk_vt_err_forbidden), retryable = false)
            code == 413 -> ProviderResult.Failed(context.getString(R.string.apk_vt_err_too_large), retryable = false)
            else -> ProviderResult.Failed(context.getString(R.string.apk_vt_err_server, code))
        }
    }

    companion object {
        const val BASE = "https://www.virustotal.com/api/v3"
        private const val HEADER = "x-apikey"
        const val DIRECT_UPLOAD_BYTES = 32L * 1024 * 1024
        const val MAX_UPLOAD_BYTES = 650L * 1024 * 1024
        fun guiUrl(sha256: String) = "https://www.virustotal.com/gui/file/${sha256.lowercase()}"
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) = cont.resume(response) { _, r, _ -> r.close() }
        override fun onFailure(call: Call, e: IOException) { if (!cont.isCancelled) cont.resumeWithException(e) }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}
