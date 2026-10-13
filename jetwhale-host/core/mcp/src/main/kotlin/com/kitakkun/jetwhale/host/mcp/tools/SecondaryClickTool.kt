package com.kitakkun.jetwhale.host.mcp.tools

import androidx.annotation.VisibleForTesting
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.scene.PointerEventResult
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import com.kitakkun.jetwhale.host.mcp.JetWhaleMcpTool
import com.kitakkun.jetwhale.host.mcp.McpToolRegistrar
import com.kitakkun.jetwhale.host.mcp.errorResult
import com.kitakkun.jetwhale.host.mcp.jsonContent
import com.kitakkun.jetwhale.host.mcp.jsonFloat
import com.kitakkun.jetwhale.host.mcp.numberProperty
import com.kitakkun.jetwhale.host.mcp.stringProperty
import com.kitakkun.jetwhale.host.mcp.viewport.ensureSceneRendered
import com.kitakkun.jetwhale.host.mcp.viewport.renderDiscardingPixels
import com.kitakkun.jetwhale.host.model.McpPermissionsRepository
import com.kitakkun.jetwhale.host.model.McpToolPermission
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
class SecondaryClickMcpTool(
    private val pluginComposeSceneService: PluginComposeSceneService,
    private val mcpPermissionsRepository: McpPermissionsRepository,
) : JetWhaleMcpTool {
    /** Leaves out the nodes an agent may not see instead of writing them as null. */
    private val secondaryClickOutcomeJson = Json { explicitNulls = false }

    override fun register(registrar: McpToolRegistrar) {
        registrar.addTool(
            name = "jetwhale.secondaryClick",
            description = "Sends a secondary-button (right) mouse click at the given pixel coordinates in a plugin's UI, " +
                "as a real press and release, so that context menus open. Unlike jetwhale.click, which invokes the " +
                "clickable element's action, this is a pointer event. The result says whether a handler consumed it " +
                "and whether a popup opened or closed, and lists the clickable nodes of every popup still open, such " +
                "as a menu's items, with their bounds, in the shape jetwhale.getAccessibilityTree uses, so pick one " +
                "with jetwhale.click. The nodes are listed only when the plugin's UI may also be inspected, as for " +
                "jetwhale.getAccessibilityTree. A secondary click outside an open popup closes it.",
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
            permission = McpToolPermission.PluginInteract,
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
            val outcome = withContext(Dispatchers.Main) {
                ensureSceneRendered(scene)
                dispatchSecondaryClick(scene, x, y)
            }
            if (!outcome.consumed && !outcome.openedPopup && !outcome.closedPopup && outcome.popupClickableNodes.isNullOrEmpty()) {
                return@addTool errorResult(
                    "Nothing at ($x, $y) consumed the secondary click, and no popup opened or closed. " +
                        "A handler that reacts without consuming the event is not detected; check with jetwhale.screenshot.",
                )
            }
            encodeOutcomeWithinInspectPermission(outcome, pluginId)
        }
    }

    /**
     * [outcome] as a tool result. The popup's nodes carry what `jetwhale.getAccessibilityTree` shows, so
     * they are left out, with a note saying why, unless the agent may also inspect [pluginId]'s UI.
     */
    private fun encodeOutcomeWithinInspectPermission(outcome: SecondaryClickOutcome, pluginId: String): CallToolResult {
        if (mcpPermissionsRepository.permissionsFlow.value.allows(McpToolPermission.PluginInspect, pluginId)) {
            return CallToolResult(content = listOf(TextContent(secondaryClickOutcomeJson.encodeToString(outcome))))
        }
        return CallToolResult(
            content = listOf(
                TextContent(secondaryClickOutcomeJson.encodeToString(outcome.copy(popupClickableNodes = null))),
                TextContent(
                    "popupClickableNodes is left out: reading the UI of '$pluginId' is not exposed to AI agents. " +
                        "Review it in Settings → AI Agents → Permissions.",
                ),
            ),
        )
    }
}

