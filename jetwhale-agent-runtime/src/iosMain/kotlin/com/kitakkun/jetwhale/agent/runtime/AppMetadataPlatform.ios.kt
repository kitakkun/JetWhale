@file:OptIn(ExperimentalForeignApi::class)

package com.kitakkun.jetwhale.agent.runtime

import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSBundle
import platform.UIKit.UIDevice
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation

/** Edge length the app icon is rasterized to, matching the cap the negotiation payload documents. */
private const val APP_ICON_SIZE_PT: Double = 64.0

internal actual fun getDeviceId(): String? = UIDevice.currentDevice.identifierForVendor?.UUIDString

internal actual fun resolveDefaultAppName(): String? {
    val info = NSBundle.mainBundle.infoDictionary ?: return null
    return (info["CFBundleDisplayName"] ?: info["CFBundleName"]) as? String
}

/**
 * Loads the launcher icon by the names the bundle advertises and rasterizes it to a 64x64 PNG.
 * The icon itself lives in the asset catalog, which cannot be read as a file, but the names in
 * `Info.plist` are enough for `UIImage.imageNamed` to find it.
 */
internal actual fun resolveDefaultAppIconPng(): ByteArray? {
    val icon = appIconNames().firstNotNullOfOrNull { UIImage.imageNamed(it) } ?: return null
    val scaled = icon.scaledToSquare(APP_ICON_SIZE_PT) ?: return null
    return UIImagePNGRepresentation(scaled)?.toByteArray()
}

/**
 * Every name the bundle offers for its icon, best first. Which key holds it depends on how the
 * icon was declared: an asset catalog fills in `CFBundleIcons`, while a project carrying loose
 * files declares them at the top level instead.
 */
private fun appIconNames(): List<String> {
    val info = NSBundle.mainBundle.infoDictionary ?: return emptyList()
    val primary = (info["CFBundleIcons"] as? Map<*, *>)?.get("CFBundlePrimaryIcon") as? Map<*, *>
    return buildList {
        // CFBundleIconFiles is ordered smallest first, so walk it backwards for the sharpest icon.
        addAll(primary?.iconFileNames().orEmpty().asReversed())
        (primary?.get("CFBundleIconName") as? String)?.let(::add)
        addAll(info.iconFileNames().asReversed())
        (info["CFBundleIconFile"] as? String)?.let(::add)
    }
}

private fun Map<*, *>.iconFileNames(): List<String> = (get("CFBundleIconFiles") as? List<*>)?.filterIsInstance<String>().orEmpty()

private fun UIImage.scaledToSquare(size: Double): UIImage? {
    UIGraphicsBeginImageContextWithOptions(CGSizeMake(size, size), false, 1.0)
    drawInRect(CGRectMake(0.0, 0.0, size, size))
    val scaled = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()
    return scaled
}
