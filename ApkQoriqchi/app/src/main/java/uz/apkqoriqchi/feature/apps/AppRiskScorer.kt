package uz.apkqoriqchi.feature.apps

import uz.apkqoriqchi.core.model.RiskLevel

/** A risky signal found on an installed app. Order = display priority. */
enum class AppSignal(val weight: Int) {
    ACCESSIBILITY(3),
    DEVICE_ADMIN(3),
    SIDELOADED(2),
    NOTIFICATION_LISTENER(2),
    DEFAULT_SMS(2),
    SMS_ACCESS(2),
    LEGACY_TARGET_SDK(2),
    OVERLAY(1),
    INSTALL_PACKAGES(1),
    CALL_LOG_ACCESS(1),
    MANY_DANGEROUS(1),
}

/** Raw, platform-independent facts about one app; produced by [InstalledAppAuditor]. */
data class AppFacts(
    val packageName: String,
    val isSystem: Boolean,
    val installer: String?,
    val targetSdk: Int,
    val requestedPermissions: Set<String>,
    val grantedDangerous: Set<String>,
    val accessibilityEnabled: Boolean = false,
    val deviceAdmin: Boolean = false,
    val notificationListener: Boolean = false,
    val defaultSms: Boolean = false,
)

data class Finding(val signal: AppSignal, val evidence: String)

data class RiskAssessment(val level: RiskLevel, val score: Int, val findings: List<Finding>)

/**
 * Pure risk policy. A permission is not proof of malware, so heuristics cap at HIGH;
 * there is no "safe" verdict: the floor is [RiskLevel.NO_DETECTIONS] (no risky signals found).
 */
object AppRiskScorer {

    val KNOWN_STORES = setOf(
        "com.android.vending", "com.google.android.feedback", "com.sec.android.app.samsungapps",
        "com.huawei.appmarket", "com.xiaomi.market", "com.xiaomi.mipicks", "com.xiaomi.discover",
        "com.oppo.market", "com.heytap.market", "com.bbk.appstore", "com.amazon.venezia",
        "ru.vk.store", "org.fdroid.fdroid", "com.aurora.store", "com.transsion.phoenix",
    )

    private val SMS_PERMS = setOf(
        "android.permission.READ_SMS", "android.permission.RECEIVE_SMS",
        "android.permission.SEND_SMS", "android.permission.RECEIVE_MMS", "android.permission.RECEIVE_WAP_PUSH",
    )
    private val CALL_PERMS = setOf(
        "android.permission.READ_CALL_LOG", "android.permission.WRITE_CALL_LOG",
        "android.permission.PROCESS_OUTGOING_CALLS", "android.permission.ANSWER_PHONE_CALLS",
        "android.permission.CALL_PHONE",
    )
    const val OVERLAY_PERM = "android.permission.SYSTEM_ALERT_WINDOW"
    const val INSTALL_PERM = "android.permission.REQUEST_INSTALL_PACKAGES"
    private const val MANY_DANGEROUS_THRESHOLD = 6
    private const val LEGACY_SDK = 23
    const val MEDIUM_SCORE = 3
    const val HIGH_SCORE = 8
    const val CRITICAL_DETECTIONS = 3

    /** Preinstalled system apps without an installer are not sideloaded; any other non-store source is. */
    fun isSideloaded(installer: String?, isSystem: Boolean): Boolean =
        if (installer == null) !isSystem else installer !in KNOWN_STORES

    /** Extracts package names from a "pkg/cls:pkg/cls" Settings.Secure value. */
    fun parseComponentPackages(value: String?): Set<String> =
        value.orEmpty().split(':').mapNotNull { it.substringBefore('/').trim().ifEmpty { null } }.toSet()

    fun assess(f: AppFacts): RiskAssessment {
        val sideloaded = isSideloaded(f.installer, f.isSystem)
        val findings = buildList {
            if (f.accessibilityEnabled) add(Finding(AppSignal.ACCESSIBILITY, "Accessibility service is enabled (can read the screen and act on the user's behalf)"))
            if (f.deviceAdmin) add(Finding(AppSignal.DEVICE_ADMIN, "Active device administrator (can lock the device and resist uninstall)"))
            if (sideloaded) add(Finding(AppSignal.SIDELOADED, "Installed outside a known app store (installer: ${f.installer ?: "unknown"})"))
            if (f.notificationListener) add(Finding(AppSignal.NOTIFICATION_LISTENER, "Notification access is enabled (can read all notifications, including SMS codes)"))
            if (f.defaultSms) add(Finding(AppSignal.DEFAULT_SMS, "Set as the default SMS app"))
            val sms = f.grantedDangerous intersect SMS_PERMS
            if (sms.isNotEmpty() && !f.defaultSms) add(Finding(AppSignal.SMS_ACCESS, "SMS permissions granted: ${sms.short()}"))
            if (f.targetSdk in 1 until LEGACY_SDK) add(Finding(AppSignal.LEGACY_TARGET_SDK, "Targets old Android API ${f.targetSdk}; permissions are granted at install without prompts"))
            if (OVERLAY_PERM in f.requestedPermissions) add(Finding(AppSignal.OVERLAY, "Requests drawing over other apps (SYSTEM_ALERT_WINDOW)"))
            if (INSTALL_PERM in f.requestedPermissions) add(Finding(AppSignal.INSTALL_PACKAGES, "Can request installing other apps (REQUEST_INSTALL_PACKAGES)"))
            val calls = f.grantedDangerous intersect CALL_PERMS
            if (calls.isNotEmpty()) add(Finding(AppSignal.CALL_LOG_ACCESS, "Call/phone permissions granted: ${calls.short()}"))
            if (f.grantedDangerous.size >= MANY_DANGEROUS_THRESHOLD) add(Finding(AppSignal.MANY_DANGEROUS, "${f.grantedDangerous.size} dangerous permissions granted"))
        }
        val signals = findings.map { it.signal }.toSet()
        val score = findings.sumOf { it.signal.weight }
        val control = AppSignal.ACCESSIBILITY in signals || AppSignal.DEVICE_ADMIN in signals
        val interception = signals.any {
            it in setOf(AppSignal.SMS_ACCESS, AppSignal.DEFAULT_SMS, AppSignal.NOTIFICATION_LISTENER, AppSignal.OVERLAY)
        }
        // Heuristics alone never reach CRITICAL: that needs confirmed reputation detections (see [withReputation]).
        val level = when {
            score == 0 -> RiskLevel.NO_DETECTIONS
            sideloaded && control && interception -> RiskLevel.HIGH // banking-trojan pattern
            score >= HIGH_SCORE -> RiskLevel.HIGH
            score >= MEDIUM_SCORE -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }
        return RiskAssessment(level, score, findings)
    }

    /**
     * Combines the heuristic verdict with a reputation lookup (e.g. VirusTotal).
     * [malicious] engines >= [CRITICAL_DETECTIONS] → CRITICAL; any detection → at least HIGH.
     * A clean or missing report never lowers the heuristic verdict.
     */
    fun withReputation(heuristic: RiskLevel, malicious: Int): RiskLevel = when {
        malicious >= CRITICAL_DETECTIONS -> RiskLevel.CRITICAL
        malicious > 0 -> maxOf(heuristic, RiskLevel.HIGH)
        else -> heuristic
    }

    private fun Set<String>.short() = sorted().joinToString { it.substringAfterLast('.') }
}
