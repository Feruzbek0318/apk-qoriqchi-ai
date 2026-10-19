package uz.apkqoriqchi.feature.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.FilterChip
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import uz.apkqoriqchi.ui.components.enterAnimation
import uz.apkqoriqchi.ui.theme.ThemeMode
import uz.apkqoriqchi.ui.theme.ThemePrefs
import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.apkqoriqchi.BuildConfig
import uz.apkqoriqchi.R
import uz.apkqoriqchi.core.data.ApiKey
import uz.apkqoriqchi.ui.components.AppTopBar
import uz.apkqoriqchi.ui.components.IconTile
import uz.apkqoriqchi.ui.components.SectionCard
import uz.apkqoriqchi.ui.components.SectionTitle
import uz.apkqoriqchi.ui.components.StatusPill
import uz.apkqoriqchi.ui.theme.RiskColors

private enum class SettingsPage { MAIN, STATUS, PRIVACY }

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    var page by rememberSaveable { mutableStateOf(SettingsPage.MAIN) }
    BackHandler(enabled = page != SettingsPage.MAIN) { page = SettingsPage.MAIN }
    AnimatedContent(
        targetState = page,
        transitionSpec = {
            val forward = targetState != SettingsPage.MAIN
            (slideInHorizontally(tween(300)) { if (forward) it / 5 else -it / 5 } + fadeIn(tween(300)))
                .togetherWith(fadeOut(tween(200)))
        },
        label = "settingsPage",
    ) { current ->
        when (current) {
            SettingsPage.MAIN -> SettingsMain(
                onBack = onBack,
                onOpenStatus = { page = SettingsPage.STATUS },
                onOpenPrivacy = { page = SettingsPage.PRIVACY },
            )
            SettingsPage.STATUS -> ServiceStatusScreen(onBack = { page = SettingsPage.MAIN })
            SettingsPage.PRIVACY -> PrivacyControlsScreen(onBack = { page = SettingsPage.MAIN })
        }
    }
}

