package com.kitakkun.jetwhale.plugins.androiddevice.host

import kotlin.math.roundToInt

/** The screen size a tap has to fall inside, in screen pixels. */
internal data class ScreenSize(val width: Int, val height: Int)

/**
 * `wm size`. An override size is what the window manager is actually driving the screen at, so it
 * wins over the physical size whenever one is set:
 * ```
 * Physical size: 1080x2400
 * Override size: 720x1600
 * ```
 */
internal fun parseWmSize(output: String): ScreenSize? {
    val sizes = SIZE_PATTERN.findAll(output).associate { match ->
        match.groupValues[1].lowercase() to ScreenSize(match.groupValues[2].toInt(), match.groupValues[3].toInt())
    }
    return sizes["override"] ?: sizes["physical"]
}

/** `wm density`, with the same override-wins rule as [parseWmSize]. */
internal fun parseWmDensity(output: String): Int? {
    val densities = DENSITY_PATTERN.findAll(output).associate { match ->
        match.groupValues[1].lowercase() to match.groupValues[2].toInt()
    }
    return densities["override"] ?: densities["physical"]
}

/**
 * The rotation of the default display from `dumpsys window displays`, as the `Surface.ROTATION_*`
 * index (0, 1, 2, 3) the platform's own APIs use. The window manager spells its current rotation in
 * **degrees** (`mCurrentRotation=ROTATION_90`), so that form is converted; `mRotation` is already an
 * index and is read as one.
 */
internal fun parseRotation(output: String): Int? {
    val degrees = ROTATION_DEGREES_PATTERN.find(output)?.groupValues?.get(1)?.toIntOrNull()
    if (degrees != null) return DEGREES_TO_ROTATION[degrees]
    return ROTATION_INDEX_PATTERN.find(output)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 0..3 }
}

/** `getprop` in its `[key]: [value]` listing form. */
internal fun parseGetProps(output: String): Map<String, String> = GETPROP_PATTERN.findAll(output)
    .associate { match -> match.groupValues[1] to match.groupValues[2] }

/** Converts a density-independent coordinate to screen pixels the way the platform does. */
internal fun dpToPixels(value: Int, density: Int): Int = (value * density / DEFAULT_DENSITY.toDouble()).roundToInt()

/** The density at which one dp is one pixel. */
private const val DEFAULT_DENSITY = 160

private val SIZE_PATTERN = Regex("(Physical|Override) size:\\s*(\\d+)x(\\d+)", RegexOption.IGNORE_CASE)
private val DENSITY_PATTERN = Regex("(Physical|Override) density:\\s*(\\d+)", RegexOption.IGNORE_CASE)
private val ROTATION_DEGREES_PATTERN = Regex("mCurrentRotation=ROTATION_(\\d+)")
private val ROTATION_INDEX_PATTERN = Regex("\\bmRotation=(\\d)\\b")
private val DEGREES_TO_ROTATION = mapOf(0 to 0, 90 to 1, 180 to 2, 270 to 3)
private val GETPROP_PATTERN = Regex("^\\[([^\\]]+)]: \\[(.*)]$", RegexOption.MULTILINE)
