package com.lamuier.scheduletimeline.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode {
    System,
    Light,
    Dark,
    ;

    companion object {
        fun fromStorage(value: String?): ThemeMode =
            entries.firstOrNull { it.name == value } ?: System
    }
}

class ThemePreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _mode = MutableStateFlow(ThemeMode.fromStorage(prefs.getString(KEY_MODE, null)))
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    private val _dynamicColor = MutableStateFlow(prefs.getBoolean(KEY_DYNAMIC_COLOR, false))
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    fun setMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_MODE, mode.name).apply()
        _mode.value = mode
    }

    fun setDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
        _dynamicColor.value = enabled
    }

    private companion object {
        const val PREFS = "theme_prefs"
        const val KEY_MODE = "theme_mode"
        const val KEY_DYNAMIC_COLOR = "theme_dynamic_color"
    }
}
