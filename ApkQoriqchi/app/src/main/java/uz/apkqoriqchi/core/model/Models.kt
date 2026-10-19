package uz.apkqoriqchi.core.model

/**
 * Risk verdict shown to the user. There is deliberately no "SAFE" value:
 * the best outcome is [NO_DETECTIONS], which never means "guaranteed safe".
 */
enum class RiskLevel { NO_DETECTIONS, LOW, MEDIUM, HIGH, CRITICAL, UNKNOWN }

enum class ScanType { APK, URL, INSTALLED_APP }

/** One persisted scan result. [detailsJson] is feature-specific JSON (owned by the feature that wrote it). */
data class ScanRecord(
    val id: Long = 0,
    val type: ScanType,
    val target: String,
    val sha256: String? = null,
    val riskLevel: RiskLevel,
    val summary: String,
    val detailsJson: String = "{}",
    val createdAt: Long = System.currentTimeMillis(),
)

/** Status of one external intelligence provider for a single scan. */
sealed interface ProviderResult<out T> {
    data class Success<T>(val data: T) : ProviderResult<T>
    /** Provider has no record (e.g. VirusTotal 404 for an unseen hash). */
    data object NotFound : ProviderResult<Nothing>
    /** User has not entered an API key for this provider. */
    data object NotConfigured : ProviderResult<Nothing>
    data class Failed(val reason: String, val retryable: Boolean = true) : ProviderResult<Nothing>
}
