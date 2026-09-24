package com.kitakkun.jetwhale.plugins.screen.agent

/**
 * Decides when the next frame may be captured: only after the app redrew, only while the host has
 * credit left, and never sooner than [minIntervalMillis] after the previous capture. Redraws that
 * happen while waiting collapse into one capture of the latest picture.
 *
 * Not thread-safe: the capture loop owns it on one thread.
 */
internal class FramePacer(
    private val minIntervalMillis: Long,
    initialCredits: Int,
) {
    private var credits = initialCredits

    // The stream opens with the current picture, not with the first redraw after it.
    private var dirty = true
    private var lastCaptureAtMillis: Long? = null

    val remainingCredits: Int get() = credits

    fun markDirty() {
        dirty = true
    }

    fun grant(count: Int) {
        credits += count
    }

    /** How long to wait before capturing; null when there is nothing new or no credit to send it with. */
    fun delayBeforeCapture(nowMillis: Long): Long? {
        if (!dirty || credits <= 0) return null
        val last = lastCaptureAtMillis ?: return 0
        return (last + minIntervalMillis - nowMillis).coerceAtLeast(0)
    }

    fun onCaptureStarted(nowMillis: Long) {
        dirty = false
        credits--
        lastCaptureAtMillis = nowMillis
    }
}
