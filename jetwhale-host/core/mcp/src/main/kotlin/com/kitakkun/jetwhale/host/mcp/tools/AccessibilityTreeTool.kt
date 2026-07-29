package com.kitakkun.jetwhale.host.mcp.tools

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.IntSize
import com.kitakkun.jetwhale.host.mcp.JetWhaleMcpTool
import com.kitakkun.jetwhale.host.mcp.McpToolRegistrar
import com.kitakkun.jetwhale.host.mcp.errorResult
import com.kitakkun.jetwhale.host.mcp.jsonContent
import com.kitakkun.jetwhale.host.mcp.stringProperty
import com.kitakkun.jetwhale.host.mcp.viewport.McpViewport
import com.kitakkun.jetwhale.host.mcp.viewport.applyViewport
import com.kitakkun.jetwhale.host.mcp.viewport.isValidForViewport
import com.kitakkun.jetwhale.host.model.PluginComposeScene
import com.kitakkun.jetwhale.host.model.PluginComposeSceneService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

@Inject
@ContributesIntoSet(AppScope::class)
class GetAccessibilityTreeMcpTool(
    private val pluginComposeSceneService: PluginComposeSceneService,
) : JetWhaleMcpTool {
    override fun register(registrar: McpToolRegistrar) {
        registrar.addTool(
            name = "jetwhale.getAccessibilityTree",
            description = "Returns the Compose semantics (accessibility) tree of a plugin's UI. " +
                "Use this to identify elements by role or label and obtain their pixel bounds for targeted clicks.",
            inputSchema = ToolSchema(
                properties = JsonObject(
                    mapOf(
                        "pluginId" to stringProperty("The plugin ID."),
                        "sessionId" to stringProperty("The session ID."),
                    ),
                ),
                required = listOf("pluginId", "sessionId"),
            ),
        ) { request ->
            val pluginId = request.arguments?.get("pluginId")?.jsonContent
                ?: return@addTool errorResult("Missing required argument: pluginId")
            val sessionId = request.arguments?.get("sessionId")?.jsonContent
                ?: return@addTool errorResult("Missing required argument: sessionId")

            val scene = pluginComposeSceneService.getOrCreatePluginScene(pluginId, sessionId)
            val json = withContext(Dispatchers.Main) { captureAccessibilityTree(scene) }
            CallToolResult(content = listOf(TextContent(json)))
        }
    }
}

/**
 * Captures the Compose semantics (accessibility) tree of a plugin's ComposeScene.
 *
 * Returns a JSON string representing the full node tree, including roles, labels,
 * bounds (in pixels), and interactivity flags. AI agents can use this to identify
 * elements by name/role and calculate precise click coordinates from [NodeInfo.bounds].
 *
 * Must be called on the UI thread (Dispatchers.Main): it applies the viewport, renders, and flips
 * the capture flag, all of which mutate the ComposeScene.
 *
 * The tree carries the same strings the screenshot shows, so it is captured under
 * [com.kitakkun.jetwhale.host.sdk.LocalIsMcpCapture] exactly like a screenshot is —
 * otherwise it would hand an agent the values a plugin redacts from captures.
 */
@OptIn(InternalComposeUiApi::class)
fun captureAccessibilityTree(scene: PluginComposeScene): String {
    // Render the scene to flush pending recompositions and sync the semantics tree.
    val currentSize = runCatching { scene.composeScene.size }.getOrNull()
    val size = currentSize?.takeIf { it.isValidForViewport() }
        ?: scene.windowInfoUpdater.currentIntSize.takeIf { it.isValidForViewport() }
        ?: IntSize(1280, 720)
    val viewport = McpViewport(size = size, density = scene.composeScene.density)
    applyViewport(scene, viewport)

    // Raised only for this off-screen render; being on the UI thread, no interactive frame can
    // observe the raised state.
    scene.isMcpCapture.value = true
    val nodes = try {
        // render() only flushes snapshot apply notifications at its end (before draw), so a write
        // made right before it is not yet observed by the scene's recomposer and the frame would be
        // drawn with the stale value. Flush explicitly here so the flag-flip recomposition is applied
        // within this render pass.
        Snapshot.sendApplyNotifications()
        scene.composeScene.render(Canvas(ImageBitmap(size.width, size.height)), System.nanoTime())
        // Read the semantics while the flag is still raised: lowering it first would leave the tree
        // one recomposition away from the values that were actually rendered.
        scene.semanticsOwners.map { it.rootSemanticsNode }.flatMap { traverseSemanticsTree(it) }
    } finally {
        scene.isMcpCapture.value = false
        // Flush the restore so the next interactive render observes capture=false immediately rather
        // than lagging a frame behind.
        Snapshot.sendApplyNotifications()
    }
    return Json.encodeToString(AccessibilityTreeResult(nodes))
}

private fun traverseSemanticsTree(node: SemanticsNode): List<NodeInfo> {
    val info = nodeToInfo(node)
    val children = node.children.flatMap { traverseSemanticsTree(it) }
    return listOf(info.copy(children = children))
}

private fun nodeToInfo(node: SemanticsNode): NodeInfo {
    val config = node.config
    val bounds = node.boundsInRoot

    val text = config.getOrNull(SemanticsProperties.Text)
        ?.joinToString(separator = " ") { it.text }
    val contentDescription = config.getOrNull(SemanticsProperties.ContentDescription)
        ?.joinToString(separator = " ")
    val role = config.getOrNull(SemanticsProperties.Role)?.toString()
    val isEnabled = config.getOrNull(SemanticsProperties.Disabled) == null
    val isClickable = config.getOrNull(SemanticsActions.OnClick) != null
    val isFocused = config.getOrNull(SemanticsProperties.Focused) == true
    val isSelected = config.getOrNull(SemanticsProperties.Selected) == true
    val isChecked = config.getOrNull(SemanticsProperties.ToggleableState)?.toString()
    val editableText = config.getOrNull(SemanticsProperties.EditableText)?.text
    val isEditable = editableText != null

    return NodeInfo(
        id = node.id,
        role = role,
        text = text ?: editableText,
        contentDescription = contentDescription,
        bounds = BoundsInfo(
            left = bounds.left,
            top = bounds.top,
            right = bounds.right,
            bottom = bounds.bottom,
        ),
        isClickable = isClickable,
        isEnabled = isEnabled,
        isFocused = isFocused,
        isSelected = isSelected,
        isChecked = isChecked,
        isEditable = isEditable,
        children = emptyList(),
    )
}

@Serializable
data class AccessibilityTreeResult(
    val nodes: List<NodeInfo>,
)

@Serializable
data class NodeInfo(
    val id: Int,
    val role: String? = null,
    val text: String? = null,
    val contentDescription: String? = null,
    val bounds: BoundsInfo,
    val isClickable: Boolean = false,
    val isEnabled: Boolean = true,
    val isFocused: Boolean = false,
    val isSelected: Boolean = false,
    val isChecked: String? = null,
    val isEditable: Boolean = false,
    val children: List<NodeInfo> = emptyList(),
)

@Serializable
data class BoundsInfo(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}
