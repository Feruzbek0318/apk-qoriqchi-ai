package uz.apkqoriqchi.feature.url

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch
import uz.apkqoriqchi.R
import uz.apkqoriqchi.core.model.ProviderResult
import uz.apkqoriqchi.core.model.RiskLevel
import uz.apkqoriqchi.core.model.color
import uz.apkqoriqchi.core.model.labelRes
import uz.apkqoriqchi.feature.ai.AiExplanationCard
import uz.apkqoriqchi.ui.theme.RiskColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UrlCheckScreen(onBack: () -> Unit) {
    val vm: UrlCheckViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.url_title), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            InputCard(state, vm::onInput, vm::check)
            val target = state.target
            if (target != null) {
                VerdictCard(state)
                ProvidersSection(state, vm::submitToVt)
                (state.vt as? ProviderResult.Success)?.data?.finalUrl?.takeIf { it.trimEnd('/') != target.url.trimEnd('/') }?.let { FinalUrlCard(it) }
                FindingsSection(state.findings)
                state.risk?.let { risk ->
                    ActionsRow(target.url, risk, onNew = { vm.onInput(""); vm.reset() })
                    state.facts?.let { AiExplanationCard(facts = it, verdict = risk) }
                }
            }
            Text(
                stringResource(R.string.url_sb_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionCard(modifier: Modifier = Modifier, border: Color? = null, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth().let { m -> border?.let { m.border(1.dp, it.copy(alpha = 0.5f), RoundedCornerShape(20.dp)) } ?: m },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() } }
}

@Composable
private fun IconBadge(icon: ImageVector, tint: Color, size: Int = 44) {
    Box(
        Modifier.size(size.dp).background(tint.copy(alpha = 0.15f), CircleShape),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size((size * 0.55f).dp)) }
}

