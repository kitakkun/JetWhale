package com.kitakkun.jetwhale.plugins.androiddevice.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException

/** The space a pointer tool's coordinates are given in. */
internal enum class CoordinateUnit { PX, DP }

internal const val UNIT_DESCRIPTION =
    "Unit the coordinates are given in: PX (screen pixels, the default, and what every tool here " +
        "reports) or DP, converted with the device's density."

/**
 * What the device says its screen is, as far as it could be read. Any part can be missing on a
 * device whose window manager does not answer, which costs validation rather than the whole call.
 *
 * @property size The screen as it is currently rotated, which is the space `input` takes its
 *   coordinates in. Unknown when either the size or the rotation could not be read.
 * @property rotation The `Surface.ROTATION_*` index of the default display.
 */
internal class CoordinateSpace(
    val size: ScreenSize?,
    val density: Int?,
    val rotation: Int?,
)

/**
 * Reads `wm size`, `wm density` and the current rotation from the device. `wm size` reports the
 * screen in its natural orientation whatever the rotation, so a quarter turn swaps its sides.
 */
@OptIn(ExperimentalJetWhaleApi::class)
internal suspend fun DeviceTarget.readCoordinateSpace(): CoordinateSpace {
    val naturalSize = shell("wm", "size", timeout = AdbTimeouts.QUICK).let { if (it.exitCode == 0) parseWmSize(it.output) else null }
    val density = shell("wm", "density", timeout = AdbTimeouts.QUICK).let { if (it.exitCode == 0) parseWmDensity(it.output) else null }
    val rotation = shell("dumpsys", "window", "displays", timeout = AdbTimeouts.SHELL).let { if (it.exitCode == 0) parseRotation(it.output) else null }
    val size = when (rotation) {
        null -> null
        1, 3 -> naturalSize?.let { ScreenSize(width = it.height, height = it.width) }
        else -> naturalSize
    }
    return CoordinateSpace(size = size, density = density, rotation = rotation)
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
