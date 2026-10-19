package uz.apkqoriqchi.feature.history

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import uz.apkqoriqchi.R
import uz.apkqoriqchi.core.model.ScanRecord
import uz.apkqoriqchi.core.model.ScanType
import uz.apkqoriqchi.core.model.color
import uz.apkqoriqchi.ui.components.AppTopBar
import uz.apkqoriqchi.ui.components.IconTile
import uz.apkqoriqchi.ui.components.RiskBadge
import uz.apkqoriqchi.ui.components.icon

@Composable
fun HistoryScreen(onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val vm: HistoryViewModel = hiltViewModel()
    val ui by vm.ui.collectAsStateWithLifecycle()
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ScanRecord?>(null) }

    Scaffold(
        topBar = {
            AppTopBar(stringResource(R.string.history_title), onBack) {
                if (ui.total > 0) IconButton(onClick = { confirmClear = true }) {
                    Icon(Icons.Filled.DeleteSweep, stringResource(R.string.history_clear_all))
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(listOf<ScanType?>(null) + ScanType.entries) { type ->
                    FilterChip(
                        selected = ui.filter == type,
                        onClick = { vm.setFilter(type) },
                        label = { Text(stringResource(type?.labelRes ?: R.string.history_filter_all)) },
                    )
                }
            }
            when {
                ui.loading -> Unit
                ui.groups.isEmpty() -> EmptyHistory(filtered = ui.total > 0)
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ui.groups.forEach { (group, records) ->
                        item(key = "h_$group") {
                            Text(
                                dayLabel(group),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                            )
                        }
                        items(records, key = { it.id }) { r ->
                            ScanRecordRow(r, onClick = { onOpen(r.id) }, onLongClick = { pendingDelete = r })
                        }
                    }
                    item {
                        Text(
                            stringResource(R.string.history_long_press_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }

    if (confirmClear) ConfirmDialog(
        title = stringResource(R.string.history_clear_title),
        text = stringResource(R.string.history_clear_text),
        confirm = stringResource(R.string.history_clear_confirm),
        onConfirm = { vm.clearAll(); confirmClear = false },
        onDismiss = { confirmClear = false },
    )
    pendingDelete?.let { r ->
        ConfirmDialog(
            title = stringResource(R.string.history_delete_title),
            text = r.target,
            confirm = stringResource(R.string.history_delete),
            onConfirm = { vm.delete(r.id); pendingDelete = null },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun EmptyHistory(filtered: Boolean) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        IconTile(Icons.Filled.History, size = 64.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(if (filtered) R.string.history_empty_filtered else R.string.history_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** One scan in a list: type icon, target, risk badge and time. Shared with the home screen. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScanRecordRow(record: ScanRecord, onClick: () -> Unit, onLongClick: (() -> Unit)? = null, showDate: Boolean = false) {
    val haptic = LocalHapticFeedback.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick?.let { { haptic.performHapticFeedback(HapticFeedbackType.LongPress); it() } },
                )
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(record.type.icon, tint = record.riskLevel.color, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(record.target, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(record.type.labelRes) + " · " +
                        if (showDate) formatDateTime(record.createdAt) else formatTime(record.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            RiskBadge(record.riskLevel)
        }
    }
}

@Composable
internal fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(title) },
        text = { Text(text, maxLines = 4, overflow = TextOverflow.Ellipsis) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirm, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@get:StringRes
val ScanType.labelRes: Int
    get() = when (this) {
        ScanType.APK -> R.string.history_type_apk
        ScanType.URL -> R.string.history_type_url
        ScanType.INSTALLED_APP -> R.string.history_type_app
    }

@Composable
private fun dayLabel(group: DayGroup): String = when (group) {
    DayGroup.Today -> stringResource(R.string.history_today)
    DayGroup.Yesterday -> stringResource(R.string.history_yesterday)
    is DayGroup.Date -> DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(currentLocale()).format(group.date)
}

@Composable
private fun currentLocale(): Locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()

@Composable
fun formatTime(ms: Long): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(currentLocale())
        .format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

@Composable
fun formatDateTime(ms: Long): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(currentLocale())
        .format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

