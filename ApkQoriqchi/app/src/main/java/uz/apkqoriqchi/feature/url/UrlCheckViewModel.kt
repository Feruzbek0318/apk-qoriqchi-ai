package uz.apkqoriqchi.feature.url

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import uz.apkqoriqchi.core.data.HistoryRepository
import uz.apkqoriqchi.feature.settings.ScanNotifier
import uz.apkqoriqchi.core.data.IncomingShare
import uz.apkqoriqchi.core.data.IncomingShareBus
import uz.apkqoriqchi.core.model.ProviderResult
import uz.apkqoriqchi.core.model.RiskLevel
import uz.apkqoriqchi.core.model.ScanRecord
import uz.apkqoriqchi.core.model.ScanType

data class UrlUiState(
    val input: String = "",
    val inputError: UrlParseResult? = null,
    val target: NormalizedUrl? = null,
    val findings: List<HeuristicFinding> = emptyList(),
    /** null = request in flight. */
    val reputation: ProviderResult<List<ThreatMatch>>? = null,
    val vt: ProviderResult<VtUrlReport>? = null,
    val vtSubmitting: Boolean = false,
    val reputationName: String = "",
) {
    val running: Boolean get() = target != null && (reputation == null || vt == null || vtSubmitting)
    val risk: RiskLevel? get() = target?.let { if (running) null else UrlVerdict.combine(findings, reputation, vt) }
    val facts: String? get() = target?.let { t -> risk?.let { UrlVerdict.facts(t, it, findings, reputation, vt) } }
}

/** Persisted details. Never contains the query string, fragment, userinfo or API keys. */
@Serializable
private data class UrlScanDetails(
    val url: String,
    val finalUrl: String?,
    val heuristics: List<HeuristicType>,
    val reputationProvider: String,
    val providerStatus: Map<String, String>,
    val incomplete: Boolean,
    val threatTypes: List<String>,
    val vt: VtUrlReport?,
)

@HiltViewModel
class UrlCheckViewModel @Inject constructor(
    private val reputationProvider: UrlReputationProvider,
    private val vtClient: VtUrlClient,
    private val history: HistoryRepository,
    private val shareBus: IncomingShareBus,
    private val json: Json,
    private val notifier: ScanNotifier,
) : ViewModel() {

    private val _state = MutableStateFlow(UrlUiState(reputationName = reputationProvider.displayName))
    val state: StateFlow<UrlUiState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        viewModelScope.launch {
            shareBus.pending.filterIsInstance<IncomingShare.Url>().collect {
                shareBus.consume()
                onInput(it.url)
                check()
            }
        }
    }

    fun onInput(text: String) = _state.update { it.copy(input = text, inputError = null) }

    fun reset() {
        job?.cancel()
        _state.update { UrlUiState(input = it.input, reputationName = it.reputationName) }
    }

    fun check() {
        val parsed = UrlNormalizer.parse(_state.value.input)
        if (parsed !is UrlParseResult.Valid) {
            _state.update { it.copy(inputError = parsed) }
            return
        }
        val u = parsed.url
        job?.cancel()
        _state.update {
            UrlUiState(input = u.url, target = u, findings = UrlHeuristics.analyze(u), reputationName = it.reputationName)
        }
        job = viewModelScope.launch {
            // Both providers run in parallel; each updates its own card as soon as it answers.
            val a = launch { reputationProvider.check(u.asciiUrl).let { r -> _state.update { it.copy(reputation = r) } } }
            val b = launch { vtClient.report(u.url).let { r -> _state.update { it.copy(vt = r) } } }
            a.join(); b.join()
            save()
        }
    }

    /** Optional: submit an unseen URL to VirusTotal and wait (capped) for the analysis. */
    fun submitToVt() {
        val u = _state.value.target ?: return
        if (_state.value.vtSubmitting) return
        _state.update { it.copy(vtSubmitting = true) }
        job = viewModelScope.launch {
            val r = vtClient.submitAndWait(u.url)
            _state.update { it.copy(vt = r, vtSubmitting = false) }
            save()
        }
    }

    private suspend fun save() {
        val s = _state.value
        val u = s.target ?: return
        val risk = s.risk ?: return
        val vtReport = (s.vt as? ProviderResult.Success)?.data
        val threats = (s.reputation as? ProviderResult.Success)?.data.orEmpty()
        val summary = listOfNotNull(
            vtReport?.let { "VirusTotal ${it.stats.malicious}/${it.stats.total}" },
            threats.flatMap { it.threatTypes }.distinct().takeIf { it.isNotEmpty() }?.joinToString(prefix = "Safe Browsing: "),
            s.findings.takeIf { it.isNotEmpty() }?.let { f -> f.joinToString(limit = 3) { it.type.name.lowercase() } },
        ).joinToString(" · ").ifEmpty { "—" }
        val details = UrlScanDetails(
            url = UrlPrivacy.historyTarget(u),
            finalUrl = vtReport?.finalUrl?.let(UrlPrivacy::minimize),
            heuristics = s.findings.map { it.type },
            reputationProvider = s.reputationName,
            providerStatus = mapOf("safe_browsing" to s.reputation.statusCode(), "virustotal" to s.vt.statusCode()),
            incomplete = UrlVerdict.incomplete(s.reputation, s.vt),
            threatTypes = threats.flatMap { it.threatTypes }.distinct(),
            vt = vtReport?.copy(finalUrl = vtReport.finalUrl?.let(UrlPrivacy::minimize)),
        )
        runCatching {
            history.save(
                ScanRecord(type = ScanType.URL, target = UrlPrivacy.historyTarget(u), riskLevel = risk, summary = summary,
                    detailsJson = json.encodeToString(UrlScanDetails.serializer(), details)),
            )
        }.onSuccess { id -> notifier.notifyScanComplete(UrlPrivacy.historyTarget(u), risk, id) }
    }

    private fun ProviderResult<*>?.statusCode() = when (this) {
        null -> "pending"
        is ProviderResult.Success -> "success"
        ProviderResult.NotFound -> "not_found"
        ProviderResult.NotConfigured -> "not_configured"
        is ProviderResult.Failed -> "failed:$reason"
    }
}
