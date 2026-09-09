package com.kitakkun.jetwhale.plugins.semantics.agent

import com.kitakkun.jetwhale.plugins.semantics.protocol.TouchProbeResult

/**
 * A node source that can send a real touch into the UI it reads.
 *
 * A capability a source may or may not have, like [ViewAttributeSource]: it takes a platform input
 * system to dispatch an event, and a composition reached through its `SemanticsOwner` alone has no
 * way in. A source without it simply does not implement this, and the plugin reports that the probe
 * is unsupported for its roots.
 */
interface TouchProbeSource {
    /**
     * Sends a touch down at ([screenX], [screenY]) and cancels it, reporting whether anything took
     * it.
     *
     * Answers the question a capture cannot: whether something consumes touches at a point without
     * saying so in its semantics. The cancel is what keeps it a probe rather than a tap — no click
     * completes — but a pressed state or a ripple can still flash where it landed.
     */
    suspend fun probeTouch(screenX: Float, screenY: Float): TouchProbeResult
}