@Composable
private fun SettingsMain(onBack: () -> Unit, onOpenStatus: () -> Unit, onOpenPrivacy: () -> Unit) {
    val vm: SettingsViewModel = hiltViewModel()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val tests by vm.tests.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { AppTopBar(stringResource(R.string.nav_settings), onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BrandHeader(Modifier.enterAnimation(0))

            SectionTitle(stringResource(R.string.settings_appearance), Modifier.enterAnimation(1))
            SectionCard(modifier = Modifier.enterAnimation(2)) {
                GroupLabel(Icons.Filled.Palette, stringResource(R.string.settings_theme))
                ThemePicker()
                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                GroupLabel(Icons.Filled.Language, stringResource(R.string.settings_language))
                LanguagePicker(vm)
            }

            SectionTitle(stringResource(R.string.settings_keys_title), Modifier.enterAnimation(3))
            SectionCard(modifier = Modifier.enterAnimation(3)) {
                Paragraph(R.string.settings_keys_body)
                ApiKey.entries.forEach { key ->
                    HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    KeyEditor(
                        key = key,
                        configured = ui.configured[key] == true,
                        testing = tests.containsKey(key) && tests[key] == null,
                        result = tests[key],
                        onSave = { vm.saveKey(key, it); vm.test(key) },
                        onRemove = { vm.removeKey(key) },
                        onTest = { vm.test(key) },
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                NavRow(Icons.Filled.NetworkCheck, stringResource(R.string.status_title), stringResource(R.string.status_entry_body), onOpenStatus)
            }

            SectionTitle(stringResource(R.string.settings_ai_section), Modifier.enterAnimation(4))
            SectionCard(modifier = Modifier.enterAnimation(4)) {
                SwitchRow(
                    stringResource(R.string.settings_ai),
                    stringResource(R.string.settings_ai_body),
                    ui.prefs.aiExplanations,
                    vm::setAiExplanations,
                )
            }

            SectionTitle(stringResource(R.string.settings_scanning), Modifier.enterAnimation(5))
            SectionCard(modifier = Modifier.enterAnimation(6)) {
                SwitchRow(
                    stringResource(R.string.settings_allow_upload),
                    stringResource(R.string.settings_allow_upload_body),
                    ui.prefs.allowFileUpload,
                    vm::setAllowUpload,
                )
                AnimatedVisibility(ui.prefs.allowFileUpload) { WarningNote(R.string.settings_upload_warning) }
                HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
                NotifyToggle(ui.notifyOnScan, vm::setNotifyOnScan)
            }

            SectionTitle(stringResource(R.string.settings_privacy), Modifier.enterAnimation(7))
            SectionCard(modifier = Modifier.enterAnimation(8)) {
                NavRow(Icons.Filled.Lock, stringResource(R.string.privacy_title), stringResource(R.string.settings_privacy_body), onOpenPrivacy)
            }

            SectionTitle(stringResource(R.string.settings_about), Modifier.enterAnimation(9))
            SectionCard(modifier = Modifier.enterAnimation(10)) {
                Paragraph(R.string.settings_about_disclaimer)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun BrandHeader(modifier: Modifier = Modifier) {
    SectionCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painterResource(R.drawable.logo_full), contentDescription = null,
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)),
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun GroupLabel(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun ThemePicker() {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(ThemePrefs.get(context)) }
    val options = listOf(
        Triple(ThemeMode.SYSTEM, Icons.Filled.BrightnessAuto, R.string.theme_system),
        Triple(ThemeMode.LIGHT, Icons.Filled.LightMode, R.string.theme_light),
        Triple(ThemeMode.DARK, Icons.Filled.DarkMode, R.string.theme_dark),
    )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (m, icon, label) ->
            SegmentedButton(
                selected = mode == m,
                onClick = { if (mode != m) { mode = m; ThemePrefs.set(context, m) } },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                icon = { Icon(icon, null, Modifier.size(18.dp)) },
            ) { Text(stringResource(label), maxLines = 1) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguagePicker(vm: SettingsViewModel) {
    var current by remember { mutableStateOf(vm.currentLanguage()) }
    val labels = mapOf(
        AppLanguage.UZ to "O‘zbek",
        AppLanguage.RU to "Русский",
        AppLanguage.EN to "English",
        AppLanguage.SYSTEM to stringResource(R.string.settings_language_system),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppLanguage.entries.forEach { lang ->
            FilterChip(
                selected = current == lang,
                onClick = { if (current != lang) { current = lang; vm.setLanguage(lang) } },
                label = { Text(labels.getValue(lang)) },
                leadingIcon = if (current == lang) { { Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) } } else null,
            )
        }
    }
}

/** Status, entry field, connection test and removal for one provider key. The saved key is never shown again. */
@Composable
private fun KeyEditor(
    key: ApiKey,
    configured: Boolean,
    testing: Boolean,
    result: TestResult?,
    onSave: (String) -> Unit,
    onRemove: () -> Unit,
    onTest: () -> Unit,
) {
    val info = key.info
    val uriHandler = LocalUriHandler.current
    var editing by rememberSaveable(key) { mutableStateOf(false) }
    var input by rememberSaveable(key) { mutableStateOf("") }
    var visible by rememberSaveable(key) { mutableStateOf(false) }
    var showHelp by rememberSaveable(key) { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconTile(info.icon, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Text(info.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        if (configured) StatusPill(stringResource(R.string.settings_key_saved), RiskColors.Clean)
        else StatusPill(stringResource(R.string.common_not_configured), MaterialTheme.colorScheme.onSurfaceVariant)
    }

    AnimatedContent(targetState = testing to result, label = "keyStatus") { (isTesting, r) ->
        when {
            isTesting -> Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.status_testing), style = MaterialTheme.typography.bodyMedium)
            }
            r != null && configured -> Column(Modifier.padding(top = 10.dp)) { TestResultView(r) }
            else -> Spacer(Modifier.height(1.dp))
        }
    }

    AnimatedVisibility(!configured || editing) {
        Column(Modifier.padding(top = 10.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.trim() },
                label = { Text(stringResource(if (configured) R.string.settings_key_replace else R.string.settings_key_label)) },
                singleLine = true,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                trailingIcon = {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(
                            if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            stringResource(if (visible) R.string.settings_key_hide else R.string.settings_key_show),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onSave(input); input = ""; visible = false; editing = false },
                    enabled = input.isNotBlank(),
                ) { Text(stringResource(R.string.settings_key_save)) }
                if (configured) TextButton(onClick = { input = ""; editing = false }) { Text(stringResource(android.R.string.cancel)) }
            }
        }
    }

    if (configured && !editing) {
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onTest, enabled = !testing) { Text(stringResource(R.string.status_test)) }
            TextButton(onClick = { editing = true }) { Text(stringResource(R.string.settings_key_replace_short)) }
            TextButton(onClick = onRemove) { Text(stringResource(R.string.settings_key_remove), color = RiskColors.Critical) }
        }
    }

    TextButton(onClick = { showHelp = !showHelp }) { Text(stringResource(R.string.settings_get_key)) }
    AnimatedVisibility(showHelp) {
        Column {
            Text(stringResource(info.helpRes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { uriHandler.openUri(info.keyUrl) }, modifier = Modifier.padding(top = 6.dp)) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.settings_key_open_site))
            }
        }
    }
}

/** Notification toggle; on Android 13+ asks for POST_NOTIFICATIONS only when the user turns it on. */
@Composable
internal fun NotifyToggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        onChange(granted)
    }
    SwitchRow(
        stringResource(R.string.settings_notify),
        stringResource(R.string.settings_notify_body),
        checked,
    ) { enable ->
        if (enable && Build.VERSION.SDK_INT >= 33 && !ScanNotifier.canPost(context)) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            denied = false
            onChange(enable)
        }
    }
    if (denied) WarningNote(R.string.settings_notify_denied)
}

@Composable
internal fun WarningNote(res: Int) {
    Row(Modifier.padding(vertical = 6.dp)) {
        Icon(Icons.Filled.Warning, null, tint = RiskColors.Medium, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(res), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}
