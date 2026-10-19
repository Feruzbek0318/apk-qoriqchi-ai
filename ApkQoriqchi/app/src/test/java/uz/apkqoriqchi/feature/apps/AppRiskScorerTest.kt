package uz.apkqoriqchi.feature.apps

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import uz.apkqoriqchi.core.model.RiskLevel

class AppRiskScorerTest {

    private fun facts(
        installer: String? = "com.android.vending",
        isSystem: Boolean = false,
        targetSdk: Int = 34,
        requested: Set<String> = emptySet(),
        granted: Set<String> = emptySet(),
        accessibility: Boolean = false,
        admin: Boolean = false,
        listener: Boolean = false,
        sms: Boolean = false,
    ) = AppFacts("com.example", isSystem, installer, targetSdk, requested + granted, granted, accessibility, admin, listener, sms)

    private val readSms = "android.permission.READ_SMS"

    @Test fun `store app with no signals has no detections`() {
        val a = AppRiskScorer.assess(facts())
        assertThat(a.level).isEqualTo(RiskLevel.NO_DETECTIONS)
        assertThat(a.findings).isEmpty()
    }

    @Test fun `sideload detection`() {
        assertThat(AppRiskScorer.isSideloaded(null, isSystem = false)).isTrue()
        assertThat(AppRiskScorer.isSideloaded(null, isSystem = true)).isFalse()
        assertThat(AppRiskScorer.isSideloaded("com.android.vending", false)).isFalse()
        assertThat(AppRiskScorer.isSideloaded("com.google.android.packageinstaller", false)).isTrue()
    }

    @Test fun `sideloaded only is low`() {
        assertThat(AppRiskScorer.assess(facts(installer = null)).level).isEqualTo(RiskLevel.LOW)
    }

    @Test fun `medium boundary at score 3`() {
        // SIDELOADED(2) + OVERLAY(1) = 3
        val a = AppRiskScorer.assess(facts(installer = null, requested = setOf(AppRiskScorer.OVERLAY_PERM)))
        assertThat(a.score).isEqualTo(AppRiskScorer.MEDIUM_SCORE)
        assertThat(a.level).isEqualTo(RiskLevel.MEDIUM)
        // INSTALL_PACKAGES(1) + OVERLAY(1) = 2 -> LOW
        val b = AppRiskScorer.assess(facts(requested = setOf(AppRiskScorer.OVERLAY_PERM, AppRiskScorer.INSTALL_PERM)))
        assertThat(b.level).isEqualTo(RiskLevel.LOW)
    }

    @Test fun `store accessibility app is medium not high`() {
        assertThat(AppRiskScorer.assess(facts(accessibility = true)).level).isEqualTo(RiskLevel.MEDIUM)
    }

    @Test fun `sideloaded accessibility sms is high and never critical on heuristics`() {
        val a = AppRiskScorer.assess(facts(installer = null, accessibility = true, granted = setOf(readSms)))
        assertThat(a.level).isEqualTo(RiskLevel.HIGH)
        assertThat(a.findings.map { it.signal }).containsAtLeast(AppSignal.SIDELOADED, AppSignal.ACCESSIBILITY, AppSignal.SMS_ACCESS)
    }

    @Test fun `everything at once still caps at high`() {
        val a = AppRiskScorer.assess(
            facts(
                installer = null, targetSdk = 22, accessibility = true, admin = true, listener = true, sms = true,
                requested = setOf(AppRiskScorer.OVERLAY_PERM, AppRiskScorer.INSTALL_PERM),
            )
        )
        assertThat(a.score).isAtLeast(AppRiskScorer.HIGH_SCORE)
        assertThat(a.level).isEqualTo(RiskLevel.HIGH)
    }

    @Test fun `default sms app does not double count sms permissions`() {
        val a = AppRiskScorer.assess(facts(sms = true, granted = setOf(readSms)))
        assertThat(a.findings.map { it.signal }).containsExactly(AppSignal.DEFAULT_SMS)
        assertThat(a.level).isEqualTo(RiskLevel.LOW)
    }

    @Test fun `reputation thresholds`() {
        val t = AppRiskScorer.CRITICAL_DETECTIONS
        assertThat(AppRiskScorer.withReputation(RiskLevel.LOW, 0)).isEqualTo(RiskLevel.LOW)
        assertThat(AppRiskScorer.withReputation(RiskLevel.NO_DETECTIONS, 1)).isEqualTo(RiskLevel.HIGH)
        assertThat(AppRiskScorer.withReputation(RiskLevel.LOW, t - 1)).isEqualTo(RiskLevel.HIGH)
        assertThat(AppRiskScorer.withReputation(RiskLevel.LOW, t)).isEqualTo(RiskLevel.CRITICAL)
        assertThat(AppRiskScorer.withReputation(RiskLevel.HIGH, 0)).isEqualTo(RiskLevel.HIGH)
    }

    @Test fun `parses settings component lists`() {
        assertThat(AppRiskScorer.parseComponentPackages("a.b/.Svc:c.d/c.d.Other")).containsExactly("a.b", "c.d")
        assertThat(AppRiskScorer.parseComponentPackages(null)).isEmpty()
        assertThat(AppRiskScorer.parseComponentPackages("")).isEmpty()
    }

    @Test fun `filter and sort`() {
        fun app(pkg: String, level: RiskLevel, sideloaded: Boolean = false, findings: List<Finding> = emptyList()) = AppAuditResult(
            pkg, pkg.uppercase(), "1", 1, 0, 0, 34, null, null, false, sideloaded, emptyList(), level, findings,
        )
        val apps = listOf(
            app("a", RiskLevel.LOW),
            app("b", RiskLevel.HIGH, sideloaded = true),
            app("c", RiskLevel.MEDIUM, findings = listOf(Finding(AppSignal.ACCESSIBILITY, ""))),
        )
        assertThat(filterApps(apps, AppFilter.ALL, "", true).map { it.packageName }).containsExactly("b", "c", "a").inOrder()
        assertThat(filterApps(apps, AppFilter.ALL, "", false).map { it.packageName }).containsExactly("a", "b", "c").inOrder()
        assertThat(filterApps(apps, AppFilter.RISKY, "", true).map { it.packageName }).containsExactly("b", "c")
        assertThat(filterApps(apps, AppFilter.SIDELOADED, "", true).map { it.packageName }).containsExactly("b")
        assertThat(filterApps(apps, AppFilter.SPECIAL_ACCESS, "", true).map { it.packageName }).containsExactly("c")
        assertThat(filterApps(apps, AppFilter.ALL, "C", true).map { it.packageName }).containsExactly("c")
    }
}
