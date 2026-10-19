package uz.apkqoriqchi.feature.apps

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import uz.apkqoriqchi.R
import uz.apkqoriqchi.core.data.HistoryRepository
import uz.apkqoriqchi.core.network.virustotal.VirusTotalClient
import uz.apkqoriqchi.core.model.ProviderResult
import uz.apkqoriqchi.core.model.RiskLevel
import uz.apkqoriqchi.core.model.ScanRecord
import uz.apkqoriqchi.core.model.ScanType

enum class AppFilter { ALL, RISKY, SIDELOADED, SPECIAL_ACCESS }

/** Reputation lookup status for one app. Idle means it was NOT checked. */
sealed interface ReputationState {
    data object NotChecked : ReputationState
    data object Checking : ReputationState
    data object NotConfigured : ReputationState
    data object NoReport : ReputationState
    data class Detections(val malicious: Int, val total: Int) : ReputationState
    data class Failed(val reason: String) : ReputationState
}

data class AppAuditUiState(
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
    val apps: List<AppAuditResult> = emptyList(),
    val includeSystem: Boolean = false,
    val filter: AppFilter = AppFilter.ALL,
    val query: String = "",
    val sortByRisk: Boolean = true,
    val selectedPackage: String? = null,
    val reputation: Map<String, ReputationState> = emptyMap(),
) {
    val visible: List<AppAuditResult> get() = filterApps(apps, filter, query, sortByRisk)
    val selected: AppAuditResult? get() = apps.firstOrNull { it.packageName == selectedPackage }
    fun reputationOf(pkg: String): ReputationState = reputation[pkg] ?: ReputationState.NotChecked

    /** Heuristic verdict combined with reputation, if it was actually checked. */
    fun effectiveRisk(app: AppAuditResult): RiskLevel =
        (reputationOf(app.packageName) as? ReputationState.Detections)
            ?.let { AppRiskScorer.withReputation(app.riskLevel, it.malicious) } ?: app.riskLevel
    val counts: Map<RiskLevel, Int> get() = apps.groupingBy { it.riskLevel }.eachCount()
    val sideloadedCount: Int get() = apps.count { it.sideloaded }
}

fun filterApps(apps: List<AppAuditResult>, filter: AppFilter, query: String, sortByRisk: Boolean): List<AppAuditResult> {
    val q = query.trim()
    return apps.asSequence()
        .filter {
            when (filter) {
                AppFilter.ALL -> true
                AppFilter.RISKY -> it.riskLevel >= RiskLevel.MEDIUM
                AppFilter.SIDELOADED -> it.sideloaded
                AppFilter.SPECIAL_ACCESS -> it.hasSpecialAccess
            }
        }
        .filter { q.isEmpty() || it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true) }
        .sortedWith(
            if (sortByRisk) compareByDescending<AppAuditResult> { it.riskLevel }.thenBy { it.label.lowercase() }
            else compareBy { it.label.lowercase() }
        )
        .toList()
}

@Serializable
data class InstalledAppDetails(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val installer: String?,
    val signerSha256: String?,
    val targetSdk: Int,
    val sideloaded: Boolean,
    val isSystem: Boolean,
    val signals: List<String>,
    val evidence: List<String>,
    val grantedDangerous: List<String>,
)

@HiltViewModel
class AppAuditViewModel @Inject constructor(
    private val auditor: InstalledAppAuditor,
    private val history: HistoryRepository,
    private val json: Json,
    private val virusTotal: VirusTotalClient,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(AppAuditUiState())
    val state: StateFlow<AppAuditUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init { load() }

    /** Runs only on screen open or explicit refresh; nothing is audited in the background. */
    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { auditor.audit(_state.value.includeSystem) }
                .onSuccess { apps -> _state.update { it.copy(loading = false, loaded = true, apps = apps) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message ?: e.javaClass.simpleName) } }
        }
    }

    fun setIncludeSystem(value: Boolean) {
        _state.update { it.copy(includeSystem = value) }
        load()
    }

    fun setFilter(filter: AppFilter) = _state.update { it.copy(filter = filter) }
    fun setQuery(query: String) = _state.update { it.copy(query = query) }
    fun toggleSort() = _state.update { it.copy(sortByRisk = !it.sortByRisk) }
    fun closeDetail() = _state.update { it.copy(selectedPackage = null) }

    /** Drops apps uninstalled while the user was in system UI (no re-audit). */
    fun pruneUninstalled() {
        val current = _state.value.apps
        viewModelScope.launch {
            val gone = withContext(Dispatchers.IO) { current.filterNot { auditor.isInstalled(it.packageName) } }.map { it.packageName }.toSet()
            if (gone.isNotEmpty()) _state.update { s ->
                s.copy(apps = s.apps.filterNot { it.packageName in gone }, selectedPackage = s.selectedPackage?.takeUnless { it in gone })
            }
        }
    }

    fun checkReputation(app: AppAuditResult) {
        val pkg = app.packageName
        if (_state.value.reputationOf(pkg) == ReputationState.Checking) return
        setReputation(pkg, ReputationState.Checking)
        viewModelScope.launch {
            val result = runCatching {
                when (val r = virusTotal.fileReport(auditor.baseApkSha256(pkg))) {
                    is ProviderResult.Success -> ReputationState.Detections(r.data.stats.malicious, r.data.stats.total)
                    ProviderResult.NotFound -> ReputationState.NoReport
                    ProviderResult.NotConfigured -> ReputationState.NotConfigured
                    is ProviderResult.Failed -> ReputationState.Failed(r.reason)
                }
            }.getOrElse { ReputationState.Failed(it.message ?: it.javaClass.simpleName) }
            setReputation(pkg, result)
        }
    }

    private fun setReputation(pkg: String, value: ReputationState) =
        _state.update { it.copy(reputation = it.reputation + (pkg to value)) }

    fun openDetail(app: AppAuditResult) {
        _state.update { it.copy(selectedPackage = app.packageName) }
        viewModelScope.launch { runCatching { history.save(app.toRecord()) } }
    }

    private fun AppAuditResult.toRecord() = ScanRecord(
        type = ScanType.INSTALLED_APP,
        target = packageName,
        riskLevel = riskLevel,
        summary = context.getString(R.string.apps_history_summary, label, findings.size),
        detailsJson = json.encodeToString(
            InstalledAppDetails.serializer(),
            InstalledAppDetails(
                packageName, label, versionName, installer, signerSha256, targetSdk, sideloaded, isSystem,
                findings.map { it.signal.name }, findings.map { it.evidence }, grantedDangerous,
            ),
        ),
    )
}
