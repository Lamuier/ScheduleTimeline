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
        "日期, 团队（多个用 / 分隔）, 类型, 特典种类, 场次说明, 地点, 开始, 结束, 备注, 完成"

    private const val COMPLETED_CSV_YES = "是"

    /** 导入样例：含表头；同日演出 / 特典按团队名称自动关联。 */
    const val IMPORT_SAMPLE =
        "$IMPORT_HEADER\n" +
            "2026-06-01, StarDiary, 演出, , , 主舞台, 14:20, 14:40, , \n" +
            "2026-06-01, StarDiary / 银烁花火, 特典, 平特, , 吧台A, 17:00, 19:00, , "

    /**
     * AI 提示词：复制后连同时间表图片发给任意 AI，
     * 让其输出能被 [parseImport] 直接解析的 CSV。
     */
    val IMPORT_AI_PROMPT = """
        请把下面图片里的日程时间表整理成 CSV 文本。只输出 CSV 内容：不要任何解释，不要用代码块包裹。

        第 1 行固定为表头，之后每行一条日程，字段用半角逗号分隔，可为空但逗号不能省：
        $IMPORT_HEADER

        各列填写规则：
        1. 日期：格式 yyyy-MM-dd（如 2026-06-01）。图片没写年份时，按活动信息推断补全，不要留空。
        2. 团队：出演者或团队名。同一时段多位出演者合并到同一行，团队之间用「 / 」分隔。
        3. 类型：只能填「演出」或「特典」。舞台演出（Live、舞台、专场）填「演出」；握手、签名、合影、一对一道别等互动环节填「特典」。
        4. 特典种类：类型为特典时填「前特」「平特」「终特」之一，图片未注明时填「平特」；演出此列留空。
        5. 场次说明、地点、备注：图片上有就照抄，没有就留空，不要编造。
        6. 开始、结束：24 小时制 HH:mm（如 14:20）。图片只给了开始时间时，结束填与开始相同的时间。
        7. 完成：一律留空。

        整理要求：
        - 按日期、开始时间从早到晚排序；每一天的日程连续排列。
        - 单元格内容里若含半角逗号，用英文双引号把该单元格整体包起来。
        - 同一天、同一时段、同类型的多个团队合并为一行；不同时段各占一行，不要合并。
    """.trimIndent()

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
                if (event.completed) COMPLETED_CSV_YES else "",
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
     * - v2（≥9 列）：日期, 团队（多团队用 / 分隔）, 类型, 特典种类, 场次说明, 地点, 开始, 结束, 备注[, 完成]
     * - v1（6–7 列）：日期, 标题(→团队), 分类(→类型), 地点, 开始, 结束, 备注
     * - 已发布的 10 列格式仍可导入；旧「关联演出开始时间」列（时钟值）会被忽略；
     *   新第 10 列「完成」写「是」时仅特典记为已完成。
     * - 首行若为表头（日期列=「日期」）则跳过。
     * - AI 输出常带说明文字或 ```csv 围栏：原样解析失败时会剥围栏、再从「日期」表头行重试。
     * 解析为引号感知（RFC 4180）：带引号字段中的逗号 / 引号 / 换行不破坏列结构。
     */
    fun parseImportDrafts(text: String, fallbackDayKey: String = "default"): List<ImportDraft> {
        val drafts = parseDrafts(text, fallbackDayKey)
        if (drafts.isNotEmpty()) return drafts
        val cleaned = stripMarkdownFence(text)
        val retry = if (cleaned != text) parseDrafts(cleaned, fallbackDayKey) else emptyList()
        if (retry.isNotEmpty()) return retry
        val fromHeader = extractFromHeaderLine(cleaned) ?: return emptyList()
        return parseDrafts(fromHeader, fallbackDayKey)
    }

    private fun parseDrafts(text: String, fallbackDayKey: String): List<ImportDraft> {
        return parseCsvRecords(text).mapNotNull { parts ->
            if (isHeaderRow(parts)) return@mapNotNull null
            when {
                parts.size >= 9 -> parseV2(parts, fallbackDayKey)
                parts.size >= 6 -> parseV1(parts, fallbackDayKey)
                else -> null
            }
        }
    }

    /** 剥掉整体 ``` 围栏（含 ```csv 等语言标记），非围栏文本原样返回。 */
    private fun stripMarkdownFence(text: String): String {
        val trimmed = text.trim()
        if (!trimmed.startsWith("```")) return text
        val body = trimmed.split('\n').drop(1)
        val lastLine = body.lastOrNull()?.trim().orEmpty()
        return if (lastLine.startsWith("```")) body.dropLast(1).joinToString("\n") else body.joinToString("\n")
    }

    /** AI 说明文字里定位第一条「日期」开头行，从该行起截取；找不到返回 null。 */
    private fun extractFromHeaderLine(text: String): String? {
        val lines = text.split('\n')
        val index = lines.indexOfFirst { it.trimStart().startsWith("日期") }
        if (index < 0) return null
        return lines.drop(index).joinToString("\n").takeIf { it.isNotBlank() }
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
        val completed = eventType == EventType.TOKUTEN &&
            parseCompletedColumn(parts.getOrNull(9).orEmpty())
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
                completed = completed,
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

    /**
     * 第 10 列：新格式为完成标记（是 / 已完成 / 1 / true）；
     * 旧格式为「关联演出开始时间」（HH:mm），忽略并视为未完成。
     */
    private fun parseCompletedColumn(raw: String): Boolean {
        val value = raw.trim()
        if (value.isEmpty()) return false
        if (TimeFormat.parseHm(value) != null) return false
        return value == COMPLETED_CSV_YES ||
            value == "已完成" ||
            value == "1" ||
            value.equals("true", ignoreCase = true)
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
