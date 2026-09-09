package com.kitakkun.jetwhale.plugins.semantics.protocol

import com.kitakkun.jetwhale.protocol.messaging.JetWhaleRequest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// -- Requests: host -> agent (debuggee) --------------------------------------

/**
 * Captures the Compose semantics tree of every root the agent knows about.
 *
 * The host asks on demand (a refresh, an MCP tool call) rather than the agent streaming changes:
 * a capture is cheap and reading the tree only when someone looks at it keeps the debuggee's main
 * thread free the rest of the time.
 */
@SerialName("compose/capture_node_tree")
@Serializable
data class CaptureNodeTree(
    val options: NodeTreeCaptureOptions = NodeTreeCaptureOptions(),
) : JetWhaleRequest<NodeTreeSnapshot>

/**
 * Invokes a semantics action on one node, addressed by the [rootId]/[nodeId] pair a
 * [NodeTreeSnapshot] reported.
 *
 * This runs the node's own action, so it works regardless of where the window sits on screen and
 * without going through the input system — which is what makes it usable for driving an app from
 * an AI agent.
 */
@SerialName("compose/perform_node_action")
@Serializable
data class PerformNodeAction(
    val rootId: String,
    val nodeId: Int,
    val action: NodeAction,
    /** Text for [NodeAction.SetText] / [NodeAction.InsertText]; ignored otherwise. */
    val text: String? = null,
    /** Horizontal scroll distance in pixels for [NodeAction.ScrollBy]; ignored otherwise. */
    val scrollX: Float = 0f,
    /** Vertical scroll distance in pixels for [NodeAction.ScrollBy]; ignored otherwise. */
    val scrollY: Float = 0f,
) : JetWhaleRequest<NodeActionResult>

/**
 * Sends a real touch down at a screen point and takes it straight back with a cancel, to find out
 * whether anything in the app consumes a touch there.
 *
 * The one question a capture cannot answer. [NodeHitTesting] reads the tree, so it cannot see an
 * overlay that takes touches without exposing semantics, nor a gesture an ancestor swallows;
 * dispatching the event and asking the platform does. Down-then-cancel is deliberately not a tap:
 * the sequence a click needs never completes, so nothing is clicked — though a pressed state or a
 * ripple can flash where the touch landed.
 */
@SerialName("compose/probe_touch")
@Serializable
data class ProbeTouch(
    /** X in screen pixels — the space [UiNode.boundsInScreen] is in. */
    val screenX: Float,
    /** Y in screen pixels. */
    val screenY: Float,
) : JetWhaleRequest<TouchProbeResult>

/** What the platform did with the touch [ProbeTouch] sent. */
@Serializable
data class TouchProbeResult(
    /** `true` when the app took the touch down; `false` when it fell through everything. */
    val consumed: Boolean,
    /** The window the probe was dispatched to, when one was found under the point. */
    val rootId: String? = null,
    /** Why no touch was sent — no window under the point, or a platform that cannot dispatch one. */
    val message: String? = null,
)
