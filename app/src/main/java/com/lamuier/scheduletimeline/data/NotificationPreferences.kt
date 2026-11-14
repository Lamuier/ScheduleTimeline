package com.lamuier.scheduletimeline.data

import android.content.Context
import android.os.Build
import androidx.core.content.edit
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
        prefs.edit { putBoolean(KEY_ENABLED, enabled) }
        _enabled.value = enabled
        if (!enabled) setAlwaysOn(false)
    }

    fun setAlwaysOn(alwaysOn: Boolean) {
        prefs.edit { putBoolean(KEY_ALWAYS_ON, alwaysOn) }
        _alwaysOn.value = alwaysOn
    }

    fun isPermissionRequested(): Boolean = prefs.getBoolean(KEY_PERMISSION_REQUESTED, false)

    fun setPermissionRequested(requested: Boolean) {
        prefs.edit { putBoolean(KEY_PERMISSION_REQUESTED, requested) }
    }

    private companion object {
        const val PREFS = "notification_prefs"
        const val KEY_ENABLED = "schedule_notifications_enabled"
        const val KEY_ALWAYS_ON = "live_updates_always_on"
        const val KEY_PERMISSION_REQUESTED = "notification_permission_requested"
    }
}
