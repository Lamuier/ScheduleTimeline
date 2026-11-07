package com.lamuier.scheduletimeline.notification

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** 刷新链路（广播、应用内刷新）的日志标签；只记动作名和异常，不含日程内容。 */
internal const val REFRESH_LOG_TAG = "ScheduleRefresh"

/**
 * goAsync 广播的收尾。
 *
 * 组件、闹钟和通知按钮的广播可能按前台广播送达：10 秒内不交还系统就会被判 ANR，进程连同这一轮刷新一起被杀。
 * 这里最多等 [WAIT_BUDGET_MS] 就交还广播；刷新本身不取消，在后台跑完，失败只写日志。
 */
internal object BroadcastWork {
    /** 前台广播 10 秒超时，留 3 秒给冷启动和主线程排队。 */
    const val WAIT_BUDGET_MS = 7_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun launch(
        label: String,
        finish: () -> Unit,
        budgetMs: Long = WAIT_BUDGET_MS,
        workScope: CoroutineScope = scope,
        work: suspend () -> Unit,
    ) {
        val job = workScope.launch {
            try {
                work()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Log.w(REFRESH_LOG_TAG, "$label failed", error)
            }
        }
        // 只给等待设上限：超时取消的是这个等待，上面的刷新照常跑完。
        workScope.launch {
            try {
                if (withTimeoutOrNull(budgetMs) { job.join() } == null) {
                    Log.w(REFRESH_LOG_TAG, "$label still running after ${budgetMs}ms, broadcast released")
                }
            } finally {
                finish()
            }
        }
    }
}
