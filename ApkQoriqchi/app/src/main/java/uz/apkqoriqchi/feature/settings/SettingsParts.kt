package uz.apkqoriqchi.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import uz.apkqoriqchi.R
import uz.apkqoriqchi.core.data.ApiKey
import uz.apkqoriqchi.ui.components.IconTile

/** Display metadata for each API provider. */
data class ProviderInfo(val name: String, val icon: ImageVector, val keyUrl: String, val helpRes: Int)

val ApiKey.info: ProviderInfo
    get() = when (this) {
        ApiKey.VIRUSTOTAL -> ProviderInfo(
            "VirusTotal", Icons.Filled.VerifiedUser,
            "https://www.virustotal.com/gui/my-apikey", R.string.settings_vt_help,
        )
        ApiKey.SAFE_BROWSING -> ProviderInfo(
            "Google Safe Browsing", Icons.Filled.Public,
            "https://console.cloud.google.com/apis/library/safebrowsing.googleapis.com", R.string.settings_gsb_help,
        )
        ApiKey.GEMINI -> ProviderInfo(
            "Google Gemini AI", Icons.Filled.AutoAwesome,
            "https://aistudio.google.com/apikey", R.string.settings_gemini_help,
        )
    }

@Composable
internal fun SwitchRow(title: String, body: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (body != null) Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
internal fun NavRow(icon: ImageVector, title: String, body: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (body != null) Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun Paragraph(res: Int) {
    Text(stringResource(res), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
