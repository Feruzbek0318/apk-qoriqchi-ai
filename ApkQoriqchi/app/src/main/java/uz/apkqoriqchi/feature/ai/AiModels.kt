package uz.apkqoriqchi.feature.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import uz.apkqoriqchi.core.model.RiskLevel

enum class AiLanguage(val promptName: String) { UZBEK("Uzbek (Latin script)"), RUSSIAN("Russian"), ENGLISH("English") }

@Serializable
enum class AiRisk(val level: RiskLevel) {
    @SerialName("no_detections") NO_DETECTIONS(RiskLevel.NO_DETECTIONS),
    @SerialName("low") LOW(RiskLevel.LOW),
    @SerialName("medium") MEDIUM(RiskLevel.MEDIUM),
    @SerialName("high") HIGH(RiskLevel.HIGH),
    @SerialName("critical") CRITICAL(RiskLevel.CRITICAL),
    @SerialName("unknown") UNKNOWN(RiskLevel.UNKNOWN),
}

@Serializable
enum class AiConfidence { @SerialName("low") LOW, @SerialName("medium") MEDIUM, @SerialName("high") HIGH }

/** The model's structured analysis. Every field is required (no defaults), so a missing field fails decoding. */
@Serializable
data class AiAnalysis(
    @SerialName("risk_level") val riskLevel: AiRisk,
    val confidence: AiConfidence,
    val verdict: String,
    @SerialName("confirmed_detections") val confirmedDetections: List<String>,
    @SerialName("suspicious_indicators") val suspiciousIndicators: List<String>,
    @SerialName("benign_or_explanatory_findings") val benignFindings: List<String>,
    @SerialName("engine_disagreements") val engineDisagreements: List<String>,
    val evidence: List<String>,
    val limitations: List<String>,
    @SerialName("recommended_actions") val recommendedActions: List<String>,
    @SerialName("localized_summary") val localizedSummary: String,
)
