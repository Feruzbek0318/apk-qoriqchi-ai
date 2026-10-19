package uz.apkqoriqchi.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import uz.apkqoriqchi.feature.settings.ServiceStatusScreen
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.apkqoriqchi.R
import uz.apkqoriqchi.core.data.ApiKey
import uz.apkqoriqchi.feature.history.ScanRecordRow
import uz.apkqoriqchi.feature.history.formatDateTime
import uz.apkqoriqchi.ui.components.IconTile
import uz.apkqoriqchi.ui.components.SectionCard
import uz.apkqoriqchi.ui.components.SectionTitle
import uz.apkqoriqchi.ui.components.enterAnimation
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.res.painterResource
import uz.apkqoriqchi.ui.components.StatusPill
import uz.apkqoriqchi.ui.navigation.Routes
import uz.apkqoriqchi.ui.theme.RiskColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onNavigate: (String) -> Unit) {
    val vm: HomeViewModel = hiltViewModel()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val onboardingDone by vm.onboardingDone.collectAsStateWithLifecycle()
    var showStatus by rememberSaveable { mutableStateOf(false) }

    when (onboardingDone) {
        null -> return
        false -> { OnboardingScreen(onDone = vm::finishOnboarding); return }
        true -> Unit
    }
    if (showStatus) {
        BackHandler { showStatus = false }
        ServiceStatusScreen(onBack = { showStatus = false }, onManageKeys = { onNavigate(Routes.SETTINGS) })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.logo_shield), null, Modifier.size(30.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.app_name))
                    }
                },
                actions = {
                    IconButton(onClick = { onNavigate(Routes.SETTINGS) }) {
                        Icon(Icons.Filled.Settings, stringResource(R.string.nav_settings))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.enterAnimation(0)) { Hero(lastScanAt = ui.lastScanAt, total = ui.totalScans) }

            Row(Modifier.enterAnimation(1).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                EntryCard(Icons.Filled.Android, R.string.home_apk_title, R.string.home_apk_body, Modifier.weight(1f).fillMaxHeight()) {
                    onNavigate(Routes.APK)
                }
                EntryCard(Icons.Filled.Link, R.string.home_url_title, R.string.home_url_body, Modifier.weight(1f).fillMaxHeight()) {
                    onNavigate(Routes.URL)
                }
            }
            EntryCard(Icons.Filled.Apps, R.string.home_apps_title, R.string.home_apps_body, Modifier.enterAnimation(2).fillMaxWidth(), wide = true) {
                onNavigate(Routes.APPS)
            }

            SectionTitle(stringResource(R.string.home_recent), Modifier.enterAnimation(3)) {
                if (ui.totalScans > 0) TextButton(onClick = { onNavigate(Routes.HISTORY) }) {
                    Text(stringResource(R.string.home_see_all))
                }
            }
            if (!ui.loading && ui.recent.isEmpty()) {
                SectionCard {
                    Text(
                        stringResource(R.string.home_recent_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            ui.recent.forEach { r ->
                ScanRecordRow(r, onClick = { onNavigate(Routes.historyDetail(r.id)) }, showDate = true)
            }

            SectionTitle(stringResource(R.string.home_services), Modifier.enterAnimation(4))
            SectionCard(onClick = { showStatus = true }, modifier = Modifier.enterAnimation(5)) {
                ServiceRow(Icons.Filled.VerifiedUser, "VirusTotal", ui.configured[ApiKey.VIRUSTOTAL] == true)
                Spacer(Modifier.height(10.dp))
                ServiceRow(Icons.Filled.Public, "Google Safe Browsing", ui.configured[ApiKey.SAFE_BROWSING] == true)
                Spacer(Modifier.height(10.dp))
                ServiceRow(Icons.Filled.AutoAwesome, "Gemini AI", ui.configured[ApiKey.GEMINI] == true)
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.home_services_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionTitle(stringResource(R.string.home_tips), Modifier.enterAnimation(6))
            SectionCard(modifier = Modifier.enterAnimation(7)) {
                listOf(R.string.home_tip_1, R.string.home_tip_2, R.string.home_tip_3, R.string.home_tip_4).forEachIndexed { i, tip ->
                    if (i > 0) Spacer(Modifier.height(10.dp))
                    Row {
                        Icon(
                            Icons.Filled.Info, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp).padding(top = 2.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(tip), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Hero(lastScanAt: Long?, total: Int) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(scheme.primaryContainer, scheme.surfaceContainerHigh)))
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.logo_shield), null, Modifier.size(60.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.home_hero_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.home_hero_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            if (lastScanAt != null) stringResource(R.string.home_last_scan, formatDateTime(lastScanAt))
            else stringResource(R.string.home_no_scans_yet),
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSurface,
        )
        if (total > 0) Text(
            stringResource(R.string.home_total_scans, total),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EntryCard(
    icon: ImageVector,
    title: Int,
    body: Int,
    modifier: Modifier,
    wide: Boolean = false,
    onClick: () -> Unit,
) {
    SectionCard(modifier, onClick = onClick) {
        if (wide) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(icon, size = 44.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) { EntryText(title, body) }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            IconTile(icon, size = 44.dp)
            Spacer(Modifier.height(14.dp))
            EntryText(title, body)
        }
    }
}

@Composable
private fun EntryText(title: Int, body: Int) {
    Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(2.dp))
    Text(stringResource(body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ServiceRow(icon: ImageVector, name: String, configured: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, size = 32.dp)
        Spacer(Modifier.width(12.dp))
        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (configured) StatusPill(stringResource(R.string.home_service_ready), RiskColors.Clean)
        else StatusPill(stringResource(R.string.common_not_configured), MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
