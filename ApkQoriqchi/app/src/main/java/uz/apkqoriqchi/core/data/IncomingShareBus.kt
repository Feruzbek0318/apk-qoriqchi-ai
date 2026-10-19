package uz.apkqoriqchi.core.data

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed interface IncomingShare {
    data class Apk(val uri: Uri) : IncomingShare
    data class Url(val url: String) : IncomingShare
    /** Tapped a scan-complete notification. */
    data class HistoryItem(val id: Long) : IncomingShare
}

/** Carries an APK or link shared into the app until the matching screen consumes it. */
@Singleton
class IncomingShareBus @Inject constructor() {
    private val _pending = MutableStateFlow<IncomingShare?>(null)
    val pending: StateFlow<IncomingShare?> = _pending

    fun accept(intent: Intent?) {
        intent ?: return
        val historyId = intent.getLongExtra(HISTORY_ID_EXTRA, -1L)
        if (historyId > 0) { _pending.value = IncomingShare.HistoryItem(historyId); return }
        val share = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.let { IncomingShare.Apk(it) }
            Intent.ACTION_SEND -> if (intent.type == "text/plain") {
                intent.getStringExtra(Intent.EXTRA_TEXT)
                    ?.let { Regex("""https?://\S+""").find(it)?.value }
                    ?.let { IncomingShare.Url(it) }
            } else {
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { IncomingShare.Apk(it) }
            }
            else -> null
        }
        if (share != null) _pending.value = share
    }

    fun consume() { _pending.value = null }

    companion object {
        /** Must match ScanNotifier.EXTRA_HISTORY_ID. */
        const val HISTORY_ID_EXTRA = "uz.apkqoriqchi.HISTORY_ID"
    }
}
