package uz.apkqoriqchi.feature.settings

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import uz.apkqoriqchi.R
import uz.apkqoriqchi.core.model.RiskLevel
import uz.apkqoriqchi.core.model.labelRes

/**
 * Posts a local notification when a user-started scan finishes. No background work or polling:
 * callers invoke it at the end of a scan. Silently does nothing when the toggle is off or permission is missing.
 */
@Singleton
class ScanNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: LocalPrefs,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun notifyScanComplete(title: String, riskLevel: RiskLevel, historyId: Long) {
        scope.launch {
            if (!prefs.notifyOnScan.first() || !canPost(context)) return@launch
            post(title, riskLevel, historyId)
        }
    }

    @SuppressLint("MissingPermission") // checked in canPost()
    private fun post(title: String, riskLevel: RiskLevel, historyId: Long) {
        val nm = NotificationManagerCompat.from(context)
        nm.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.settings_notify_channel))
                .build(),
        )
        val important = riskLevel in setOf(RiskLevel.MEDIUM, RiskLevel.HIGH, RiskLevel.CRITICAL)
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.putExtra(EXTRA_HISTORY_ID, historyId)
        val pending = launch?.let {
            PendingIntent.getActivity(
                context, historyId.toInt(), it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(if (important) R.string.settings_notify_title_important else R.string.settings_notify_title))
            .setContentText("$title · ${context.getString(riskLevel.labelRes)}")
            .setPriority(if (important) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .apply { pending?.let(::setContentIntent) }
            .build()
        nm.notify(NOTIFICATION_TAG, historyId.toInt(), notification)
    }

    companion object {
        const val CHANNEL_ID = "scan_results"
        const val EXTRA_HISTORY_ID = "uz.apkqoriqchi.HISTORY_ID"
        private const val NOTIFICATION_TAG = "scan"

        fun canPost(context: Context): Boolean {
            val granted = Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }
}
