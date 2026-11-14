package com.lamuier.scheduletimeline.notification

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import java.lang.reflect.Method

data class XiaomiHyperIslandCapability(
    val isXiaomiDevice: Boolean,
    val protocol: Int,
    val islandFeatureEnabled: Boolean,
    val focusPermissionGranted: Boolean,
) {
    /**
     * HyperIsland candidate detection deliberately does not require Focus whitelist
     * permission. HyperOS versions in the wild may render the private extras even
     * when the compatibility provider reports false.
     */
    val isOs3Supported: Boolean
        get() = isXiaomiDevice &&
            islandFeatureEnabled &&
            protocol != 1 &&
            protocol != 2

    companion object {
        const val OS3_PROTOCOL = 3
    }
}

/** Reads Xiaomi's public compatibility signals without making the app Xiaomi-only. */
object XiaomiHyperIslandCapabilityReader {
    private const val PROTOCOL_SETTING = "notification_focus_protocol"
    private const val ISLAND_PROPERTY = "persist.sys.feature.island"

    private val systemPropertiesClass: Class<*>? by lazy { loadSystemPropertiesClass() }

    @SuppressLint("PrivateApi")
    private fun loadSystemPropertiesClass(): Class<*>? =
        runCatching { Class.forName("android.os.SystemProperties") }.getOrNull()

    private val getBooleanMethod: Method? by lazy {
        runCatching {
            systemPropertiesClass?.getDeclaredMethod(
                "getBoolean",
                String::class.java,
                Boolean::class.javaPrimitiveType,
            )?.apply { isAccessible = true }
        }.getOrNull()
    }

    private val getStringMethod: Method? by lazy {
        runCatching {
            systemPropertiesClass?.getDeclaredMethod("get", String::class.java)
                ?.apply { isAccessible = true }
        }.getOrNull()
    }

    fun isSupported(context: Context): Boolean = isXiaomiDevice() && readIslandFeature() &&
        readProtocol(context.applicationContext).let { it != 1 && it != 2 }

    private fun isXiaomiDevice(): Boolean {
        return Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ||
            Build.MANUFACTURER.equals("Redmi", ignoreCase = true) ||
            Build.MANUFACTURER.equals("POCO", ignoreCase = true)
    }

    private fun readProtocol(context: Context): Int = runCatching {
        Settings.System.getInt(context.contentResolver, PROTOCOL_SETTING, 0)
    }.getOrDefault(0)

    private fun readIslandFeature(): Boolean {
        val booleanMethod = getBooleanMethod
        if (booleanMethod != null) {
            val value = runCatching {
                booleanMethod.invoke(null, ISLAND_PROPERTY, false) as? Boolean
            }.getOrNull()
            if (value != null) return value
        }
        val stringMethod = getStringMethod ?: return false
        return runCatching {
            when ((stringMethod.invoke(null, ISLAND_PROPERTY) as? String)?.trim()?.lowercase()) {
                "1", "true", "yes", "y", "on" -> true
                else -> false
            }
        }.getOrDefault(false)
    }
}
