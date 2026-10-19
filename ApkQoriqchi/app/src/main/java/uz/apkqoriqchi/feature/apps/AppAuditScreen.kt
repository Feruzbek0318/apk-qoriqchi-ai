package uz.apkqoriqchi.feature.apps

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.apkqoriqchi.R
import uz.apkqoriqchi.core.model.RiskLevel
import uz.apkqoriqchi.core.model.color
import uz.apkqoriqchi.core.model.labelRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppAuditScreen(onBack: () -> Unit) {
    val vm: AppAuditViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val selected = state.selected

    BackHandler(enabled = state.selectedPackage != null) { vm.closeDetail() }
    // Returning from the system uninstall dialog: drop removed apps (no background re-audit).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (state.loaded) vm.pruneUninstalled() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (selected != null) R.string.apps_detail_title else R.string.apps_title)) },
                navigationIcon = {
                    IconButton(onClick = { if (state.selectedPackage != null) vm.closeDetail() else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                    }
                },
                actions = {
                    if (selected == null) IconButton(onClick = { vm.load() }, enabled = !state.loading) {
                        Icon(Icons.Default.Refresh, stringResource(R.string.apps_refresh))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                selected != null -> AppDetailContent(
                    app = selected,
                    risk = state.effectiveRisk(selected),
                    reputation = state.reputationOf(selected.packageName),
                    onCheckReputation = { vm.checkReputation(selected) },
                )
                state.loading -> Column(
                    Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.size(16.dp))
                    Text(stringResource(R.string.apps_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                state.error != null -> Column(
                    Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.apps_error, state.error.orEmpty()), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { vm.load() }) { Text(stringResource(R.string.common_retry)) }
                }
                else -> AppList(state, vm)
            }
        }
    }
}

@Composable
private fun AppList(state: AppAuditUiState, vm: AppAuditViewModel) {
    val visible = state.visible
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { SummaryHeader(state) }
        item { ScopeNotice() }
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.apps_search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(AppFilter.entries) { f ->
                    FilterChip(selected = state.filter == f, onClick = { vm.setFilter(f) }, label = { Text(stringResource(f.labelRes)) })
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = state.includeSystem, onCheckedChange = vm::setIncludeSystem)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.apps_show_system), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = vm::toggleSort) {
                    Icon(Icons.AutoMirrored.Filled.Sort, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(if (state.sortByRisk) R.string.apps_sort_risk else R.string.apps_sort_name))
                }
            }
        }
        if (visible.isEmpty()) item {
            Text(
                stringResource(R.string.apps_empty), Modifier.fillMaxWidth().padding(32.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(visible, key = { it.packageName }) { app -> AppRow(app, state.effectiveRisk(app)) { vm.openDetail(app) } }
    }
}

@Composable
private fun SummaryHeader(state: AppAuditUiState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.apps_summary_total, state.apps.size),
                    Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.apps_summary_sideloaded, state.sideloadedCount),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (state.sideloadedCount > 0) RiskLevel.MEDIUM.color else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val counts = state.counts
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(RiskLevel.CRITICAL, RiskLevel.HIGH, RiskLevel.MEDIUM, RiskLevel.LOW, RiskLevel.NO_DETECTIONS).forEach { level ->
                    Column(
                        Modifier.weight(1f).background(level.color.copy(alpha = 0.12f), RoundedCornerShape(12.dp)).padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("${counts[level] ?: 0}", color = level.color, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(level.labelRes), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScopeNotice() {
    Row(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            .padding(12.dp),
    ) {
        Icon(Icons.Outlined.Info, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.apps_scope_notice), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AppRow(app: AppAuditResult, risk: RiskLevel, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app.packageName)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(app.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    app.packageName, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).background(risk.color, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(app.shortReason(), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            RiskBadge(risk)
        }
    }
}

@Composable
private fun AppAuditResult.shortReason(): String {
    val first = findings.firstOrNull() ?: return stringResource(R.string.apps_no_reason)
    val title = stringResource(first.signal.titleRes)
    return if (findings.size > 1) "$title · ${stringResource(R.string.apps_more_signals, findings.size - 1)}" else title
}

private val AppFilter.labelRes: Int
    get() = when (this) {
        AppFilter.ALL -> R.string.apps_filter_all
        AppFilter.RISKY -> R.string.apps_filter_risky
        AppFilter.SIDELOADED -> R.string.apps_filter_sideloaded
        AppFilter.SPECIAL_ACCESS -> R.string.apps_filter_special
    }
