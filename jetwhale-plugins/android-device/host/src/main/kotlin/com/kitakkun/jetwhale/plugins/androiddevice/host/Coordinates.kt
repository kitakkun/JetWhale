package com.kitakkun.jetwhale.plugins.androiddevice.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException

/** The space a pointer tool's coordinates are given in. */
internal enum class CoordinateUnit { PX, DP }

internal const val UNIT_DESCRIPTION =
    "Unit the coordinates are given in: PX (screen pixels, the default, and what every tool here " +
        "reports) or DP, converted with the device's density."

/**
 * What the device says its screen is, as far as it could be read. Either half can be missing on a
 * device whose window manager does not answer, which costs validation rather than the whole call.
 */
internal class CoordinateSpace(
    val size: ScreenSize?,
    val density: Int?,
)

/** Reads `wm size` and `wm density` from the device. */
internal suspend fun DeviceTarget.readCoordinateSpace(): CoordinateSpace {
    val size = shell("wm", "size", timeout = AdbTimeouts.QUICK).let { if (it.exitCode == 0) parseWmSize(it.output) else null }
    val density = shell("wm", "density", timeout = AdbTimeouts.QUICK).let { if (it.exitCode == 0) parseWmDensity(it.output) else null }
    return CoordinateSpace(size = size, density = density)
}

@OptIn(ExperimentalJetWhaleApi::class)
internal fun CoordinateSpace.toPixels(value: Int, unit: CoordinateUnit): Int = when (unit) {
    CoordinateUnit.PX -> value

    CoordinateUnit.DP -> dpToPixels(
        value,
        density ?: throw JetWhaleMcpArgumentException("unit DP needs the device density, which `wm density` did not report; give the coordinates in pixels instead"),
    )
}

/**
 * Rejects a point that is not on the screen. An off-screen tap is accepted silently by
 * `input tap` and simply does nothing, which is the failure mode this check exists to turn into an
 * answer.
 */
@OptIn(ExperimentalJetWhaleApi::class)
internal fun CoordinateSpace.requireOnScreen(xName: String, x: Int, yName: String, y: Int) {
    val size = size ?: return
    if (x < 0 || x >= size.width || y < 0 || y >= size.height) {
        throw JetWhaleMcpArgumentException(
            "$xName=$x, $yName=$y is outside the screen, which is ${size.width}x${size.height} pixels",
        )
    }
}
