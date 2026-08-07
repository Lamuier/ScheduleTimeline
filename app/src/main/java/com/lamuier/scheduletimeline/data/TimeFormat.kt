package com.lamuier.scheduletimeline.data

import java.util.Locale
import kotlin.math.max

object TimeFormat {
    fun minutesToHm(totalMinutes: Int): String {
        val normalized = ((totalMinutes % (24 * 60)) + (24 * 60)) % (24 * 60)
        val h = normalized / 60
        val m = normalized % 60
        return String.format(Locale.getDefault(), "%02d:%02d", h, m)
    }

    fun rangeLabel(startMinutes: Int, endMinutes: Int): String {
        return "${minutesToHm(startMinutes)} ~ ${minutesToHm(endMinutes)}"
    }

    fun durationLabel(startMinutes: Int, endMinutes: Int): String {
        val minutes = max(0, endMinutes - startMinutes)
        val hours = minutes / 60
        val remain = minutes % 60
        return when {
            hours > 0 && remain > 0 -> "${hours}小时${remain}分钟"
            hours > 0 -> "${hours}小时"
            else -> "${remain}分钟"
        }
    }

    fun parseHm(text: String): Int? {
        val trimmed = text.trim()
        val match = Regex("""^(\d{1,2}):(\d{2})$""").matchEntire(trimmed) ?: return null
        val h = match.groupValues[1].toInt()
        val m = match.groupValues[2].toInt()
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }
}
