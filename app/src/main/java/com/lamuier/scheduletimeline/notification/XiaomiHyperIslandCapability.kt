package com.lamuier.scheduletimeline.notification

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings

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
    private const val FOCUS_URI = "content://miui.statusbar.notification.public"

    fun inspect(context: Context): XiaomiHyperIslandCapability {
        val appContext = context.applicationContext
        val xiaomiDevice = isXiaomiDevice()
        return XiaomiHyperIslandCapability(
            isXiaomiDevice = xiaomiDevice,
            protocol = if (xiaomiDevice) readProtocol(appContext) else 0,
            islandFeatureEnabled = xiaomiDevice && readIslandFeature(),
            focusPermissionGranted = xiaomiDevice && canShowFocus(appContext),
        )
    }

    fun isSupported(context: Context): Boolean = inspect(context).isOs3Supported

    private fun isXiaomiDevice(): Boolean {
        return Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ||
            Build.MANUFACTURER.equals("Redmi", ignoreCase = true) ||
            Build.MANUFACTURER.equals("POCO", ignoreCase = true)
    }

    private fun readProtocol(context: Context): Int = runCatching {
        Settings.System.getInt(context.contentResolver, PROTOCOL_SETTING, 0)
    }.getOrDefault(0)

    @SuppressLint("PrivateApi")
    private fun readIslandFeature(): Boolean {
        return runCatching {
            val systemProperties = Class.forName("android.os.SystemProperties")
            val getBoolean = systemProperties.getDeclaredMethod(
                "getBoolean",
                String::class.java,
                Boolean::class.javaPrimitiveType,
            )
            getBoolean.isAccessible = true
            getBoolean.invoke(null, ISLAND_PROPERTY, false) as Boolean
        }.getOrElse {
            runCatching {
                val systemProperties = Class.forName("android.os.SystemProperties")
                val get = systemProperties.getDeclaredMethod("get", String::class.java)
                get.isAccessible = true
                when ((get.invoke(null, ISLAND_PROPERTY) as? String)?.trim()?.lowercase()) {
                    "1", "true", "yes", "y", "on" -> true
                    else -> false
                }
            }.getOrDefault(false)
        }
    }

    private fun canShowFocus(context: Context): Boolean = runCatching {
        val result = context.contentResolver.call(
            Uri.parse(FOCUS_URI),
            "canShowFocus",
            null,
            Bundle().apply { putString("package", context.packageName) },
        )
        result?.getBoolean("canShowFocus", false) == true
    }.getOrDefault(false)
}
