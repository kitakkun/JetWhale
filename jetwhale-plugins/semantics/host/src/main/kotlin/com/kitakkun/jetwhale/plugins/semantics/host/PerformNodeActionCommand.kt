package com.kitakkun.jetwhale.plugins.semantics.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeAction
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeActionResult
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeCaptureOptions
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeSnapshot
import com.kitakkun.jetwhale.plugins.semantics.protocol.PerformNodeAction
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@OptIn(ExperimentalJetWhaleApi::class)
internal class PerformNodeActionCommand(
    private val lastSnapshot: () -> NodeTreeSnapshot?,
    private val capture: suspend (NodeTreeCaptureOptions) -> NodeTreeSnapshot,
    private val perform: suspend (PerformNodeAction) -> NodeActionResult,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.performNodeAction"
    override val description =
        "Invokes a semantics action on one node of the running app, addressed by the id findNodes or " +
            "getNodeTree reported. It needs no coordinates — prefer it over tapping coordinates. On Android, desktop " +
            "and iOS it runs the node's own action, so it cannot land on something that moved in the meantime. On the " +
            "web only Click is the node's own: LongClick and ScrollBy are mouse and wheel input synthesized at the " +
            "node's center, reaching whatever is drawn there, and their message says the page cannot confirm what took " +
            "it; SetText/InsertText type into the field's input. Returns {\"performed\", \"rootId\", \"nodeId\", \"action\", \"message\"}; " +
            "performed=false with a message when the node does not expose the action or declined it. " +
            "BringIntoView scrolls whatever surrounds the node — Compose scrollables, Android Views and iOS scroll views alike — " +
            "by the least amount that shows it whole, and works on any node except on the web; ScrollToIndex scrolls a lazy " +
            "list, a RecyclerView or an iOS table/collection view to an item that may not exist yet. Both land on the next frame: " +
            "capture the tree again afterwards to see the result."

    private val nodeId by int("The node's id, as reported by findNodes or getNodeTree.")
    private val action by enum(
        "The semantics action to invoke. Click is what a tap would do. SetText/InsertText require text; ScrollBy uses scrollX/scrollY; ScrollToIndex requires index; BringIntoView takes nothing.",
        NodeAction.entries,
    )
    private val rootId by stringOrNull(
        "The root the node belongs to. Optional: without it the node is looked up in the most recent capture, and the topmost root wins if the id appears in more than one.",
    )
    private val text by stringOrNull("The text for SetText or InsertText.")
    private val scrollX by intOrNull("Horizontal scroll distance in pixels (points on iOS, CSS pixels on the web) for ScrollBy. Defaults to 0.")
    private val scrollY by intOrNull("Vertical scroll distance in pixels (points on iOS, CSS pixels on the web) for ScrollBy. Defaults to 0.")
    private val index by intOrNull("The item index for ScrollToIndex, counted from 0 over the container's items.")

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val nodeId = arguments[nodeId]
        val action = arguments[action]
        val result: NodeActionResult
        val rootId: String
        try {
            rootId = arguments[this.rootId] ?: resolveRootId(nodeId)
            result = perform(
                PerformNodeAction(
                    rootId = rootId,
                    nodeId = nodeId,
                    action = action,
                    text = arguments[text],
                    scrollX = (arguments[scrollX] ?: 0).toFloat(),
                    scrollY = (arguments[scrollY] ?: 0).toFloat(),
                    index = arguments[index],
                ),
            )
        } catch (e: JetWhaleMessagingException) {
            return agentErrorJson(e)
        }

        return buildJsonObject {
            put("performed", result.performed)
            put("rootId", rootId)
            put("nodeId", nodeId)
            put("action", action.name)
            result.message?.let { put("message", it) }
        }.toString()
    }

    /**
     * The last capture is consulted first so the common flow — findNodes, then act on what it
     * returned — costs no extra round trip; a fresh capture is only taken when that misses, which
     * also covers a caller acting on an id it obtained some other way.
     */
    private suspend fun resolveRootId(nodeId: Int): String {
        lastSnapshot()?.findRootOf(nodeId)?.let { return it.rootId }
        val snapshot = capture(NodeTreeCaptureOptions(merged = true, includeInvisible = true, maxDepth = null))
        snapshot.findRootOf(nodeId)?.let { return it.rootId }
        throw JetWhaleMcpArgumentException(
            "unknown nodeId: $nodeId (it is in none of the ${snapshot.roots.size} current roots; capture the tree again and use a fresh id)",
        )
    }
}
