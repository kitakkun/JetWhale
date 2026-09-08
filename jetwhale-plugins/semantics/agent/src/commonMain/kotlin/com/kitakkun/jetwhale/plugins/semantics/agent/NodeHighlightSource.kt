package com.kitakkun.jetwhale.plugins.semantics.agent

import com.kitakkun.jetwhale.plugins.semantics.protocol.HighlightResult
import kotlin.time.Duration

/**
 * A node source that can point at one of its nodes on the device itself.
 *
 * Optional, like [ViewAttributeSource]: a source with nowhere to draw — a composition read through
 * its `SemanticsOwner` alone — does not implement it, and the plugin answers "not supported" for its
 * roots rather than every source carrying a no-op.
 */
interface NodeHighlightSource {
    /**
     * Shows [nodeId], or clears the highlight when it is `null`.
     *
     * The highlight clears itself once [ttl] passes without another call renewing it, so a host that
     * dies cannot leave a box on the app's screen for the rest of the process's life.
     */
    suspend fun highlight(nodeId: Int?, ttl: Duration): HighlightResult
}
