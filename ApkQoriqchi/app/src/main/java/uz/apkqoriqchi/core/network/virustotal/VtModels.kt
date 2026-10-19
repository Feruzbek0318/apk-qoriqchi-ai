package uz.apkqoriqchi.core.network.virustotal

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Engine verdict counters (VT `last_analysis_stats` / analysis `stats`). */
@Serializable
data class VtStats(
    val malicious: Int = 0,
    val suspicious: Int = 0,
    val undetected: Int = 0,
    val harmless: Int = 0,
    val timeout: Int = 0,
    @SerialName("type-unsupported") val typeUnsupported: Int = 0,
    val failure: Int = 0,
) {
    /** Engines that produced a usable verdict. */
    val total: Int get() = malicious + suspicious + undetected + harmless
}

@Serializable
data class VtEngineResult(val engine: String, val category: String, val result: String? = null) {
    val isDetection: Boolean get() = category == "malicious" || category == "suspicious"
}

@Serializable
data class VtFileReport(
    val sha256: String,
    val stats: VtStats,
    val results: List<VtEngineResult> = emptyList(),
    /** Unix seconds. */
    val lastAnalysisDate: Long? = null,
    val threatLabel: String? = null,
    val reputation: Int? = null,
    val meaningfulName: String? = null,
) {
    /** Malicious first, then suspicious, alphabetically by engine. */
    val detections: List<VtEngineResult>
        get() = results.filter { it.isDetection }
            .sortedWith(compareBy({ it.category != "malicious" }, { it.engine.lowercase() }))
}

@Serializable
data class VtAnalysis(
    val id: String,
    /** queued | in-progress | completed */
    val status: String,
    val stats: VtStats = VtStats(),
    val results: List<VtEngineResult> = emptyList(),
) {
    val isCompleted: Boolean get() = status == "completed"
}

// ---- wire DTOs (internal) ----

@Serializable
internal class VtEngineDto(
    val category: String? = null,
    @SerialName("engine_name") val engineName: String? = null,
    val result: String? = null,
)

@Serializable
internal class VtThreatClassDto(@SerialName("suggested_threat_label") val suggestedThreatLabel: String? = null)

@Serializable
internal class VtFileAttrsDto(
    @SerialName("last_analysis_stats") val stats: VtStats? = null,
    @SerialName("last_analysis_results") val results: Map<String, VtEngineDto> = emptyMap(),
    @SerialName("last_analysis_date") val lastAnalysisDate: Long? = null,
    @SerialName("popular_threat_classification") val threat: VtThreatClassDto? = null,
    val reputation: Int? = null,
    @SerialName("meaningful_name") val meaningfulName: String? = null,
    val sha256: String? = null,
)

@Serializable
internal class VtAnalysisAttrsDto(
    val status: String = "queued",
    val stats: VtStats? = null,
    val results: Map<String, VtEngineDto> = emptyMap(),
)

@Serializable
internal class VtObjectDto<A>(val id: String = "", val type: String? = null, val attributes: A? = null)

@Serializable
internal class VtDataDto<T>(val data: T)

@Serializable
internal class VtErrorDto(val code: String? = null, val message: String? = null)

@Serializable
internal class VtErrorEnvelope(val error: VtErrorDto? = null)

internal fun Map<String, VtEngineDto>.toEngineResults(): List<VtEngineResult> =
    map { (key, v) -> VtEngineResult(engine = v.engineName ?: key, category = v.category ?: "undetected", result = v.result) }
