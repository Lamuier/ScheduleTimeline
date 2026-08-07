package com.lamuier.scheduletimeline.data

import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NotificationPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val defaultEnabled = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, defaultEnabled))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()
    private val _alwaysOn = MutableStateFlow(prefs.getBoolean(KEY_ALWAYS_ON, false))
    val alwaysOn: StateFlow<Boolean> = _alwaysOn.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _enabled.value = enabled
        if (!enabled) setAlwaysOn(false)
    }

    fun setAlwaysOn(alwaysOn: Boolean) {
        prefs.edit().putBoolean(KEY_ALWAYS_ON, alwaysOn).apply()
        _alwaysOn.value = alwaysOn
    }

    private companion object {
        const val PREFS = "notification_prefs"
        const val KEY_ENABLED = "schedule_notifications_enabled"
        const val KEY_ALWAYS_ON = "live_updates_always_on"
    }
}
