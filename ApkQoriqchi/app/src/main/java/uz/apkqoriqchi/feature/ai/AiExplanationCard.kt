package uz.apkqoriqchi.feature.ai

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import uz.apkqoriqchi.core.model.RiskLevel
import uz.apkqoriqchi.core.model.labelRes
import uz.apkqoriqchi.ui.theme.RiskColors
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uz.apkqoriqchi.R
import uz.apkqoriqchi.core.data.ApiKey
import uz.apkqoriqchi.core.data.SettingsRepository
import uz.apkqoriqchi.core.model.ProviderResult
import uz.apkqoriqchi.ui.components.IconTile
import uz.apkqoriqchi.ui.components.SectionCard
import uz.apkqoriqchi.ui.components.StatusPill

sealed interface AiState {
    data object Idle : AiState
    data object Loading : AiState
    data class Done(val analysis: AiAnalysis) : AiState
    data class Error(val reason: String) : AiState
}

@HiltViewModel
class AiExplanationViewModel @Inject constructor(
    private val explainer: GeminiExplainer,
    private val settings: SettingsRepository,
) : ViewModel() {
    val enabled: StateFlow<Boolean?> = settings.prefs.map { it.aiExplanations }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val hasKey: StateFlow<Boolean> = settings.keysChanged.map { settings.apiKey(ApiKey.GEMINI) != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, settings.apiKey(ApiKey.GEMINI) != null)

    private val _state = MutableStateFlow<AiState>(AiState.Idle)
    val state: StateFlow<AiState> = _state.asStateFlow()

    fun explain(facts: String) {
        if (_state.value == AiState.Loading) return
        _state.value = AiState.Loading
        viewModelScope.launch {
            _state.value = when (val r = explainer.explain(facts)) {
                is ProviderResult.Success -> AiState.Done(r.data)
                is ProviderResult.Failed -> AiState.Error(r.reason)
                ProviderResult.NotConfigured -> AiState.Error("auth")
                ProviderResult.NotFound -> AiState.Error("invalid")
            }
        }
    }
}

/**
 * PUBLIC CONTRACT (used by APK / URL / app-audit screens).
 * Shows an AI-written plain-language explanation of [facts] in the app's current language.
 * [facts] is a plain-text evidence list built by the caller (verdict, engine counts, permissions...).
 * Renders nothing when AI is disabled, a hint when no key is set, and never calls the API without a tap.
 * [verdict] is the caller's deterministic verdict: the AI never overrides it; a different AI estimate is shown only as a note.
 */
@Composable
fun AiExplanationCard(facts: String, modifier: Modifier = Modifier, verdict: RiskLevel? = null) {
    val vm: AiExplanationViewModel = hiltViewModel(key = "ai_" + facts.hashCode())
    val enabled by vm.enabled.collectAsStateWithLifecycle()
    val hasKey by vm.hasKey.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    if (enabled != true) return

    SectionCard(modifier.animateContentSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Filled.AutoAwesome, size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.ai_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            StatusPill(stringResource(R.string.ai_generated_badge), MaterialTheme.colorScheme.secondary)
        }
        Spacer(Modifier.height(12.dp))
        if (!hasKey) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Key, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.ai_no_key_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@SectionCard
        }
        when (val s = state) {
            AiState.Idle -> {
                Text(
                    stringResource(R.string.ai_idle_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = { vm.explain(facts) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.ai_explain_button))
                }
            }
            AiState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.ai_loading), style = MaterialTheme.typography.bodyMedium)
            }
            is AiState.Done -> {
                AnalysisContent(s.analysis, verdict)
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.ai_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            is AiState.Error -> {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    Text(errorText(s.reason), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { vm.explain(facts) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.common_retry))
                }
            }
        }
    }
}

@Composable
private fun AnalysisContent(a: AiAnalysis, verdict: RiskLevel?) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(a.verdict, style = MaterialTheme.typography.titleSmall)
                Text(a.localizedSummary, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text(
            stringResource(R.string.ai_confidence, stringResource(a.confidence.labelRes)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // The app's deterministic verdict is authoritative; the AI estimate only appears as a note.
        val aiLevel = a.riskLevel.level
        if (verdict == null || aiLevel != verdict) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Info, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(
                        if (verdict == null) R.string.ai_note_estimate else R.string.ai_note_disagree,
                        stringResource(aiLevel.labelRes),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        Section(R.string.ai_sec_confirmed, a.confirmedDetections, Icons.Filled.Dangerous, RiskColors.Critical)
        Section(R.string.ai_sec_suspicious, a.suspiciousIndicators, Icons.Filled.Warning, RiskColors.Medium)
        Section(R.string.ai_sec_benign, a.benignFindings, Icons.Filled.CheckCircle, RiskColors.Clean)
        Section(R.string.ai_sec_disagreements, a.engineDisagreements, Icons.AutoMirrored.Filled.CompareArrows, MaterialTheme.colorScheme.secondary)
        Section(R.string.ai_sec_evidence, a.evidence, Icons.Filled.FactCheck, MaterialTheme.colorScheme.onSurfaceVariant)
        Section(R.string.ai_sec_actions, a.recommendedActions, Icons.Filled.TaskAlt, MaterialTheme.colorScheme.primary, numbered = true)
        Section(R.string.ai_sec_limitations, a.limitations, Icons.Filled.Info, MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Section(title: Int, items: List<String>, icon: ImageVector, tint: Color, numbered: Boolean = false) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(16.dp), tint = tint)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(title), style = MaterialTheme.typography.labelLarge, color = tint)
        }
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items.forEachIndexed { i, item ->
                    Text(
                        (if (numbered) "${i + 1}. " else "• ") + item,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 22.dp),
                    )
                }
            }
        }
    }
}

private val AiConfidence.labelRes: Int
    get() = when (this) {
        AiConfidence.LOW -> R.string.ai_confidence_low
        AiConfidence.MEDIUM -> R.string.ai_confidence_medium
        AiConfidence.HIGH -> R.string.ai_confidence_high
    }

@Composable
private fun errorText(reason: String): String = when (reason) {
    "auth" -> stringResource(R.string.ai_error_auth)
    "rate_limit" -> stringResource(R.string.ai_error_rate_limit)
    "refusal" -> stringResource(R.string.ai_error_refusal)
    "invalid" -> stringResource(R.string.ai_error_invalid)
    "network" -> stringResource(R.string.ai_error_network)
    "overloaded" -> stringResource(R.string.ai_error_overloaded)
    else -> stringResource(R.string.ai_error_generic, reason)
}
