package uz.apkqoriqchi.feature.apps

import android.content.pm.PackageManager
import android.util.LruCache
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uz.apkqoriqchi.R
import uz.apkqoriqchi.core.model.RiskLevel
import uz.apkqoriqchi.core.model.color
import uz.apkqoriqchi.core.model.labelRes

private val iconCache = LruCache<String, ImageBitmap>(200)

@Composable
fun AppIcon(packageName: String, size: Dp = 44.dp) {
    val pm = LocalContext.current.packageManager
    val icon by produceState(iconCache.get(packageName), packageName) {
        if (value == null) value = withContext(Dispatchers.IO) {
            runCatching {
                pm.getApplicationIcon(packageName).toBitmap(128, 128).asImageBitmap()
                    .also { iconCache.put(packageName, it) }
            }.getOrNull()
        }
    }
    val modifier = Modifier.size(size).clip(RoundedCornerShape(12.dp))
    icon?.let { Image(it, contentDescription = null, modifier = modifier) } ?: Box(
        modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center,
    ) { Icon(Icons.Outlined.Android, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
fun RiskBadge(level: RiskLevel, modifier: Modifier = Modifier) {
    Text(
        stringResource(level.labelRes),
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(level.color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        color = level.color,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@get:StringRes
val AppSignal.titleRes: Int
    get() = when (this) {
        AppSignal.ACCESSIBILITY -> R.string.apps_signal_accessibility_title
        AppSignal.DEVICE_ADMIN -> R.string.apps_signal_device_admin_title
        AppSignal.SIDELOADED -> R.string.apps_signal_sideloaded_title
        AppSignal.NOTIFICATION_LISTENER -> R.string.apps_signal_notification_listener_title
        AppSignal.DEFAULT_SMS -> R.string.apps_signal_default_sms_title
        AppSignal.SMS_ACCESS -> R.string.apps_signal_sms_access_title
        AppSignal.LEGACY_TARGET_SDK -> R.string.apps_signal_legacy_target_sdk_title
        AppSignal.OVERLAY -> R.string.apps_signal_overlay_title
        AppSignal.INSTALL_PACKAGES -> R.string.apps_signal_install_packages_title
        AppSignal.CALL_LOG_ACCESS -> R.string.apps_signal_call_log_access_title
        AppSignal.MANY_DANGEROUS -> R.string.apps_signal_many_dangerous_title
    }

@get:StringRes
val AppSignal.descRes: Int
    get() = when (this) {
        AppSignal.ACCESSIBILITY -> R.string.apps_signal_accessibility_desc
        AppSignal.DEVICE_ADMIN -> R.string.apps_signal_device_admin_desc
        AppSignal.SIDELOADED -> R.string.apps_signal_sideloaded_desc
        AppSignal.NOTIFICATION_LISTENER -> R.string.apps_signal_notification_listener_desc
        AppSignal.DEFAULT_SMS -> R.string.apps_signal_default_sms_desc
        AppSignal.SMS_ACCESS -> R.string.apps_signal_sms_access_desc
        AppSignal.LEGACY_TARGET_SDK -> R.string.apps_signal_legacy_target_sdk_desc
        AppSignal.OVERLAY -> R.string.apps_signal_overlay_desc
        AppSignal.INSTALL_PACKAGES -> R.string.apps_signal_install_packages_desc
        AppSignal.CALL_LOG_ACCESS -> R.string.apps_signal_call_log_access_desc
        AppSignal.MANY_DANGEROUS -> R.string.apps_signal_many_dangerous_desc
    }

/** Localized system label for a permission, falling back to its short constant name. */
fun PackageManager.permissionLabel(permission: String): String =
    runCatching { getPermissionInfo(permission, 0).loadLabel(this).toString() }
        .getOrNull()?.takeIf { it.isNotBlank() && it != permission }
        ?.replaceFirstChar { it.uppercase() }
        ?: permission.substringAfterLast('.')
