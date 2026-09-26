package com.kitakkun.jetwhale.plugins.mirror.host

import kotlin.math.ceil

/**
 * Where the grid's tiles go: every tile's screen shares one [screenHeight], each as wide as its
 * device's aspect ratio makes it, laid out in [rows] of tile indices. [scrolls] is true when even at
 * the smallest readable height the tiles do not fit, and the rows then scroll.
 *
 * @property groupWidth the widest row, with its gaps; the group is centered in the available width.
 * @property groupHeight every row with its caption and gaps.
 */
internal class DeviceTileLayout(
    val screenHeight: Float,
    val rows: List<List<Int>>,
    val scrolls: Boolean,
    val groupWidth: Float,
    val groupHeight: Float,
)

/**
 * The fixed measures of a tile, in dp.
 *
 * @property tileExtraWidth what a tile adds around its screen horizontally (padding).
 * @property captionHeight what a tile adds below its screen (the caption and padding).
 * @property minScreenHeight below this a screen stops being readable, so the rows scroll instead.
 */
internal class TileMetrics(
    val gap: Float,
    val tileExtraWidth: Float,
    val captionHeight: Float,
    val minScreenHeight: Float,
    val maxScreenHeight: Float,
)

/**
 * Sizes the tiles so that all of them fit [width] by [height] without scrolling, as large as
 * possible up to [TileMetrics.maxScreenHeight], trying every column count and keeping the one that
 * gives the tallest screens. A phone is roughly half as wide as it is tall and a tablet wider, so
 * rows are filled in order and each keeps its devices' own shapes. When not even
 * [TileMetrics.minScreenHeight] fits, the tiles stay at that size and the rows scroll.
 *
 * @param aspectRatios each device's screen width divided by its height.
 */
internal fun layoutDeviceTiles(aspectRatios: List<Float>, width: Float, height: Float, metrics: TileMetrics): DeviceTileLayout {
    if (aspectRatios.isEmpty()) return DeviceTileLayout(screenHeight = 0f, rows = emptyList(), scrolls = false, groupWidth = 0f, groupHeight = 0f)
    val fitter = TileFitter(aspectRatios, metrics)
    val (fitting, best) = (1..aspectRatios.size)
        .map { columns -> aspectRatios.indices.chunked(columns) }
        .map { rows -> rows to fitter.tallestScreenFor(rows, width = width, height = height) }
        .maxBy { (_, screenHeight) -> screenHeight }
    return if (best >= metrics.minScreenHeight) {
        fitter.measured(fitting, screenHeight = minOf(best, metrics.maxScreenHeight), scrolls = false)
    } else {
        fitter.measured(fitter.wrapRows(width), screenHeight = metrics.minScreenHeight, scrolls = true)
    }
}

/** The arithmetic of one layout: the devices' shapes and the tile measures they are laid out with. */
private class TileFitter(private val aspectRatios: List<Float>, private val metrics: TileMetrics) {
    /** The tallest screen at which [rows] fit [width] by [height]; negative when not even a sliver does. */
    fun tallestScreenFor(rows: List<List<Int>>, width: Float, height: Float): Float {
        val byHeight = (height - metrics.gap * (rows.size - 1)) / rows.size - metrics.captionHeight
        val byWidth = rows.minOf { row -> (width - metrics.gap * (row.size - 1) - metrics.tileExtraWidth * row.size) / row.sumOf { aspectRatios[it].toDouble() }.toFloat() }
        return minOf(byHeight, byWidth)
    }

    /** Rows at the smallest height, filled left to right until the next tile would pass [width]. */
    fun wrapRows(width: Float): List<List<Int>> {
        val rows = mutableListOf<MutableList<Int>>()
        var used = 0f
        aspectRatios.indices.forEach { index ->
            val tile = tileWidth(index, metrics.minScreenHeight)
            val current = rows.lastOrNull()
            if (current == null || used + metrics.gap + tile > width) {
                rows += mutableListOf(index)
                used = tile
            } else {
                current += index
                used += metrics.gap + tile
            }
        }
        return rows
    }

    fun measured(rows: List<List<Int>>, screenHeight: Float, scrolls: Boolean): DeviceTileLayout {
        val groupWidth = rows.maxOf { row -> row.sumOf { tileWidth(it, screenHeight).toDouble() }.toFloat() + metrics.gap * (row.size - 1) }
        val groupHeight = (screenHeight + metrics.captionHeight) * rows.size + metrics.gap * (rows.size - 1)
        return DeviceTileLayout(screenHeight = screenHeight, rows = rows, scrolls = scrolls, groupWidth = ceil(groupWidth), groupHeight = ceil(groupHeight))
    }

    private fun tileWidth(index: Int, screenHeight: Float): Float = aspectRatios[index] * screenHeight + metrics.tileExtraWidth
}
