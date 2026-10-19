package uz.apkqoriqchi.feature.apps

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import android.provider.Settings
import android.provider.Telephony
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uz.apkqoriqchi.core.model.RiskLevel

data class AppAuditResult(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val versionCode: Long,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val targetSdk: Int,
    val installer: String?,
    val signerSha256: String?,
    val isSystem: Boolean,
    val sideloaded: Boolean,
    val grantedDangerous: List<String>,
    val riskLevel: RiskLevel,
    val findings: List<Finding>,
) {
    val signals: Set<AppSignal> get() = findings.mapTo(mutableSetOf()) { it.signal }
    val hasSpecialAccess: Boolean get() = signals.any { it in SPECIAL_ACCESS }

    /** Plain-text evidence for AI explanations and history. */
    fun factsText(): String = buildString {
        appendLine("Installed app: $label ($packageName) ${versionName.orEmpty()}")
        appendLine("Verdict: $riskLevel")
        appendLine("Installer: ${installer ?: "unknown"}; target SDK: $targetSdk; ${if (isSystem) "system app" else "user-installed"}")
        signerSha256?.let { appendLine("Signing certificate SHA-256: $it") }
        if (findings.isEmpty()) appendLine("No risky signals found.")
        findings.forEach { appendLine("- ${it.evidence}") }
        if (grantedDangerous.isNotEmpty()) appendLine("Granted dangerous permissions: ${grantedDangerous.joinToString { it.substringAfterLast('.') }}")
    }.trim()

    companion object {
        val SPECIAL_ACCESS = setOf(
            AppSignal.ACCESSIBILITY, AppSignal.DEVICE_ADMIN, AppSignal.NOTIFICATION_LISTENER,
            AppSignal.DEFAULT_SMS, AppSignal.OVERLAY, AppSignal.INSTALL_PACKAGES,
        )
    }
}

/** Reusable, device-wide permission audit of installed apps. */
@Singleton
class InstalledAppAuditor @Inject constructor(@ApplicationContext private val context: Context) {

    private val pm: PackageManager get() = context.packageManager

    suspend fun audit(includeSystem: Boolean): List<AppAuditResult> = withContext(Dispatchers.IO) {
        val cr = context.contentResolver
        val accessibility = AppRiskScorer.parseComponentPackages(
            Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        )
        val listeners = AppRiskScorer.parseComponentPackages(Settings.Secure.getString(cr, "enabled_notification_listeners"))
        val admins = runCatching {
            context.getSystemService(DevicePolicyManager::class.java)?.activeAdmins.orEmpty().map { it.packageName }.toSet()
        }.getOrDefault(emptySet())
        val sms = runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull()
        val dangerousCache = HashMap<String, Boolean>()

        installedPackages()
            .filter { it.packageName != context.packageName }
            .mapNotNull { info ->
                val app = info.applicationInfo ?: return@mapNotNull null
                val isSystem = app.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                if (isSystem && !includeSystem) return@mapNotNull null
                val requested = info.requestedPermissions.orEmpty()
                val flags = info.requestedPermissionsFlags
                val granted = requested.filterIndexed { i, p ->
                    flags != null && flags.getOrElse(i) { 0 } and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0 &&
                        dangerousCache.getOrPut(p) { isDangerous(p) }
                }.sorted()
                val installer = installerOf(info.packageName)
                val facts = AppFacts(
                    packageName = info.packageName, isSystem = isSystem, installer = installer,
                    targetSdk = app.targetSdkVersion, requestedPermissions = requested.toSet(),
                    grantedDangerous = granted.toSet(),
                    accessibilityEnabled = info.packageName in accessibility,
                    deviceAdmin = info.packageName in admins,
                    notificationListener = info.packageName in listeners,
                    defaultSms = info.packageName == sms,
                )
                val assessment = AppRiskScorer.assess(facts)
                AppAuditResult(
                    packageName = info.packageName,
                    label = runCatching { app.loadLabel(pm).toString() }.getOrDefault(info.packageName),
                    versionName = info.versionName,
                    versionCode = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong(),
                    firstInstallTime = info.firstInstallTime,
                    lastUpdateTime = info.lastUpdateTime,
                    targetSdk = app.targetSdkVersion,
                    installer = installer,
                    signerSha256 = signerSha256(info),
                    isSystem = isSystem,
                    sideloaded = AppRiskScorer.isSideloaded(installer, isSystem),
                    grantedDangerous = granted,
                    riskLevel = assessment.level,
                    findings = assessment.findings,
                )
            }
            .sortedWith(compareByDescending<AppAuditResult> { it.riskLevel }.thenBy { it.label.lowercase() })
    }

    private fun installedPackages(): List<PackageInfo> =
        if (Build.VERSION.SDK_INT >= 33) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of((PackageManager.GET_PERMISSIONS or PackageManager.GET_SIGNING_CERTIFICATES).toLong()))
        } else if (Build.VERSION.SDK_INT >= 28) {
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS or PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            @Suppress("DEPRECATION") pm.getInstalledPackages(PackageManager.GET_PERMISSIONS or PackageManager.GET_SIGNATURES)
        }

    /** SHA-256 of the current signing certificate (first signer). */
    private fun signerSha256(info: PackageInfo): String? = runCatching {
        val sigs = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.let { if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory }
        } else @Suppress("DEPRECATION") info.signatures
        // signingCertificateHistory is oldest-first; the current cert is last.
        sigs?.lastOrNull()?.toByteArray()?.let { sha256Hex(it) }
    }.getOrNull()

    fun isInstalled(packageName: String): Boolean = runCatching { pm.getApplicationInfo(packageName, 0) }.isSuccess

    /** SHA-256 of the app's base.apk, for reputation lookups. */
    suspend fun baseApkSha256(packageName: String): String = withContext(Dispatchers.IO) {
        val path = pm.getApplicationInfo(packageName, 0).sourceDir
        val md = MessageDigest.getInstance("SHA-256")
        File(path).inputStream().buffered().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        md.digest().toHex()
    }

    private fun installerOf(pkg: String): String? = runCatching {
        if (Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(pkg).installingPackageName
        else @Suppress("DEPRECATION") pm.getInstallerPackageName(pkg)
    }.getOrNull()

    private fun isDangerous(permission: String): Boolean = runCatching {
        val info = pm.getPermissionInfo(permission, 0)
        val base = if (Build.VERSION.SDK_INT >= 28) info.protection
        else @Suppress("DEPRECATION") (info.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE)
        base == PermissionInfo.PROTECTION_DANGEROUS
    }.getOrDefault(false)
}

private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
private fun sha256Hex(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()
