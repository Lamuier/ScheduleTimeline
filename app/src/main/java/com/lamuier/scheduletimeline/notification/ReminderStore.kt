package com.lamuier.scheduletimeline.notification

import android.content.Context
import com.lamuier.scheduletimeline.data.ReminderState

internal class ReminderStore(context: Context) {
    private val prefs = context.getSharedPreferences("scheduled_reminder_ledger", Context.MODE_PRIVATE)
    fun read() = ReminderState(prefs.getStringSet("pending", emptySet()).orEmpty().toSet(),
        prefs.getStringSet("handled", emptySet()).orEmpty().toSet())
    fun write(state: ReminderState) {
        check(prefs.edit().putStringSet("pending", state.pending).putStringSet("handled", state.handled).commit())
    }
}