@Composable
private fun InputCard(state: UrlUiState, onInput: (String) -> Unit, onCheck: () -> Unit) {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Filled.Link, MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.url_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(
            value = state.input,
            onValueChange = onInput,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.url_input_label)) },
            placeholder = { Text(stringResource(R.string.url_input_hint)) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            isError = state.inputError != null,
            supportingText = state.inputError?.let { err -> { Text(errorText(err)) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Search, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onSearch = { onCheck() }),
            trailingIcon = {
                if (state.input.isEmpty()) {
                    IconButton(onClick = {
                        scope.launch {
                            val text = clipboard.getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }
                                ?.getItemAt(0)?.coerceToText(context)?.toString()
                            if (!text.isNullOrBlank()) onInput(text.trim())
                        }
                    }) { Icon(Icons.Filled.ContentPaste, stringResource(R.string.url_paste)) }
                } else {
                    IconButton(onClick = { onInput("") }) { Icon(Icons.Filled.Close, stringResource(R.string.url_clear)) }
                }
            },
        )
        Button(
            onClick = onCheck,
            enabled = !state.running,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp),
        ) { Text(stringResource(R.string.url_check), fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun errorText(err: UrlParseResult): String = when (err) {
    is UrlParseResult.UnsupportedScheme -> stringResource(R.string.url_err_scheme, err.scheme)
    UrlParseResult.Empty -> stringResource(R.string.url_err_empty)
    else -> stringResource(R.string.url_err_malformed)
}

@Composable
private fun VerdictCard(state: UrlUiState) {
    val risk = state.risk
    if (risk == null) {
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(36.dp), strokeWidth = 3.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(stringResource(R.string.url_checking), style = MaterialTheme.typography.titleMedium)
                    Text(state.target?.url.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        return
    }
    val icon = when (risk) {
        RiskLevel.CRITICAL, RiskLevel.HIGH -> Icons.Filled.GppBad
        RiskLevel.MEDIUM, RiskLevel.LOW -> Icons.Filled.GppMaybe
        RiskLevel.UNKNOWN -> Icons.AutoMirrored.Filled.HelpOutline
        RiskLevel.NO_DETECTIONS -> Icons.Filled.GppGood
    }
    val desc = when (risk) {
        RiskLevel.CRITICAL, RiskLevel.HIGH -> R.string.url_verdict_desc_high
        RiskLevel.MEDIUM -> R.string.url_verdict_desc_medium
        RiskLevel.LOW -> R.string.url_verdict_desc_low
        RiskLevel.UNKNOWN -> R.string.url_verdict_desc_unknown
        RiskLevel.NO_DETECTIONS -> R.string.url_verdict_desc_none
    }
    val localOnly = state.reputation !is ProviderResult.Success && state.vt !is ProviderResult.Success
    SectionCard(border = risk.color) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, risk.color, 56)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(stringResource(risk.labelRes), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = risk.color)
                Text(state.target?.url.orEmpty(), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(stringResource(desc), style = MaterialTheme.typography.bodyMedium)
        if (risk == RiskLevel.NO_DETECTIONS) {
            Text(stringResource(R.string.disclaimer_not_safe), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (localOnly && risk != RiskLevel.UNKNOWN) {
            Text(stringResource(R.string.url_local_only), style = MaterialTheme.typography.bodySmall, color = RiskColors.Medium)
        }
    }
}

@Composable
private fun ProvidersSection(state: UrlUiState, onSubmit: () -> Unit) {
    SectionTitle(R.string.url_providers)
    ProviderCard(state.reputationName, state.reputation) { matches ->
        val types = matches.flatMap { it.threatTypes }.distinct()
        if (types.isEmpty()) StatusLine(Icons.Filled.CheckCircle, RiskColors.Clean, stringResource(R.string.url_status_no_match))
        else StatusLine(Icons.Filled.GppBad, RiskColors.Critical, stringResource(R.string.url_status_threats, types.joinToString()))
    }
    ProviderCard(
        "VirusTotal",
        if (state.vtSubmitting) null else state.vt,
        loadingText = if (state.vtSubmitting) stringResource(R.string.url_vt_submitting) else null,
        notFoundExtra = {
            Text(stringResource(R.string.url_vt_submit_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onSubmit, shape = RoundedCornerShape(12.dp)) { Text(stringResource(R.string.url_vt_submit)) }
        },
    ) { r -> VtDetails(r) }
}

@Composable
private fun SectionTitle(@StringRes res: Int) =
    Text(stringResource(res), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))

@Composable
private fun StatusLine(icon: ImageVector, tint: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun <T> ProviderCard(
    name: String,
    result: ProviderResult<T>?,
    loadingText: String? = null,
    notFoundExtra: @Composable () -> Unit = {},
    success: @Composable (T) -> Unit,
) {
    SectionCard {
        Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        when (result) {
            null -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(loadingText ?: stringResource(R.string.url_status_loading), style = MaterialTheme.typography.bodyMedium)
            }
            is ProviderResult.Success -> success(result.data)
            ProviderResult.NotFound -> {
                StatusLine(Icons.Filled.SearchOff, RiskColors.Unknown, stringResource(R.string.url_status_not_found))
                notFoundExtra()
            }
            ProviderResult.NotConfigured -> StatusLine(Icons.Filled.Key, RiskColors.Unknown, stringResource(R.string.url_status_not_configured))
            is ProviderResult.Failed -> StatusLine(Icons.Filled.ErrorOutline, RiskColors.High, stringResource(R.string.url_status_failed, failText(result.reason)))
        }
    }
}

@Composable
private fun failText(code: String): String = when (code) {
    FailCode.AUTH -> stringResource(R.string.url_fail_auth)
    FailCode.QUOTA -> stringResource(R.string.url_fail_quota)
    FailCode.NETWORK -> stringResource(R.string.url_fail_network)
    FailCode.TIMEOUT -> stringResource(R.string.url_fail_timeout)
    else -> stringResource(R.string.url_fail_other, code)
}

@Composable
private fun VtDetails(r: VtUrlReport) {
    val s = r.stats
    val tint = when {
        s.malicious >= 2 -> RiskColors.Critical
        s.malicious == 1 || s.suspicious > 0 -> RiskColors.Medium
        else -> RiskColors.Clean
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("${s.malicious}/${s.total}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = tint)
        Spacer(Modifier.width(12.dp))
        Text(stringResource(R.string.url_vt_stats, s.malicious, s.total), style = MaterialTheme.typography.bodyMedium)
    }
    if (s.total > 0) {
        LinearProgressIndicator(
            progress = { s.malicious.toFloat() / s.total },
            modifier = Modifier.fillMaxWidth(),
            color = tint,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Text(stringResource(R.string.url_vt_suspicious, s.suspicious, s.harmless, s.undetected), style = MaterialTheme.typography.bodySmall, color = muted)
    r.hits.take(8).forEach { hit ->
        StatusLine(
            if (hit.category == "malicious") Icons.Filled.GppBad else Icons.Filled.Warning,
            if (hit.category == "malicious") RiskColors.Critical else RiskColors.Medium,
            "${hit.engine}: ${hit.result ?: hit.category}",
        )
    }
    if (r.categories.isNotEmpty()) Text(stringResource(R.string.url_vt_categories, r.categories.take(4).joinToString()), style = MaterialTheme.typography.bodySmall, color = muted)
    r.reputation?.let { Text(stringResource(R.string.url_vt_reputation, it), style = MaterialTheme.typography.bodySmall, color = muted) }
    r.lastAnalysisDate?.let {
        val date = remember(it) { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it * 1000)) }
        Text(stringResource(R.string.url_vt_date, date), style = MaterialTheme.typography.bodySmall, color = muted)
    }
}

@Composable
private fun FinalUrlCard(finalUrl: String) {
    SectionCard {
        StatusLine(Icons.AutoMirrored.Filled.OpenInNew, MaterialTheme.colorScheme.primary, stringResource(R.string.url_final_url))
        Text(finalUrl, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FindingsSection(findings: List<HeuristicFinding>) {
    SectionTitle(R.string.url_findings)
    SectionCard {
        if (findings.isEmpty()) {
            StatusLine(Icons.Filled.CheckCircle, RiskColors.Clean, stringResource(R.string.url_findings_none))
        }
        findings.forEachIndexed { i, f ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            val tint = when (f.severity) {
                Severity.HIGH -> RiskColors.High
                Severity.MEDIUM -> RiskColors.Medium
                Severity.LOW -> RiskColors.Low
            }
            val (title, desc) = f.type.texts
            Row {
                IconBadge(if (f.severity == Severity.LOW) Icons.Filled.Info else Icons.Filled.Warning, tint, 32)
                Spacer(Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(stringResource(desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.url_evidence, f.evidence), style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace, color = tint, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private val HeuristicType.texts: Pair<Int, Int>
    get() = when (this) {
        HeuristicType.PLAIN_HTTP -> R.string.url_h_plain_http to R.string.url_h_plain_http_d
        HeuristicType.IP_HOST -> R.string.url_h_ip_host to R.string.url_h_ip_host_d
        HeuristicType.PUNYCODE -> R.string.url_h_punycode to R.string.url_h_punycode_d
        HeuristicType.MIXED_SCRIPT -> R.string.url_h_mixed_script to R.string.url_h_mixed_script_d
        HeuristicType.AT_IN_AUTHORITY -> R.string.url_h_at to R.string.url_h_at_d
        HeuristicType.MANY_SUBDOMAINS -> R.string.url_h_subdomains to R.string.url_h_subdomains_d
        HeuristicType.LONG_URL -> R.string.url_h_long to R.string.url_h_long_d
        HeuristicType.SHORTENER -> R.string.url_h_shortener to R.string.url_h_shortener_d
        HeuristicType.SUSPICIOUS_TLD -> R.string.url_h_tld to R.string.url_h_tld_d
        HeuristicType.BRAND_IMPERSONATION -> R.string.url_h_brand to R.string.url_h_brand_d
        HeuristicType.LOOKALIKE_DOMAIN -> R.string.url_h_lookalike to R.string.url_h_lookalike_d
        HeuristicType.APK_DOWNLOAD -> R.string.url_h_apk to R.string.url_h_apk_d
        HeuristicType.NON_STANDARD_PORT -> R.string.url_h_port to R.string.url_h_port_d
    }

@Composable
private fun ActionsRow(url: String, risk: RiskLevel, onNew: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var warn by rememberSaveable { mutableStateOf(false) }
    val open = {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) { }
    }
    val risky = risk != RiskLevel.NO_DETECTIONS
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { if (risky) warn = true else open() },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = if (risky) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = risk.color)
            else ButtonDefaults.buttonColors(),
        ) {
            Icon(if (risky) Icons.Filled.Warning else Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.url_open))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("url", url))) } },
                modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Filled.ContentCopy, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.url_copy))
            }
            OutlinedButton(onClick = onNew, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                Text(stringResource(R.string.url_new_check))
            }
        }
    }
    if (warn) {
        AlertDialog(
            onDismissRequest = { warn = false },
            icon = { Icon(Icons.Filled.GppBad, null, tint = risk.color) },
            title = { Text(stringResource(R.string.url_open_warn_title)) },
            text = { Text(stringResource(R.string.url_open_warn_text)) },
            confirmButton = {
                TextButton(onClick = { warn = false; open() }) { Text(stringResource(R.string.url_open_anyway), color = RiskColors.Critical) }
            },
            dismissButton = { Button(onClick = { warn = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}
