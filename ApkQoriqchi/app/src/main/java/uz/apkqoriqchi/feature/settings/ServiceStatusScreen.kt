package uz.apkqoriqchi.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.apkqoriqchi.R
import uz.apkqoriqchi.core.data.ApiKey
import uz.apkqoriqchi.ui.components.AppTopBar
import uz.apkqoriqchi.ui.components.IconTile
import uz.apkqoriqchi.ui.components.SectionCard
import uz.apkqoriqchi.ui.components.StatusPill
import uz.apkqoriqchi.ui.theme.RiskColors

/** Per-provider configuration status, a real "test connection" call and troubleshooting tips. */
@Composable
fun ServiceStatusScreen(onBack: () -> Unit, onManageKeys: (() -> Unit)? = null) {
    val vm: SettingsViewModel = hiltViewModel()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val tests by vm.tests.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { AppTopBar(stringResource(R.string.status_title), onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Paragraph(R.string.status_intro)
            ApiKey.entries.forEach { key ->
                val configured = ui.configured[key] == true
                val testing = tests.containsKey(key) && tests[key] == null
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(key.info.icon, size = 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(key.info.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        if (configured) StatusPill(stringResource(R.string.settings_key_saved), RiskColors.Clean)
                        else StatusPill(stringResource(R.string.common_not_configured), MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(10.dp))
                    tests[key]?.let { TestResultView(it) }
                    if (testing) Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.status_testing), style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { vm.test(key) }, enabled = configured && !testing) {
                        Text(stringResource(R.string.status_test))
                    }
                }
            }
            SectionCard {
                Text(stringResource(R.string.status_fixes_title), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                listOf(R.string.status_fix_key, R.string.status_fix_quota, R.string.status_fix_offline, R.string.status_fix_gsb)
                    .forEach { Text("• " + stringResource(it), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp)) }
            }
            if (onManageKeys != null) Button(onClick = onManageKeys) { Text(stringResource(R.string.status_manage_keys)) }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
internal fun TestResultView(r: TestResult) {
    val (color, res) = when (r.outcome) {
        TestOutcome.OK -> RiskColors.Clean to R.string.status_ok
        TestOutcome.NOT_CONFIGURED -> MaterialTheme.colorScheme.onSurfaceVariant to R.string.common_not_configured
        TestOutcome.BAD_KEY -> RiskColors.Critical to R.string.status_bad_key
        TestOutcome.QUOTA -> RiskColors.Medium to R.string.status_quota
        TestOutcome.OFFLINE -> RiskColors.Medium to R.string.status_offline
        TestOutcome.SERVER -> RiskColors.Medium to R.string.status_server
        TestOutcome.OTHER -> RiskColors.High to R.string.status_other
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusPill(stringResource(res), color)
            r.httpCode?.let {
                Spacer(Modifier.width(8.dp))
                Text("HTTP $it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        r.message?.takeIf { r.outcome != TestOutcome.OK }?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

