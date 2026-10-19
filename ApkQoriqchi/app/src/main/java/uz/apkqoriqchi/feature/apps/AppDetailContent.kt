package uz.apkqoriqchi.feature.apps

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uz.apkqoriqchi.R
import uz.apkqoriqchi.core.model.RiskLevel
import uz.apkqoriqchi.core.model.color
import uz.apkqoriqchi.feature.ai.AiExplanationCard

@Composable
fun AppDetailContent(app: AppAuditResult, risk: RiskLevel, reputation: ReputationState, onCheckReputation: () -> Unit) {
    val context = LocalContext.current
    val pm = context.packageManager
    val permissionLabels = remember(app.packageName, app.grantedDangerous) { app.grantedDangerous.map { pm.permissionLabel(it) } }
    val facts = remember(app, reputation) {
        app.factsText() + when (reputation) {
            is ReputationState.Detections -> "\nVirusTotal: ${reputation.malicious}/${reputation.total} engines flagged base.apk"
            ReputationState.NoReport -> "\nVirusTotal: no existing report for base.apk hash"
            else -> "\nReputation lookup: not performed"
        }
    }
    fun launch(intent: Intent) = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) { } catch (_: SecurityException) { }
    val pkgUri = Uri.fromParts("package", app.packageName, null)

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app.packageName, size = 56.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(app.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RiskBadge(risk)
        }

        Section {
            InfoRow(stringResource(R.string.apps_version), listOfNotNull(app.versionName, "(${app.versionCode})").joinToString(" "))
            InfoRow(stringResource(R.string.apps_installed), formatDate(app.firstInstallTime))
            InfoRow(stringResource(R.string.apps_updated), formatDate(app.lastUpdateTime))
            InfoRow(stringResource(R.string.apps_target_sdk), app.targetSdk.toString())
            InfoRow(stringResource(R.string.apps_installer), app.installer ?: stringResource(R.string.apps_installer_unknown))
            InfoRow(stringResource(R.string.apps_type), stringResource(if (app.isSystem) R.string.apps_type_system else R.string.apps_type_user))
            Column {
                Text(stringResource(R.string.apps_signer), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                Text(
                    app.signerSha256?.chunked(2)?.joinToString(":")?.uppercase() ?: stringResource(R.string.apps_signer_unavailable),
                    style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                )
            }
        }

        Section(stringResource(R.string.apps_reputation)) {
            val (text, color) = when (reputation) {
                ReputationState.NotChecked -> stringResource(R.string.apps_rep_not_checked) to MaterialTheme.colorScheme.onSurfaceVariant
                ReputationState.Checking -> stringResource(R.string.apps_vt_checking) to MaterialTheme.colorScheme.onSurfaceVariant
                ReputationState.NotConfigured -> stringResource(R.string.apps_vt_not_configured) to MaterialTheme.colorScheme.onSurfaceVariant
                ReputationState.NoReport -> stringResource(R.string.apps_vt_not_found) to MaterialTheme.colorScheme.onSurfaceVariant
                is ReputationState.Detections -> stringResource(R.string.apps_vt_result, reputation.malicious, reputation.total) to
                    (if (reputation.malicious > 0) risk.color else MaterialTheme.colorScheme.onSurface)
                is ReputationState.Failed -> stringResource(R.string.apps_vt_failed, reputation.reason) to MaterialTheme.colorScheme.error
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (reputation == ReputationState.Checking) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(text, color = color, style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedButton(onClick = onCheckReputation, enabled = reputation != ReputationState.Checking) {
                Icon(Icons.Default.TravelExplore, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.apps_vt_check))
            }
            Text(stringResource(R.string.apps_vt_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Section(stringResource(R.string.apps_signals)) {
            if (app.findings.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Info, null, tint = risk.color)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.apps_no_signals), style = MaterialTheme.typography.bodyMedium)
                }
            }
            app.findings.forEachIndexed { i, finding ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Row {
                    Icon(Icons.Outlined.WarningAmber, null, tint = risk.color, modifier = Modifier.padding(top = 2.dp).size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(stringResource(finding.signal.titleRes), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(finding.signal.descRes), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Section(stringResource(R.string.apps_permissions_title, app.grantedDangerous.size)) {
            if (permissionLabels.isEmpty()) Text(stringResource(R.string.apps_no_permissions), style = MaterialTheme.typography.bodyMedium)
            permissionLabels.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri)) },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Settings, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.apps_open_settings))
            }
            Button(
                onClick = { launch(Intent(Intent.ACTION_DELETE, pkgUri)) },
                modifier = Modifier.weight(1f),
                enabled = !app.isSystem,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(Icons.Default.Delete, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.apps_uninstall))
            }
        }

        AiExplanationCard(facts = facts, verdict = risk)
    }
}

@Composable
private fun Section(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            title?.let { Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun formatDate(millis: Long): String {
    val context = LocalContext.current
    return remember(millis) {
        "${DateFormat.getMediumDateFormat(context).format(millis)} ${DateFormat.getTimeFormat(context).format(millis)}"
    }
}
