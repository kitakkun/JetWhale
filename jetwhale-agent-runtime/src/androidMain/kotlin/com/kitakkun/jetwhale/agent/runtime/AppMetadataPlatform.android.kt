package com.kitakkun.jetwhale.agent.runtime

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.provider.Settings
import java.io.ByteArrayOutputStream

/** Edge length the app icon is rasterized to, matching the cap the negotiation payload documents. */
private const val APP_ICON_SIZE_PX: Int = 64

@SuppressLint("HardwareIds")
internal actual fun getDeviceId(): String? = try {
    val context = currentApplicationOrNull() ?: return null
    Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
} catch (_: SecurityException) {
    null
}

/**
 * Retrieves the current [Application] context without requiring the host app to pass one in.
 * Uses the hidden `ActivityThread.currentApplication()` entry point reflectively so metadata
 * resolution stays best-effort and never crashes the debuggee.
 */
private fun currentApplicationOrNull(): Context? = try {
    val activityThread = Class.forName("android.app.ActivityThread")
    val method = activityThread.getMethod("currentApplication")
    method.invoke(null) as? Application
} catch (_: ReflectiveOperationException) {
    null
} catch (_: SecurityException) {
    null
}

internal actual fun resolveDefaultAppName(): String? {
    val context = currentApplicationOrNull() ?: return null
    return context.packageManager.getApplicationLabel(context.applicationInfo).toString()
}

/**
 * Rasterizes the launcher icon into a 64x64 PNG. Going through [Drawable] rather than the raw
 * resource is what makes adaptive icons work: those are XML, so they have no PNG bytes to read.
 */
// Drawing runs the app's own Drawable, which can throw anything; the icon is optional metadata.
@Suppress("KOTRAIL_CATCH_TOO_BROAD")
internal actual fun resolveDefaultAppIconPng(): ByteArray? = try {
    val context = currentApplicationOrNull() ?: return null
    context.packageManager.getApplicationIcon(context.applicationInfo).toPngBytesOrNull()
} catch (_: Throwable) {
    null
}

private fun Drawable.toPngBytesOrNull(): ByteArray? {
    val bitmap = Bitmap.createBitmap(APP_ICON_SIZE_PX, APP_ICON_SIZE_PX, Bitmap.Config.ARGB_8888)
    return try {
        setBounds(0, 0, APP_ICON_SIZE_PX, APP_ICON_SIZE_PX)
        draw(Canvas(bitmap))
        val stream = ByteArrayOutputStream()
        if (bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) stream.toByteArray() else null
    } finally {
        bitmap.recycle()
    }
}
