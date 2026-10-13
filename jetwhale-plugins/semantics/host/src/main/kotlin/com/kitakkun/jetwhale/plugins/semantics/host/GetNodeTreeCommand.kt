package com.kitakkun.jetwhale.plugins.semantics.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.semantics.protocol.ComposeRoot
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeCaptureOptions
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeSnapshot
import com.kitakkun.jetwhale.plugins.semantics.protocol.UiNode
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingException

@OptIn(ExperimentalJetWhaleApi::class)
internal class GetNodeTreeCommand(
    private val capture: suspend (NodeTreeCaptureOptions) -> NodeTreeSnapshot,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.getNodeTree"
    override val description =
        "Captures the running app's UI node tree right now — Compose semantics, plus Android Views on Android, " +
            "and on iOS everything the accessibility tree carries (UIKit, SwiftUI and Compose) — and returns it as JSON: " +
            "{\"capturedAtMs\", \"captureDurationMs\", \"merged\", \"roots\": [{\"rootId\", \"label\", \"density\", \"node\"}]}. " +
            "Each node carries id, text, contentDescription, \"actions\" — the action names performNodeAction accepts for it (BringIntoView works on any node and is not listed) — screen \"bounds\" in the node's \"unit\" " +
            "(px, or pt on iOS), a \"tap\" point (the centre of the bounds), and per kind a role and testTag (Compose), " +
            "a viewClass and resourceId (Android View), or a className and accessibilityIdentifier (iOS). " +
            "A root is a window: on Android a dialog or popup is a window of its own, on iOS it stays inside the app's. " +
            "Prefer findNodes when you are looking for a specific element, and performNodeAction over tapping coordinates."

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
    private val format by serializableOrNull<NodeOutputFormat>(
        "\"json\" (default) returns the JSON described above. \"text\" returns the same nodes as a compact outline for reading rather than parsing — " +
            "one line per node, indented by depth: `- <role or class> #<id> \"<text>\" key=value… [flags] actions=… tap=x,y`, " +
            "with each root on a `root <rootId> \"<label>\" unit=… density=…` line. It leaves out the bounds and takes a fraction of the tokens.",
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
                "unknown rootId: $requestedRootId (known roots: ${snapshot.roots.joinToString(transform = ComposeRoot::rootId)})",
            )
        }

        val pruned = if (arguments[interactiveOnly] == true) {
            roots.map { root -> root.copy(node = root.node?.filterTree(UiNode::isInteractive)) }
        } else {
            roots
        }

        val result = snapshot.copy(roots = pruned)
        return when (arguments[format] ?: NodeOutputFormat.Json) {
            NodeOutputFormat.Json -> result.toMcpJson().toString()
            NodeOutputFormat.Text -> result.toMcpText()
        }
    }
}
