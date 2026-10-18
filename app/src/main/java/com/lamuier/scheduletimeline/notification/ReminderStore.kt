package com.lamuier.scheduletimeline.notification

import android.content.Context
import android.content.SharedPreferences
import com.lamuier.scheduletimeline.data.ReminderState

internal class ReminderStore(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences("scheduled_reminder_ledger", Context.MODE_PRIVATE))
    private var persisted: ReminderState? = null
    fun read() = ReminderState(prefs.getStringSet("pending", emptySet()).orEmpty().toSet(),
        prefs.getStringSet("handled", emptySet()).orEmpty().toSet()).also { persisted = it }
    fun write(state: ReminderState) {
        if (state == (persisted ?: read())) return
        check(prefs.edit().putStringSet("pending", state.pending).putStringSet("handled", state.handled).commit())
        persisted = state.copy(pending = state.pending.toSet(), handled = state.handled.toSet())
    }
}
