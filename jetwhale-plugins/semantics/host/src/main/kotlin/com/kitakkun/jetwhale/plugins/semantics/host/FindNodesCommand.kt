package com.kitakkun.jetwhale.plugins.semantics.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeCaptureOptions
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeSnapshot
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@OptIn(ExperimentalJetWhaleApi::class)
internal class FindNodesCommand(
    private val capture: suspend (NodeTreeCaptureOptions) -> NodeTreeSnapshot,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.findNodes"
    override val description =
        "Captures the Compose node tree and returns the nodes matching the given criteria as a flat " +
            "list: {\"nodes\": [...], \"totalMatches\", \"truncated\"}. Each entry carries its \"rootId\" and " +
            "\"id\", which together address the node in performNodeAction, plus screen-pixel \"bounds\" and a " +
            "\"tap\" point. Criteria are combined with AND; matching is case-insensitive and by substring " +
            "unless exact is set. Omit every criterion to list all interactive nodes on screen."

    private val text by stringOrNull("Match the node's text (or a text field's current content).")
    private val contentDescription by stringOrNull("Match the node's contentDescription.")
    private val testTag by stringOrNull("Match the node's Modifier.testTag — the most reliable identifier when the app sets one.")
    private val resourceId by stringOrNull(
        "Match an Android View node's resource id — the entry name of its android:id, e.g. \"submit\" for @id/submit. Compared whole, not by substring.",
    )
    private val role by stringOrNull("Match the node's semantics role, e.g. Button, Checkbox, Tab, Image.")
    private val interactiveOnly by booleanOrNull(
        "Keep only nodes that expose an action, are editable, or scroll. Defaults to true when no other criterion is given, false otherwise.",
    )
    private val exact by booleanOrNull("Compare whole values instead of substrings. Defaults to false.")
    private val merged by booleanOrNull("Search the merged tree (default true). See getNodeTree.")
    private val includeInvisible by booleanOrNull("Include nodes that are not laid out or fully clipped away. Defaults to false.")
    private val limit by intOrNull("Maximum number of nodes to return, in tree order. Returns all matches if omitted.")

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val limit = arguments[limit]
        if (limit != null && limit <= 0) throw JetWhaleMcpArgumentException("invalid limit: $limit (expected a positive integer)")

        val criteria = NodeQuery(
            text = arguments[text],
            contentDescription = arguments[contentDescription],
            testTag = arguments[testTag],
            resourceId = arguments[resourceId],
            role = arguments[role],
            exact = arguments[exact] ?: false,
        )
        // With no criterion at all, "every node on screen" is never the useful answer; the caller is
        // asking what there is to operate. An explicit interactiveOnly still wins.
        val query = criteria.copy(interactiveOnly = arguments[interactiveOnly] ?: criteria.isEmpty)

        val snapshot = try {
            capture(
                NodeTreeCaptureOptions(
                    merged = arguments[merged] ?: true,
                    includeInvisible = arguments[includeInvisible] ?: false,
                    maxDepth = null,
                ),
            )
        } catch (e: JetWhaleMessagingException) {
            return agentErrorJson(e)
        }

        val matches = snapshot.roots.flatMap { root ->
            val nodes = root.node?.asSequence().orEmpty()
            nodes.filter { it.matches(query) }.map { root.rootId to it }.toList()
        }
        val page = if (limit == null) matches else matches.take(limit)

        return buildJsonObject {
            put("nodes", JsonArray(page.map { (rootId, node) -> node.toMcpJson(rootId = rootId, includeChildren = false) }))
            put("totalMatches", matches.size)
            put("truncated", page.size < matches.size)
            if (snapshot.warnings.isNotEmpty()) {
                put("warnings", JsonArray(snapshot.warnings.map { JsonPrimitive(it) }))
            }
        }.toString()
    }
}
