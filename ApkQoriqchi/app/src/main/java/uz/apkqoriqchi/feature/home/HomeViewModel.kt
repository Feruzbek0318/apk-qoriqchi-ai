package uz.apkqoriqchi.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uz.apkqoriqchi.feature.settings.LocalPrefs
import uz.apkqoriqchi.core.data.ApiKey
import uz.apkqoriqchi.core.data.HistoryRepository
import uz.apkqoriqchi.core.data.SettingsRepository
import uz.apkqoriqchi.core.model.RiskLevel
import uz.apkqoriqchi.core.model.ScanRecord

data class HomeUi(
    val loading: Boolean = true,
    val recent: List<ScanRecord> = emptyList(),
    val totalScans: Int = 0,
    val lastScanAt: Long? = null,
    val configured: Map<ApiKey, Boolean> = emptyMap(),
)

/** Builds the dashboard state from history (newest first) and which keys are set. Pure for tests. */
internal fun buildHomeUi(history: List<ScanRecord>, configured: Map<ApiKey, Boolean>, recentCount: Int = 5) = HomeUi(
    loading = false,
    recent = history.sortedByDescending { it.createdAt }.take(recentCount),
    totalScans = history.size,
    // UNKNOWN means the scan could not reach a verdict, so it doesn't count as "successful".
    lastScanAt = history.filter { it.riskLevel != RiskLevel.UNKNOWN }.maxOfOrNull { it.createdAt },
    configured = configured,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    history: HistoryRepository,
    settings: SettingsRepository,
    private val localPrefs: LocalPrefs,
) : ViewModel() {
    /** null while loading, so the onboarding never flashes for returning users. */
    val onboardingDone: StateFlow<Boolean?> = localPrefs.onboardingDone
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun finishOnboarding() { viewModelScope.launch { localPrefs.setOnboardingDone() } }

    val ui: StateFlow<HomeUi> = combine(history.observeAll(), settings.keysChanged) { all, _ ->
        buildHomeUi(all, ApiKey.entries.associateWith { settings.apiKey(it) != null })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUi())
}
