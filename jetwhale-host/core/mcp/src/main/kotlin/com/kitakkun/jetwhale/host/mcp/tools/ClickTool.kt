package com.kitakkun.jetwhale.host.mcp.tools

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import com.kitakkun.jetwhale.host.mcp.JetWhaleMcpTool
import com.kitakkun.jetwhale.host.mcp.errorResult
import com.kitakkun.jetwhale.host.mcp.jsonContent
import com.kitakkun.jetwhale.host.mcp.jsonFloat
import com.kitakkun.jetwhale.host.mcp.numberProperty
import com.kitakkun.jetwhale.host.mcp.stringProperty
import com.kitakkun.jetwhale.host.model.PluginComposeScene
import com.kitakkun.jetwhale.host.model.PluginComposeSceneService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

@Inject
@ContributesIntoSet(AppScope::class)
class ClickMcpTool(
    private val pluginComposeSceneService: PluginComposeSceneService,
) : JetWhaleMcpTool {
    override fun register(server: Server) {
        server.addTool(
            name = "jetwhale.click",
            description = "Dispatches a mouse click at the given pixel coordinates in a plugin's UI. " +
                "Use jetwhale.getAccessibilityTree to obtain element bounds.",
            inputSchema = ToolSchema(
                properties = JsonObject(
                    mapOf(
                        "pluginId" to stringProperty("The plugin ID."),
                        "sessionId" to stringProperty("The session ID."),
                        "x" to numberProperty("X coordinate in pixels from the left edge of the plugin UI."),
                        "y" to numberProperty("Y coordinate in pixels from the top edge of the plugin UI."),
                    ),
                ),
                required = listOf("pluginId", "sessionId", "x", "y"),
            ),
        ) { request ->
            val pluginId = request.arguments?.get("pluginId")?.jsonContent
                ?: return@addTool errorResult("Missing required argument: pluginId")
            val sessionId = request.arguments?.get("sessionId")?.jsonContent
                ?: return@addTool errorResult("Missing required argument: sessionId")
            val x = request.arguments?.get("x")?.jsonFloat
                ?: return@addTool errorResult("Missing required argument: x")
            val y = request.arguments?.get("y")?.jsonFloat
                ?: return@addTool errorResult("Missing required argument: y")

            val scene = pluginComposeSceneService.getOrCreatePluginScene(pluginId, sessionId)
            val clicked = withContext(Dispatchers.Main) { dispatchClick(scene, x, y) }
            if (clicked) {
                CallToolResult(content = listOf(TextContent("""{"success":true}""")))
            } else {
                errorResult("No clickable element found at ($x, $y)")
            }
        }
    }
}

/**
 * Invokes the OnClick semantics action of the deepest clickable node that contains (x, y).
 *
 * Using the semantics action is more reliable than pointer event simulation for headless
 * ComposeScenes, where gesture-detection coroutines may not be driven by a platform event loop.
 *
 * @return true if a clickable node was found and its action was invoked, false otherwise.
 */
fun dispatchClick(scene: PluginComposeScene, x: Float, y: Float): Boolean {
    val point = Offset(x, y)
    val rootNodes = scene.semanticsOwners.map { it.rootSemanticsNode }
    val target = rootNodes.firstNotNullOfOrNull { findClickableNodeAt(it, point) }
        ?: return false
    target.config.getOrNull(SemanticsActions.OnClick)?.action?.invoke()
    return true
}

private fun findClickableNodeAt(node: SemanticsNode, point: Offset): SemanticsNode? {
    if (!node.boundsInRoot.contains(point)) return null
    // Prefer the deepest (most specific) clickable child.
    for (child in node.children) {
        val result = findClickableNodeAt(child, point)
        if (result != null) return result
    }
    return if (node.config.getOrNull(SemanticsActions.OnClick) != null) node else null
}
