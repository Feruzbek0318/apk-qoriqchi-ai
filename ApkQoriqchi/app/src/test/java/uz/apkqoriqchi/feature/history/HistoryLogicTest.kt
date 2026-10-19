package uz.apkqoriqchi.feature.history

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Test
import uz.apkqoriqchi.core.model.RiskLevel
import uz.apkqoriqchi.core.model.ScanRecord
import uz.apkqoriqchi.core.model.ScanType

class HistoryLogicTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private fun at(date: String, hour: Int) = LocalDate.parse(date).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    private fun rec(id: Long, time: Long) = ScanRecord(id, ScanType.URL, "https://e.com/$id", null, RiskLevel.LOW, "s", "{}", time)

    @Test fun `groups by day newest first`() {
        val now = at("2026-10-18", 12)
        val groups = groupByDay(
            listOf(rec(1, at("2026-10-15", 9)), rec(2, at("2026-10-18", 8)), rec(3, at("2026-10-17", 23)), rec(4, at("2026-10-18", 11))),
            now, zone,
        )
        assertThat(groups.map { it.first }).containsExactly(
            DayGroup.Today, DayGroup.Yesterday, DayGroup.Date(LocalDate.parse("2026-10-15")),
        ).inOrder()
        assertThat(groups[0].second.map { it.id }).containsExactly(4L, 2L).inOrder()
    }

    @Test fun `empty list gives no groups`() {
        assertThat(groupByDay(emptyList(), 0, zone)).isEmpty()
    }

    @Test fun `pretty print keeps invalid json unchanged`() {
        assertThat(prettyPrintJson("""{"a":1,"b":[true]}""")).isEqualTo("{\n  \"a\": 1,\n  \"b\": [\n    true\n  ]\n}")
        assertThat(prettyPrintJson("not json")).isEqualTo("not json")
        assertThat(isEmptyJson(" {} ")).isTrue()
        assertThat(isEmptyJson("""{"a":1}""")).isFalse()
    }

    @Test fun `detail meta reads provider status and incomplete flag`() {
        val m = parseDetailMeta("""{"providerStatus":{"VirusTotal":"OK","Safe Browsing":{"status":"FAILED","code":429}},"incomplete":true}""")
        assertThat(m.providerStatus).containsExactly("VirusTotal" to "OK", "Safe Browsing" to "status: FAILED, code: 429").inOrder()
        assertThat(m.incomplete).isTrue()
        assertThat(m.incompleteReason).isNull()

        val arr = parseDetailMeta("""{"providerStatus":[{"provider":"VT","status":"NOT_FOUND"}],"incomplete":"timeout"}""")
        assertThat(arr.providerStatus).containsExactly("VT" to "status: NOT_FOUND")
        assertThat(arr.incompleteReason).isEqualTo("timeout")

        val none = parseDetailMeta("""{"x":1}""")
        assertThat(none.providerStatus).isEmpty()
        assertThat(none.incomplete).isFalse()
        assertThat(parseDetailMeta("garbage").incomplete).isFalse()
    }
}
