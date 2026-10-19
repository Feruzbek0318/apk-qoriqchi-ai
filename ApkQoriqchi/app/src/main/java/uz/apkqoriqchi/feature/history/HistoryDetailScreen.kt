package uz.apkqoriqchi.feature.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch
import uz.apkqoriqchi.R
import uz.apkqoriqchi.core.model.RiskLevel
import uz.apkqoriqchi.core.model.ScanRecord
import uz.apkqoriqchi.core.model.color
import uz.apkqoriqchi.core.model.labelRes
import uz.apkqoriqchi.ui.components.AppTopBar
import uz.apkqoriqchi.ui.components.IconTile
import uz.apkqoriqchi.ui.components.SectionCard
import uz.apkqoriqchi.ui.components.icon
import uz.apkqoriqchi.ui.theme.MonoStyle
import uz.apkqoriqchi.ui.theme.RiskColors

private sealed interface Loaded {
    data object Loading : Loaded
    data class Value(val record: ScanRecord?) : Loaded
}

@Composable
fun HistoryDetailScreen(id: Long, onBack: () -> Unit) {
    val vm: HistoryViewModel = hiltViewModel()
    val flow = remember(id) { vm.observe(id) }
    val loaded by remember(flow) {
        kotlinx.coroutines.flow.flow<Loaded> { flow.collect { emit(Loaded.Value(it)) } }
    }.collectAsState(Loaded.Loading)
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var deleted by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    // Leave the screen once the record is gone (deleted here or elsewhere).
    LaunchedEffect(loaded, deleted) {
        if (deleted && (loaded as? Loaded.Value)?.record == null) onBack()
    }

    Scaffold(
        topBar = {
            AppTopBar(stringResource(R.string.history_detail_title), onBack) {
                if ((loaded as? Loaded.Value)?.record != null) IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.DeleteOutline, stringResource(R.string.history_delete))
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val record = (loaded as? Loaded.Value)?.record
        when {
            loaded is Loaded.Loading -> Unit
            record == null -> Text(
                stringResource(R.string.history_not_found),
                Modifier.padding(padding).padding(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> DetailContent(record, snackbar, Modifier.padding(padding))
        }
    }

    if (confirmDelete) {
        val record = (loaded as? Loaded.Value)?.record
        ConfirmDialog(
            title = stringResource(R.string.history_delete_title),
            text = record?.target.orEmpty(),
            confirm = stringResource(R.string.history_delete),
            onConfirm = { vm.delete(id); deleted = true; confirmDelete = false },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun DetailContent(record: ScanRecord, snackbar: SnackbarHostState, modifier: Modifier) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val copiedMsg = stringResource(R.string.history_copied)
    var showTech by rememberSaveable { mutableStateOf(false) }
    val risk = record.riskLevel

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Verdict header, tinted with the risk colour like the design's result cards.
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(risk.color.copy(alpha = 0.12f)).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconTile(record.type.icon, tint = risk.color, size = 64.dp)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(risk.labelRes), style = MaterialTheme.typography.headlineSmall, color = risk.color)
            if (record.summary.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(record.summary, style = MaterialTheme.typography.bodyMedium)
            }
            if (risk == RiskLevel.NO_DETECTIONS) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.disclaimer_not_safe),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val meta = remember(record.detailsJson) { parseDetailMeta(record.detailsJson) }
        if (meta.incomplete) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(RiskColors.Medium.copy(alpha = 0.14f)).padding(14.dp),
            ) {
                Icon(Icons.Filled.Warning, null, tint = RiskColors.Medium)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(stringResource(R.string.history_incomplete), style = MaterialTheme.typography.titleSmall)
                    Text(
                        meta.incompleteReason ?: stringResource(R.string.history_incomplete_body),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        if (meta.providerStatus.isNotEmpty()) SectionCard {
            Text(stringResource(R.string.history_provider_status), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            meta.providerStatus.forEach { (name, status) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    if (name.isNotEmpty()) Text(
                        name,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(0.4f),
                    )
                    Text(
                        status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(0.6f),
                    )
                }
            }
        }

        SectionCard {
            InfoRow(stringResource(R.string.history_field_type), stringResource(record.type.labelRes))
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            InfoRow(stringResource(R.string.history_field_target), record.target)
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            InfoRow(stringResource(R.string.history_field_date), formatDateTime(record.createdAt))
            record.sha256?.let { hash ->
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("SHA-256", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SelectionContainer { Text(hash, style = MonoStyle) }
                    }
                    IconButton(onClick = {
                        clipboard.setText(AnnotatedString(hash))
                        scope.launch { snackbar.showSnackbar(copiedMsg) }
                    }) { Icon(Icons.Filled.ContentCopy, stringResource(R.string.history_copy)) }
                }
            }
        }

        if (!isEmptyJson(record.detailsJson)) SectionCard(padding = 0.dp) {
            Row(
                Modifier.fillMaxWidth().clickable { showTech = !showTech }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.history_technical), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Icon(if (showTech) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
            }
            AnimatedVisibility(showTech) {
                val pretty = remember(record.detailsJson) { prettyPrintJson(record.detailsJson) }
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    SelectionContainer {
                        Text(pretty, style = MonoStyle, modifier = Modifier.horizontalScroll(rememberScrollState()))
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        clipboard.setText(AnnotatedString(pretty))
                        scope.launch { snackbar.showSnackbar(copiedMsg) }
                    }) {
                        Icon(Icons.Filled.ContentCopy, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.history_copy))
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer { Text(value, style = MaterialTheme.typography.bodyMedium) }
    }
}
