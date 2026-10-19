package uz.apkqoriqchi.feature.history

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import uz.apkqoriqchi.core.model.ScanRecord

/** A date bucket in the history list. */
sealed interface DayGroup {
    data object Today : DayGroup
    data object Yesterday : DayGroup
    data class Date(val date: LocalDate) : DayGroup
}

/** Groups records (any order) by local calendar day, newest day and newest record first. */
fun groupByDay(
    records: List<ScanRecord>,
    now: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): List<Pair<DayGroup, List<ScanRecord>>> {
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return records.sortedByDescending { it.createdAt }
        .groupBy { Instant.ofEpochMilli(it.createdAt).atZone(zone).toLocalDate() }
        .map { (date, items) ->
            val group = when (date) {
                today -> DayGroup.Today
                today.minusDays(1) -> DayGroup.Yesterday
                else -> DayGroup.Date(date)
            }
            group to items
        }
}

private val prettyJson = Json { prettyPrint = true; prettyPrintIndent = "  " }

/** Pretty-prints [raw] JSON; returns it unchanged if it isn't valid JSON. */
fun prettyPrintJson(raw: String): String =
    runCatching { prettyJson.encodeToString(JsonElement.serializer(), Json.parseToJsonElement(raw)) }
        .getOrDefault(raw)

/** True when there is nothing worth showing in the technical-details section. */
fun isEmptyJson(raw: String): Boolean = raw.isBlank() || raw.trim() in setOf("{}", "[]", "null")

/** Provider status rows and the "incomplete" flag extracted from a record's detailsJson. */
data class DetailMeta(val providerStatus: List<Pair<String, String>>, val incomplete: Boolean, val incompleteReason: String?)

/**
 * Reads the optional "providerStatus" (object, array or string) and "incomplete" (boolean, string or object) keys.
 * Unknown shapes are rendered as compact text instead of failing.
 */
fun parseDetailMeta(raw: String): DetailMeta {
    val root = runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
        ?: return DetailMeta(emptyList(), false, null)
    val status = when (val ps = root["providerStatus"]) {
        is JsonObject -> ps.map { (k, v) -> k to v.asText() }
        is JsonArray -> ps.mapIndexed { i, v ->
            val o = v as? JsonObject
            val name = o?.let { it["provider"] ?: it["name"] }?.asText()
            (name ?: "#${i + 1}") to (o?.filterKeys { it != "provider" && it != "name" }?.let { JsonObject(it).asText() } ?: v.asText())
        }
        null, JsonNull -> emptyList()
        else -> listOf("" to ps.asText())
    }
    val inc = root["incomplete"]
    val (incomplete, reason) = when {
        inc == null || inc is JsonNull -> false to null
        inc is JsonPrimitive && inc.booleanOrNull != null -> inc.booleanOrNull!! to null
        inc is JsonPrimitive -> inc.content.isNotBlank() to inc.content.takeIf { it.isNotBlank() }
        else -> true to inc.asText()
    }
    return DetailMeta(status, incomplete, reason)
}

private fun JsonElement.asText(): String = when (this) {
    is JsonPrimitive -> contentOrNull ?: "null"
    is JsonObject -> entries.joinToString(", ") { (k, v) -> "$k: ${v.asText()}" }
    is JsonArray -> joinToString(", ") { it.asText() }
}