/**
 * What a secondary click changed in a scene.
 *
 * @property consumed whether a pointer input handler consumed the press or the release, as Compose
 * reports it. A handler that reacts without consuming reads false.
 * @property openedPopup whether a popup or dialog opened, which is how a context menu shows.
 * @property closedPopup whether a popup or dialog that was open closed. A press outside an open
 * popup only dismisses it; nothing beneath the popup receives that press.
 * @property popupClickableNodes the clickable nodes of every popup or dialog open after the click, the topmost
 * one's first and each one's top to bottom; null when the agent may not inspect the plugin's UI.
 */
@Serializable
@VisibleForTesting
internal data class SecondaryClickOutcome(
    val consumed: Boolean,
    val openedPopup: Boolean,
    val closedPopup: Boolean,
    val popupClickableNodes: List<NodeInfo>?,
)

/**
 * Presses and releases the secondary mouse button at (x, y) through the scene's pointer input, as
 * the window would for a real right-click, so `ContextMenuArea` and secondary-button pointer
 * handlers react to it.
 *
 * Must be called on the UI thread (Dispatchers.Main). The popup a press opens is composed only by the
 * next frame, so this renders one, under the MCP capture flag because the items it reports carry the
 * same strings an accessibility capture does.
 */
@OptIn(InternalComposeUiApi::class)
@VisibleForTesting
internal fun dispatchSecondaryClick(scene: PluginComposeScene, x: Float, y: Float): SecondaryClickOutcome {
    val ownersBeforeClick = scene.semanticsOwners.toSet()
    val position = Offset(x, y)
    val pressResult = scene.composeScene.sendPointerEvent(
        eventType = PointerEventType.Press,
        position = position,
        buttons = PointerButtons(isSecondaryPressed = true),
        button = PointerButton.Secondary,
    )
    val releaseResult = scene.composeScene.sendPointerEvent(
        eventType = PointerEventType.Release,
        position = position,
        buttons = PointerButtons(),
        button = PointerButton.Secondary,
    )
    return scene.whileCapturingForMcp {
        scene.renderDiscardingPixels()
        val ownersAfterClick = scene.semanticsOwners.toList()
        // The first owner is the scene's own content; each Popup or Dialog appends its own as it
        // opens, so the last one is on top.
        val popupOwners = ownersAfterClick.drop(1).reversed()
        SecondaryClickOutcome(
            consumed = pressResult in RESULTS_WITH_A_CONSUMED_CHANGE || releaseResult in RESULTS_WITH_A_CONSUMED_CHANGE,
            openedPopup = ownersAfterClick.any { it !in ownersBeforeClick },
            closedPopup = ownersBeforeClick.any { it !in ownersAfterClick },
            popupClickableNodes = popupOwners.flatMap { clickableNodes(it.rootSemanticsNode) }.map(SemanticsNode::toNodeInfo),
        )
    }
}

/**
 * Compose keeps [PointerEventResult]'s flags internal, so a result is read by comparing it with every
 * value its public constructor builds with `anyChangeConsumed` set.
 */
@OptIn(InternalComposeUiApi::class)
private val RESULTS_WITH_A_CONSUMED_CHANGE: Set<PointerEventResult> = buildSet {
    for (dispatchedToAPointerInputModifier in listOf(false, true)) {
        for (anyMovementConsumed in listOf(false, true)) {
            add(
                PointerEventResult(
                    anyMovementConsumed = anyMovementConsumed,
                    anyChangeConsumed = true,
                    dispatchedToAPointerInputModifier = dispatchedToAPointerInputModifier,
                ),
            )
        }
    }
}

private fun clickableNodes(node: SemanticsNode): List<SemanticsNode> = buildList {
    if (node.config.getOrNull(SemanticsActions.OnClick) != null) add(node)
    node.children.forEach { addAll(clickableNodes(it)) }
}
