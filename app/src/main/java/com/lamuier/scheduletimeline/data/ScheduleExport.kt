package com.lamuier.scheduletimeline.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** CSV 导入草稿。 */
data class ImportDraft(val event: ScheduleEvent)

/** 空日提示：距最近有日程的日期还有几天。 */
data class NearestScheduleHint(
    val dayKey: String,
    val daysAway: Long,
    val isFuture: Boolean,
)

object ScheduleExport {
    const val IMPORT_HEADER =
        "日期, 团队（多个用 / 分隔）, 类型, 特典种类, 场次说明, 地点, 开始, 结束, 备注"

    /** 导入样例：含表头；同日演出 / 特典按团队名称自动关联。 */
    const val IMPORT_SAMPLE =
        "$IMPORT_HEADER\n" +
            "2026-06-01, StarDiary, 演出, , , 主舞台, 14:20, 14:40, \n" +
            "2026-06-01, StarDiary / 银烁花火, 特典, 平特, , 吧台A, 17:00, 19:00, "

    fun toCsv(events: List<ScheduleEvent>): String {
        val body = events.joinToString("\n") { event ->
            val type = EventType.fromStorage(event.eventType)
            listOf(
                event.dayKey,
                TeamNames.toCsv(event.team),
                EventLabels.eventTypeToCsv(type),
                EventLabels.tokutenKindToCsv(event.tokutenKind),
                event.title,
                event.location,
                TimeFormat.minutesToHm(event.startMinutes),
                TimeFormat.minutesToHm(event.endMinutes),
                event.note,
            ).joinToString(", ") { escapeCsvField(it) }
        }
        return if (body.isEmpty()) IMPORT_HEADER else "$IMPORT_HEADER\n$body"
    }

    /** RFC 4180：字段含逗号 / 引号 / 换行时整体加引号，内部引号翻倍。 */
    private fun escapeCsvField(value: String): String {
        if (value.none { it == ',' || it == '"' || it == '\n' || it == '\r' }) return value
        return "\"" + value.replace("\"", "\"\"") + "\""
    }

    fun parseImport(text: String, fallbackDayKey: String = "default"): List<ScheduleEvent> {
        return parseImportDrafts(text, fallbackDayKey).map { it.event }
    }

    /**
     * 解析批量导入。
     * - v2（≥9 列）：日期, 团队（多团队用 / 分隔）, 类型, 特典种类, 场次说明, 地点, 开始, 结束, 备注
     * - v1（6–7 列）：日期, 标题(→团队), 分类(→类型), 地点, 开始, 结束, 备注
     * - 已发布的 10 列格式仍可导入；旧「关联演出开始时间」列会被忽略。
     * 首行若为表头（日期列=「日期」）则跳过。
     * 解析为引号感知（RFC 4180）：带引号字段中的逗号 / 引号 / 换行不破坏列结构。
     */
    fun parseImportDrafts(text: String, fallbackDayKey: String = "default"): List<ImportDraft> {
        return parseCsvRecords(text).mapNotNull { parts ->
            if (isHeaderRow(parts)) return@mapNotNull null
            when {
                parts.size >= 9 -> parseV2(parts, fallbackDayKey)
                parts.size >= 6 -> parseV1(parts, fallbackDayKey)
                else -> null
            }
        }
    }

