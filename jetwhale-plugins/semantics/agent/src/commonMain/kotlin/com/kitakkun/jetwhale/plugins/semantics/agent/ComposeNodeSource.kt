package com.kitakkun.jetwhale.plugins.semantics.agent

import com.kitakkun.jetwhale.plugins.semantics.protocol.ComposeRoot
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeActionResult
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeCaptureOptions
import com.kitakkun.jetwhale.plugins.semantics.protocol.PerformNodeAction

/**
 * One Compose root the agent can read.
 *
 * Reaching a live composition is platform-specific — on Android a root is an `AndroidComposeView`
 * and its semantics may only be touched on the main thread — so the platform integration
 * (`jetwhale-compose-semantics-inspector-agent-android`) implements this and registers it with
 * [ComposeNodeSourceRegistry]. The core plugin only ever sees this interface, which is what keeps
 * it free of a Compose dependency.
 */
interface ComposeNodeSource {
    /**
     * Identifies this root for the lifetime of its registration; travels to the host as
     * [ComposeRoot.rootId] and comes back in [PerformNodeAction.rootId].
     */
    val sourceId: String

    /**
     * Reads the semantics tree of this root, or returns `null` when the root currently has nothing
     * to report (e.g. its view is detached). Implementations are responsible for hopping to
     * whatever thread their toolkit requires.
     */
    suspend fun capture(options: NodeTreeCaptureOptions): ComposeRoot?

    /** Invokes [request]'s action on the node it names within this root. */
    suspend fun performAction(request: PerformNodeAction): NodeActionResult
}
