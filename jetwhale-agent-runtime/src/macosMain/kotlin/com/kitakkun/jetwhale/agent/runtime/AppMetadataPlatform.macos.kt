@file:OptIn(ExperimentalForeignApi::class)

package com.kitakkun.jetwhale.agent.runtime

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AppKit.NSBitmapImageFileType
import platform.AppKit.NSBitmapImageRep
import platform.AppKit.NSCalibratedRGBColorSpace
import platform.AppKit.NSGraphicsContext
import platform.AppKit.NSImage
import platform.AppKit.NSWorkspace
import platform.AppKit.representationUsingType
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSBundle

/** Edge length the app icon is rasterized to, matching the cap the negotiation payload documents. */
private const val APP_ICON_SIZE_PX: Int = 64

// macOS does not expose a stable per-device id without extra entitlements, so device id is left
// unresolved; the bundle name is used as the application name when available.
internal actual fun getDeviceId(): String? = null

internal actual fun resolveDefaultAppName(): String? {
    val info = NSBundle.mainBundle.infoDictionary ?: return null
    return (info["CFBundleDisplayName"] ?: info["CFBundleName"]) as? String
}

/**
 * Rasterizes the bundle's icon to a 64x64 PNG. Asking the workspace for the bundle path covers both
 * an `.icns` in the bundle and an asset-catalog icon, and works without an `NSApplication` — which a
 * plain executable does not have.
 */
internal actual fun resolveDefaultAppIconPng(): ByteArray? = NSWorkspace.sharedWorkspace.iconForFile(NSBundle.mainBundle.bundlePath).pngBytesOrNull(APP_ICON_SIZE_PX)

private fun NSImage.pngBytesOrNull(size: Int): ByteArray? {
    val representation = NSBitmapImageRep(
        bitmapDataPlanes = null,
        pixelsWide = size.toLong(),
        pixelsHigh = size.toLong(),
        bitsPerSample = 8,
        samplesPerPixel = 4,
        hasAlpha = true,
        isPlanar = false,
        colorSpaceName = NSCalibratedRGBColorSpace,
        bytesPerRow = 0,
        bitsPerPixel = 0,
    )
    val context = NSGraphicsContext.graphicsContextWithBitmapImageRep(representation) ?: return null
    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.currentContext = context
    drawInRect(CGRectMake(0.0, 0.0, size.toDouble(), size.toDouble()))
    NSGraphicsContext.restoreGraphicsState()
    return representation.representationUsingType(NSBitmapImageFileType.NSBitmapImageFileTypePNG, mapOf<Any?, Any?>())?.toByteArray()
}
