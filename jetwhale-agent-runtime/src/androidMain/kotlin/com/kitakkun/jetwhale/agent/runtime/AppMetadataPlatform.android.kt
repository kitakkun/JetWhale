package com.kitakkun.jetwhale.agent.runtime

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.provider.Settings

/**
 * Retrieves the current [Application] context without requiring the host app to pass one in.
 * Uses the hidden `ActivityThread.currentApplication()` entry point reflectively so metadata
 * resolution stays best-effort and never crashes the debuggee.
 */
private fun currentApplicationOrNull(): Context? = try {
    val activityThread = Class.forName("android.app.ActivityThread")
    val method = activityThread.getMethod("currentApplication")
    method.invoke(null) as? Application
} catch (_: Throwable) {
    null
}

@SuppressLint("HardwareIds")
internal actual fun getDeviceId(): String? = try {
    val context = currentApplicationOrNull() ?: return null
    Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
} catch (_: Throwable) {
    null
}

internal actual fun resolveDefaultAppName(): String? = try {
    val context = currentApplicationOrNull() ?: return null
    val applicationInfo = context.applicationInfo
    context.packageManager.getApplicationLabel(applicationInfo).toString()
} catch (_: Throwable) {
    null
}
