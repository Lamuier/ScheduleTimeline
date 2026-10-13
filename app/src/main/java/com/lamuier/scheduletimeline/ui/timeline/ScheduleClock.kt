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

@Composable
internal fun rememberScheduleTime(clock: Clock = Clock.systemUTC()): State<ScheduleTimeSnapshot> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return produceState(ScheduleTimeSnapshot.now(clock), lifecycle, clock) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = ScheduleTimeSnapshot.now(clock)
                delay(30_000)
            }
        }
    }
}
