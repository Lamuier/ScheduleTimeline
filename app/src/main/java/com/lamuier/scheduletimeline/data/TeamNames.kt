package com.lamuier.scheduletimeline.data

/**
 * 多团队名称的存储与展示规则。
 *
 * 数据库继续复用 `schedule_events.team`；旧数据是单个名称，新数据用不可见分隔符保存，
 * 从而无需修改 Room schema。CSV / 手动输入使用更易读的 `/`、`、` 或 `|` 分隔。
 */
object TeamNames {
    private const val STORAGE_SEPARATOR = "\u001F"
    private val inputSeparator = Regex("[\u001F/、|]+")

    fun normalize(names: Iterable<String>): List<String> = names
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

    fun encode(names: Iterable<String>): String =
        normalize(names).joinToString(STORAGE_SEPARATOR)

    fun decode(stored: String): List<String> =
        normalize(stored.split(STORAGE_SEPARATOR))

    fun parseInput(value: String): List<String> =
        normalize(value.split(inputSeparator))

    fun display(stored: String): String = decode(stored).joinToString(" / ")

    fun toCsv(stored: String): String = display(stored)

    fun fromCsv(value: String): String = encode(parseInput(value))

    fun shareAny(first: String, second: String): Boolean {
        val firstNames = decode(first).toSet()
        return firstNames.isNotEmpty() && decode(second).any(firstNames::contains)
    }
}

val ScheduleEvent.teamNames: List<String>
    get() = TeamNames.decode(team)

val ScheduleEvent.teamDisplay: String
    get() = TeamNames.display(team)

fun ScheduleEvent.sharesTeamWith(other: ScheduleEvent): Boolean =
    TeamNames.shareAny(team, other.team)
