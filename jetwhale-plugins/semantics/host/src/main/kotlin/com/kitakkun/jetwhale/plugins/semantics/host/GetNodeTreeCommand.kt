package com.kitakkun.jetwhale.plugins.semantics.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeCaptureOptions
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeSnapshot
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingException

@OptIn(ExperimentalJetWhaleApi::class)
internal class GetNodeTreeCommand(
    private val capture: suspend (NodeTreeCaptureOptions) -> NodeTreeSnapshot,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.getNodeTree"
    override val description =
        "Captures the Compose node tree of the running app right now and returns it as JSON: " +
            "{\"capturedAtMs\", \"captureDurationMs\", \"merged\", \"roots\": [{\"rootId\", \"label\", \"density\", \"node\"}]}. " +
            "Each node carries id, role, text, contentDescription, testTag, its semantics actions, " +
            "screen-pixel \"bounds\", and a \"tap\" point (the centre, ready for `adb shell input tap`). " +
            "A dialog or popup appears as its own root. Prefer findNodes when you are looking for a " +
            "specific element, and performNodeAction over tapping coordinates."

    private val merged by booleanOrNull(
        "true (default) returns the merged tree an accessibility service sees, where a Button's label is folded into the clickable node. false keeps every semantics node separate.",
    )
    private val includeInvisible by booleanOrNull(
        "Include nodes that are not laid out or fully clipped away. Defaults to false.",
    )
    private val maxDepth by intOrNull(
        "Stop descending past this depth (each root's own node is depth 0). Returns the whole tree if omitted.",
    )
    private val interactiveOnly by booleanOrNull(
        "Keep only nodes that expose an action, are editable, or scroll — plus their ancestors, so the structure is preserved. Defaults to false.",
    )
    private val rootId by stringOrNull(
        "Return only this root. Use it to look at just the dialog on top, for example. Returns every root if omitted.",
    )

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val snapshot = try {
            capture(
                NodeTreeCaptureOptions(
                    merged = arguments[merged] ?: true,
                    includeInvisible = arguments[includeInvisible] ?: false,
                    maxDepth = arguments[maxDepth],
                ),
            )
        } catch (e: JetWhaleMessagingException) {
            return agentErrorJson(e)
        }

        val requestedRootId = arguments[rootId]
        val roots = snapshot.roots.filter { requestedRootId == null || it.rootId == requestedRootId }
        if (requestedRootId != null && roots.isEmpty()) {
            throw JetWhaleMcpArgumentException(
                "unknown rootId: $requestedRootId (known roots: ${snapshot.roots.joinToString { it.rootId }})",
            )
        }

        val pruned = if (arguments[interactiveOnly] == true) {
            roots.map { root -> root.copy(node = root.node?.filterTree { it.isInteractive }) }
        } else {
            roots
        }

        return snapshot.copy(roots = pruned).toMcpJson().toString()
    }
}
