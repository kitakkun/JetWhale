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
