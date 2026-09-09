package com.kitakkun.jetwhale.plugins.semantics.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeHitTesting
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeCaptureOptions
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeSnapshot
import com.kitakkun.jetwhale.plugins.semantics.protocol.ProbeTouch
import com.kitakkun.jetwhale.plugins.semantics.protocol.TouchProbeResult
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingException
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Checks a point against the app itself rather than against the captured tree, and reports where
 * the two disagree.
 *
 * The disagreement is the whole value of it: the tree cannot see an overlay that takes touches
 * without exposing semantics, so a point that reads as free but consumes a touch is exactly the
 * kind of bug neither the tree nor a screenshot shows.
 */
@OptIn(ExperimentalJetWhaleApi::class)
internal class ProbeTouchCommand(
    private val capture: suspend (NodeTreeCaptureOptions) -> NodeTreeSnapshot,
    private val probe: suspend (ProbeTouch) -> TouchProbeResult,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.probeTouch"
    override val description =
        "Sends a real touch down at the given screen coordinates and immediately cancels it, to find out whether the " +
            "app consumes a touch there: {\"consumed\", \"expected\": {node or null}, \"agrees\", \"note\"}. " +
            "Nothing is clicked — the cancel ends the gesture before a click can complete — but a pressed state or a " +
            "ripple may flash. Use it where nodeAt is not enough: \"consumed\" true with \"expected\" null means " +
            "something takes touches there without exposing any semantics, which is what an invisible overlay looks " +
            "like. Prefer performNodeAction to drive the app; this only asks a question."

    private val x by int("X coordinate in screen pixels — the space getNodeTree's \"bounds\" and \"tap\" are in.")
    private val y by int("Y coordinate in screen pixels.")

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val pointX = arguments[x].toFloat()
        val pointY = arguments[y].toFloat()

        val result = try {
            probe(ProbeTouch(screenX = pointX, screenY = pointY))
        } catch (e: JetWhaleMessagingException) {
            return agentErrorJson(e)
        }

        // Captured after the probe so the tree describes the screen the touch actually met, and
        // because the cancel leaves the UI as it found it.
        val snapshot = try {
            capture(NodeTreeCaptureOptions())
        } catch (e: JetWhaleMessagingException) {
            return agentErrorJson(e)
        }
        val target = NodeHitTesting.targetAt(snapshot.roots, pointX, pointY)
        val expectedRef = (target as? NodeHitTesting.TouchTarget.Node)?.ref
        val expected = expectedRef?.let { ref ->
            snapshot.roots.firstOrNull { it.rootId == ref.rootId }?.findNode(ref.nodeId)
        }
        // A window that swallows the tap is expected to consume it even though no node takes it, so
        // it agrees with a consuming app rather than reading as an overlay nobody can see.
        val expectsConsumption = target !is NodeHitTesting.TouchTarget.Nothing

        return buildJsonObject {
            put("consumed", result.consumed)
            result.rootId?.let { put("rootId", it) }
            put("expected", expected?.toMcpJson(rootId = expectedRef?.rootId, includeChildren = false) ?: JsonNull)
            (target as? NodeHitTesting.TouchTarget.Window)?.let { put("swallowedByWindow", it.rootId) }
            put("agrees", result.consumed == expectsConsumption)
            val note = result.message ?: note(target, consumed = result.consumed)
            note?.let { put("note", it) }
        }.toString()
    }
}

private fun note(target: NodeHitTesting.TouchTarget, consumed: Boolean): String? = when {
    target is NodeHitTesting.TouchTarget.Window ->
        "a touch-modal window (${target.rootId}) is over this point: it takes every tap that lands outside it, so " +
            "nothing in the windows below can be reached until it goes away."

    consumed && target is NodeHitTesting.TouchTarget.Nothing ->
        "something consumes touches here that the node tree does not show — an overlay with no semantics, or a gesture " +
            "handler on a plain layout. A node underneath cannot be tapped even though it looks reachable."

    !consumed && target is NodeHitTesting.TouchTarget.Node ->
        "the node here advertises an action but the app took no touch: its gesture handling may sit elsewhere, or an " +
            "ancestor is refusing the event."

    else -> null
}
