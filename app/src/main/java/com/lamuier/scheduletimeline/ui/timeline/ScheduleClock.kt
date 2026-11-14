package com.lamuier.scheduletimeline.ui.timeline

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.lamuier.scheduletimeline.data.ScheduleTimeSnapshot
import kotlinx.coroutines.delay
import java.time.Clock
import java.time.ZoneId

@Composable
internal fun rememberScheduleTime(
    clock: Clock = Clock.systemUTC(),
    zone: ZoneId? = null,
): State<ScheduleTimeSnapshot> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val resolveZone = { zone ?: ZoneId.systemDefault() }
    return produceState(ScheduleTimeSnapshot.now(clock, resolveZone()), lifecycle, clock, zone) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val current = ScheduleTimeSnapshot.now(clock, resolveZone())
                if (current.today != value.today ||
                    current.minutes != value.minutes ||
                    current.zone != value.zone
                ) {
                    value = current
                }
                delay(current.millisUntilNextMinute())
            }
        }
    }
}
