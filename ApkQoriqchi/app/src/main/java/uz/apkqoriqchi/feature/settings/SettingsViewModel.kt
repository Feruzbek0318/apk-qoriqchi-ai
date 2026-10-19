package uz.apkqoriqchi.feature.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uz.apkqoriqchi.core.data.ApiKey
import uz.apkqoriqchi.core.data.HistoryRepository
import uz.apkqoriqchi.core.data.SettingsRepository
import uz.apkqoriqchi.core.data.UserPrefs

/** Language choices; an empty tag means "follow the system". */
enum class AppLanguage(val tag: String) { UZ("uz"), RU("ru"), EN("en"), SYSTEM("") }

data class SettingsUi(
    val prefs: UserPrefs = UserPrefs(),
    val notifyOnScan: Boolean = false,
    val configured: Map<ApiKey, Boolean> = emptyMap(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val history: HistoryRepository,
    private val localPrefs: LocalPrefs,
    private val tester: ConnectionTester,
) : ViewModel() {

    private val configured = settings.keysChanged.map { ApiKey.entries.associateWith { k -> settings.apiKey(k) != null } }

    val ui: StateFlow<SettingsUi> = combine(settings.prefs, localPrefs.notifyOnScan, configured) { p, n, c ->
        SettingsUi(p, n, c)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUi(configured = currentConfigured()))

    private val _tests = MutableStateFlow<Map<ApiKey, TestResult?>>(emptyMap())
    /** Connection-test results; a key present with a null value means "testing…". */
    val tests: StateFlow<Map<ApiKey, TestResult?>> = _tests.asStateFlow()

    private fun currentConfigured() = ApiKey.entries.associateWith { settings.apiKey(it) != null }

    fun saveKey(key: ApiKey, value: String) {
        settings.setApiKey(key, value)
        _tests.update { it - key }
    }

    fun removeKey(key: ApiKey) {
        settings.setApiKey(key, null)
        _tests.update { it - key }
    }

    fun removeAllKeys() = ApiKey.entries.forEach(::removeKey)

    fun test(key: ApiKey) {
        if (_tests.value.containsKey(key) && _tests.value[key] == null) return
        _tests.update { it + (key to null) }
        viewModelScope.launch {
            val r = tester.test(key)
            _tests.update { it + (key to r) }
        }
    }

    fun setAllowUpload(v: Boolean) = viewModelScope.launch { settings.setAllowFileUpload(v) }
    fun setAiExplanations(v: Boolean) = viewModelScope.launch { settings.setAiExplanations(v) }
    fun setNotifyOnScan(v: Boolean) = viewModelScope.launch { localPrefs.setNotifyOnScan(v) }
    fun clearHistory() = viewModelScope.launch { history.clear() }

    fun currentLanguage(): AppLanguage {
        val tags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        return AppLanguage.entries.firstOrNull { it != AppLanguage.SYSTEM && tags.startsWith(it.tag) } ?: AppLanguage.SYSTEM
    }

    fun setLanguage(lang: AppLanguage) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang.tag))
    }
}