    /**
     * 宽松的 CSV 记录解析（RFC 4180 兼容，兼容历史无引号数据）：
     * - 仅当引号出现在字段起始（前导空白之后）才进入引号段；
     * 历史数据字段中部的半角引号按字面保留。
     * - 引号段内 `""` 表示字面引号；段内换行属于同一条记录。
     * - 字段解析后 trim；仅含一个空字段的空白行跳过。
     */
    private fun parseCsvRecords(text: String): List<List<String>> {
        val records = mutableListOf<List<String>>()
        val fields = mutableListOf<String>()
        val field = StringBuilder()

        fun endField() {
            fields.add(field.toString().trim())
            field.clear()
        }

        fun endRecord() {
            endField()
            if (fields.size > 1 || fields.first().isNotEmpty()) records.add(fields.toList())
            fields.clear()
        }

        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                when {
                    c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                        field.append('"')
                        i += 2
                    }
                    c == '"' -> {
                        inQuotes = false
                        i++
                    }
                    else -> {
                        field.append(c)
                        i++
                    }
                }
            } else {
                when (c) {
                    '"' -> if (field.isBlank()) {
                        inQuotes = true
                        i++
                    } else {
                        field.append(c)
                        i++
                    }
                    ',' -> {
                        endField()
                        i++
                    }
                    '\n', '\r' -> {
                        endRecord()
                        i++
                    }
                    else -> {
                        field.append(c)
                        i++
                    }
                }
            }
        }
        // 结尾无换行的最后一条记录
        if (field.isNotEmpty() || fields.isNotEmpty()) endRecord()
        return records
    }

    private fun isHeaderRow(parts: List<String>): Boolean {
        val first = parts.getOrNull(0).orEmpty()
        return first == "日期" || first.equals("dayKey", ignoreCase = true) ||
            first.equals("date", ignoreCase = true)
    }

    private fun parseV2(parts: List<String>, fallbackDayKey: String): ImportDraft? {
        val dayKey = when {
            parts[0].isEmpty() -> fallbackDayKey
            else -> parseDayKey(parts[0]) ?: return null
        }
        val team = TeamNames.fromCsv(parts[1])
        if (team.isEmpty()) return null
        val eventType = EventLabels.eventTypeStorageFromCsv(parts[2])
        val tokutenKind = when (eventType) {
            EventType.TOKUTEN -> {
                val kind = EventLabels.tokutenKindStorageFromCsv(parts[3])
                kind.ifEmpty { TokutenKind.PARALLEL.storage }
            }
            EventType.PERFORMANCE -> ""
        }
        val start = TimeFormat.parseHm(parts[6]) ?: return null
        val end = TimeFormat.parseHm(parts[7]) ?: return null
        return ImportDraft(
            event = ScheduleEvent(
                team = team,
                eventType = eventType.storage,
                tokutenKind = tokutenKind,
                title = parts.getOrNull(4).orEmpty(),
                location = parts.getOrNull(5).orEmpty(),
                startMinutes = start,
                endMinutes = end,
                note = parts.getOrNull(8).orEmpty(),
                dayKey = dayKey,
            ),
        )
    }

    private fun parseV1(parts: List<String>, fallbackDayKey: String): ImportDraft? {
        val dayKey = when {
            parts[0].isEmpty() -> fallbackDayKey
            else -> parseDayKey(parts[0]) ?: return null
        }
        val team = TeamNames.fromCsv(parts[1])
        if (team.isEmpty()) return null
        val category = parts.getOrNull(2).orEmpty()
        val eventType = EventLabels.eventTypeStorageFromCsv(category)
        val tokutenKind = when (eventType) {
            EventType.TOKUTEN -> TokutenKind.PARALLEL.storage
            EventType.PERFORMANCE -> ""
        }
        val start = TimeFormat.parseHm(parts[4]) ?: return null
        val end = TimeFormat.parseHm(parts[5]) ?: return null
        return ImportDraft(
            event = ScheduleEvent(
                team = team,
                eventType = eventType.storage,
                tokutenKind = tokutenKind,
                location = parts.getOrNull(3).orEmpty(),
                startMinutes = start,
                endMinutes = end,
                note = parts.getOrNull(6).orEmpty(),
                dayKey = dayKey,
                category = "",
            ),
        )
    }

    fun parseDayKey(text: String): String? {
        return try {
            LocalDate.parse(text.trim(), DateTimeFormatter.ISO_LOCAL_DATE)
                .format(DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    /** 相对 [from] 找最近有日程的日期；优先未来，否则取最近的过去。 */
    fun nearestScheduleHint(from: LocalDate, dayKeys: List<String>): NearestScheduleHint? {
        val dates = dayKeys.mapNotNull { parseDayKey(it)?.let { key -> LocalDate.parse(key) } }
            .filter { it != from }
            .distinct()
        if (dates.isEmpty()) return null
        val future = dates.filter { it.isAfter(from) }.minOrNull()
        val past = dates.filter { it.isBefore(from) }.maxOrNull()
        val chosen = future ?: past ?: return null
        return NearestScheduleHint(
            dayKey = chosen.format(DateTimeFormatter.ISO_LOCAL_DATE),
            daysAway = abs(ChronoUnit.DAYS.between(from, chosen)),
            isFuture = chosen.isAfter(from),
        )
    }
}
