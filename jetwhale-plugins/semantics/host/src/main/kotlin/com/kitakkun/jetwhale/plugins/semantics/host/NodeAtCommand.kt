package com.kitakkun.jetwhale.plugins.semantics.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpResult
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeHitTesting
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeCaptureOptions
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeSnapshot
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingException
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Answers what a tap at a screen coordinate would reach — the question a caller has once it has
 * picked a point from a screenshot rather than from a node's own bounds.
 */
@OptIn(ExperimentalJetWhaleApi::class)
internal class NodeAtCommand(
    private val capture: suspend (NodeTreeCaptureOptions) -> NodeTreeSnapshot,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.nodeAt"
    override val description =
        "Reports which node a tap at the given screen coordinates would be dispatched to: " +
            "{\"node\": {\"rootId\", \"id\", ...}} or {\"node\": null} when nothing there takes touch input. " +
            "Windows are searched from the top down and, within one, the last-drawn node first, the way the " +
            "platform dispatches a touch — so this is what a coordinate read off a screenshot actually hits. " +
            "It cannot see a gesture consumed by an ancestor or an overlay that exposes no semantics, so it " +
            "errs towards reporting a node as reachable."

    private val x by int("X coordinate in screen pixels — the space getNodeTree's \"bounds\" and \"tap\" are in.")
    private val y by int("Y coordinate in screen pixels.")
    private val merged by booleanOrNull("Search the merged tree (default true). See getNodeTree.")

    override suspend fun execute(arguments: JetWhaleMcpArguments): JetWhaleMcpResult {
        val pointX = arguments[x].toFloat()
        val pointY = arguments[y].toFloat()

        val snapshot = try {
            capture(NodeTreeCaptureOptions(merged = arguments[merged] ?: true))
        } catch (e: JetWhaleMessagingException) {
            return appDidNotAnswerResult(e)
        }

        val hit = NodeHitTesting.nodeAt(snapshot.roots, pointX, pointY)
        val node = hit?.let { ref ->
            snapshot.roots.firstOrNull { it.rootId == ref.rootId }?.findNode(ref.nodeId)
        }

        return JetWhaleMcpResult.json(
            buildJsonObject {
                put("node", node?.toMcpJson(rootId = hit?.rootId, includeChildren = false) ?: JsonNull)
            },
        )
    }
}
