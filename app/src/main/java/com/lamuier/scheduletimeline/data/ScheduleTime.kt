package com.lamuier.scheduletimeline.data

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class ScheduleTimeSnapshot(val instant: Instant, val zone: ZoneId) {
    private val local get() = instant.atZone(zone)
    val today: LocalDate get() = local.toLocalDate()
    val minutes: Int get() = local.hour * 60 + local.minute
    companion object {
        fun now(clock: Clock = Clock.systemUTC(), zone: ZoneId = ZoneId.systemDefault()) =
            ScheduleTimeSnapshot(clock.instant(), zone)
    }
}
