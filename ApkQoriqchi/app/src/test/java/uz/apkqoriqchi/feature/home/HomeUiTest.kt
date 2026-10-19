package uz.apkqoriqchi.feature.home

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import uz.apkqoriqchi.core.data.ApiKey
import uz.apkqoriqchi.core.model.RiskLevel
import uz.apkqoriqchi.core.model.ScanRecord
import uz.apkqoriqchi.core.model.ScanType

class HomeUiTest {
    private fun rec(id: Long, t: Long, risk: RiskLevel = RiskLevel.LOW) = ScanRecord(id, ScanType.APK, "a$id.apk", null, risk, "", "{}", t)

    @Test fun `keeps five newest and ignores unknown for last successful scan`() {
        val history = (1L..7L).map { rec(it, it * 100) } + rec(8, 10_000, RiskLevel.UNKNOWN)
        val ui = buildHomeUi(history, mapOf(ApiKey.GEMINI to true))
        assertThat(ui.recent.map { it.id }).containsExactly(8L, 7L, 6L, 5L, 4L).inOrder()
        assertThat(ui.totalScans).isEqualTo(8)
        assertThat(ui.lastScanAt).isEqualTo(700L)
        assertThat(ui.loading).isFalse()
    }

    @Test fun `empty history has no last scan`() {
        assertThat(buildHomeUi(emptyList(), emptyMap()).lastScanAt).isNull()
    }
}
