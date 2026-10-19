package uz.apkqoriqchi.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import uz.apkqoriqchi.R
import uz.apkqoriqchi.ui.components.AppTopBar
import uz.apkqoriqchi.ui.components.SectionCard
import uz.apkqoriqchi.ui.components.SectionTitle

private enum class PendingAction { CLEAR_HISTORY, DELETE_KEYS }

@Composable
fun PrivacyControlsScreen(onBack: () -> Unit) {
    val vm: SettingsViewModel = hiltViewModel()
    val ui by vm.ui.collectAsStateWithLifecycle()
    var pending by rememberSaveable { mutableStateOf<PendingAction?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val doneMsg = stringResource(R.string.privacy_done)

    Scaffold(
        topBar = { AppTopBar(stringResource(R.string.privacy_title), onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle(stringResource(R.string.privacy_controls))
            SectionCard {
                SwitchRow(
                    stringResource(R.string.settings_allow_upload),
                    stringResource(R.string.settings_allow_upload_body),
                    ui.prefs.allowFileUpload,
                    vm::setAllowUpload,
                )
                if (ui.prefs.allowFileUpload) WarningNote(R.string.settings_upload_warning)
                HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SwitchRow(stringResource(R.string.settings_ai), stringResource(R.string.settings_ai_body), ui.prefs.aiExplanations, vm::setAiExplanations)
                HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
                NotifyToggle(ui.notifyOnScan, vm::setNotifyOnScan)
            }
            SectionCard {
                DangerButton(R.string.history_clear_all) { pending = PendingAction.CLEAR_HISTORY }
                Spacer(Modifier.height(8.dp))
                DangerButton(R.string.privacy_delete_keys) { pending = PendingAction.DELETE_KEYS }
            }

            SectionTitle(stringResource(R.string.privacy_policy_title))
            SectionCard {
                listOf(
                    R.string.privacy_policy_1, R.string.privacy_policy_2, R.string.privacy_policy_3,
                    R.string.privacy_policy_4, R.string.privacy_policy_5,
                ).forEachIndexed { i, res ->
                    if (i > 0) Spacer(Modifier.height(8.dp))
                    Paragraph(res)
                }
            }

            SectionTitle(stringResource(R.string.privacy_deletion_title))
            SectionCard { Paragraph(R.string.privacy_deletion_body) }
            Spacer(Modifier.height(16.dp))
        }
    }

    pending?.let { action ->
        AlertDialog(
            onDismissRequest = { pending = null },
            icon = { Icon(Icons.Filled.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
            title = {
                Text(stringResource(if (action == PendingAction.CLEAR_HISTORY) R.string.history_clear_title else R.string.privacy_delete_keys))
            },
            text = {
                Text(stringResource(if (action == PendingAction.CLEAR_HISTORY) R.string.history_clear_text else R.string.privacy_delete_keys_text))
            },
            confirmButton = {
                TextButton(onClick = {
                    if (action == PendingAction.CLEAR_HISTORY) vm.clearHistory() else vm.removeAllKeys()
                    pending = null
                    scope.launch { snackbar.showSnackbar(doneMsg) }
                }) { Text(stringResource(R.string.history_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

@Composable
private fun DangerButton(res: Int, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.DeleteOutline, null, tint = MaterialTheme.colorScheme.error)
        Text("  " + stringResource(res), color = MaterialTheme.colorScheme.error)
    }
}
