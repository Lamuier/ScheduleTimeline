package com.lamuier.scheduletimeline.ui.timeline

import androidx.compose.runtime.Composable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.remember
import com.lamuier.scheduletimeline.ScheduleDayState
import com.lamuier.scheduletimeline.ScheduleViewModel
import com.lamuier.scheduletimeline.data.StateFlowLease
import java.time.LocalDate
import kotlinx.coroutines.flow.StateFlow

/** RememberObserver also closes a lease when Compose abandons an uncommitted composition. */
internal class RememberedDayStateLease(private val lease: StateFlowLease<ScheduleDayState>) : RememberObserver {
    val state: StateFlow<ScheduleDayState> get() = lease.state
    override fun onRemembered() = Unit
    override fun onForgotten() = lease.close()
    override fun onAbandoned() = lease.close()
}

@Composable
internal fun rememberDayState(viewModel: ScheduleViewModel, date: LocalDate): StateFlow<ScheduleDayState> =
    remember(viewModel, date) { RememberedDayStateLease(viewModel.acquireDayState(date)) }.state
