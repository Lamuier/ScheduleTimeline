package com.lamuier.scheduletimeline.data

import android.app.Application
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.lamuier.scheduletimeline.ui.timeline.rememberScheduleTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ScheduleClockAndTimeTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun millisUntilNextMinute_alignsAcrossBoundariesAndDstTransitions() {
        val utc = ZoneOffset.UTC
        assertEquals(
            60_000L,
            ScheduleTimeSnapshot(Instant.parse("2026-10-20T10:15:00.000Z"), utc).millisUntilNextMinute(),
        )
        assertEquals(
            15_000L,
            ScheduleTimeSnapshot(Instant.parse("2026-10-20T10:15:45.000Z"), utc).millisUntilNextMinute(),
        )
        assertEquals(
            1L,
            ScheduleTimeSnapshot(Instant.parse("2026-10-20T10:15:59.999900Z"), utc).millisUntilNextMinute(),
        )

        val ny = ZoneId.of("America/New_York")
        // Spring-forward: 01:59:30-05:00 jumps directly to 03:00:00-04:00 30s later.
        assertEquals(
            30_000L,
            ScheduleTimeSnapshot(Instant.parse("2026-03-08T06:59:30Z"), ny).millisUntilNextMinute(),
        )
        // Fall-back: 01:59:30-04:00 becomes 01:00:00-05:00 30s later.
        assertEquals(
            30_000L,
            ScheduleTimeSnapshot(Instant.parse("2026-11-01T05:59:30Z"), ny).millisUntilNextMinute(),
        )
    }

    @Test
    fun rememberScheduleTime_wakesAtMinuteBoundaryWithout30sPolling() {
        compose.mainClock.autoAdvance = false
        val clock = MutableTestClock(Instant.parse("2026-10-20T10:15:45.000Z"))
        lateinit var snapshot: State<ScheduleTimeSnapshot>
        var compositions = 0

        compose.setContent {
            snapshot = rememberScheduleTime(clock = clock, zone = ZoneOffset.UTC)
            // Read snapshot.value inside composition so recomposition count reflects clock ticks.
            val currentMinutes = snapshot.value.minutes
            SideEffect {
                if (currentMinutes >= 0) compositions++
            }
        }
        compose.waitForIdle()
        assertEquals(10 * 60 + 15, snapshot.value.minutes)
        assertEquals(1, compositions)

        // At 10:15:59.400 (+14.4s), still 600ms before minute boundary: no recomposition.
        clock.advanceMillis(14_400L)
        compose.mainClock.advanceTimeBy(14_400L)
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertEquals(10 * 60 + 15, snapshot.value.minutes)
        assertEquals(1, compositions)

        // Advance past 10:16:00 boundary: wakes right on minute transition instead of waiting 30s.
        clock.advanceMillis(600L)
        compose.mainClock.advanceTimeBy(640L)
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertEquals(10 * 60 + 16, snapshot.value.minutes)
        assertEquals(2, compositions)

        // Mid-minute (10:16:30.000, +30s since boundary): no 30s blind polling recomposition.
        clock.advanceMillis(30_000L)
        compose.mainClock.advanceTimeBy(30_000L)
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertEquals(10 * 60 + 16, snapshot.value.minutes)
        assertEquals(2, compositions)

        // Advance past 10:17:00 boundary: updates once at the next minute boundary.
        clock.advanceMillis(30_000L)
        compose.mainClock.advanceTimeBy(30_000L)
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertEquals(10 * 60 + 17, snapshot.value.minutes)
        assertEquals(3, compositions)
    }

    @Test
    fun rememberScheduleTime_crossesMidnightAtExactMinuteBoundary() {
        compose.mainClock.autoAdvance = false
        val shanghai = ZoneId.of("Asia/Shanghai")
        val clock = MutableTestClock(Instant.parse("2026-10-19T15:59:50.000Z")) // 23:59:50+08:00
        lateinit var snapshot: State<ScheduleTimeSnapshot>

        compose.setContent {
            snapshot = rememberScheduleTime(clock = clock, zone = shanghai)
        }
        compose.waitForIdle()
        assertEquals(LocalDate.of(2026, 10, 19), snapshot.value.today)
        assertEquals(23 * 60 + 59, snapshot.value.minutes)

        clock.advanceMillis(10_000L)
        compose.mainClock.advanceTimeBy(10_000L)
        compose.waitForIdle()
        assertEquals(LocalDate.of(2026, 10, 20), snapshot.value.today)
        assertEquals(0, snapshot.value.minutes)
    }

    private class MutableTestClock(private var currentInstant: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = currentInstant
        fun advanceMillis(millis: Long) {
            currentInstant = currentInstant.plusMillis(millis)
        }
    }
}
