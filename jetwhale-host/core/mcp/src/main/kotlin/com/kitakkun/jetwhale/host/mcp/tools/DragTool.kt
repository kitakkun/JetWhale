package com.kitakkun.jetwhale.host.mcp.tools

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import com.kitakkun.jetwhale.host.mcp.JetWhaleMcpTool
import com.kitakkun.jetwhale.host.mcp.errorResult
import com.kitakkun.jetwhale.host.mcp.jsonContent
import com.kitakkun.jetwhale.host.mcp.successResult
import com.kitakkun.jetwhale.host.mcp.jsonFloat
import com.kitakkun.jetwhale.host.mcp.numberProperty
import com.kitakkun.jetwhale.host.mcp.stringProperty
import com.kitakkun.jetwhale.host.model.PluginComposeScene
import com.kitakkun.jetwhale.host.model.PluginComposeSceneService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.serialization.json.JsonObject

@Inject
@ContributesIntoSet(AppScope::class)
class DragMcpTool(
    private val pluginComposeSceneService: PluginComposeSceneService,
) : JetWhaleMcpTool {
    override fun register(server: Server) {
        server.addTool(
            name = "jetwhale.drag",
            description = "Simulates a drag gesture in a plugin's UI by dispatching press, move, and release pointer events. " +
                "Intended for drag-and-drop style interactions, not scrolling — use jetwhale.scroll to scroll lists. " +
                "Use jetwhale.getAccessibilityTree to obtain element bounds.",
            inputSchema = ToolSchema(
                properties = JsonObject(
                    mapOf(
                        "pluginId" to stringProperty("The plugin ID."),
                        "sessionId" to stringProperty("The session ID."),
                        "startX" to numberProperty("X coordinate in pixels where the drag starts."),
                        "startY" to numberProperty("Y coordinate in pixels where the drag starts."),
                        "endX" to numberProperty("X coordinate in pixels where the drag ends."),
                        "endY" to numberProperty("Y coordinate in pixels where the drag ends."),
                        "steps" to numberProperty("Number of intermediate move events between start and end (default: 10)."),
                    ),
                ),
                required = listOf("pluginId", "sessionId", "startX", "startY", "endX", "endY"),
            ),
        ) { request ->
            val pluginId = request.arguments?.get("pluginId")?.jsonContent
                ?: return@addTool errorResult("Missing required argument: pluginId")
            val sessionId = request.arguments?.get("sessionId")?.jsonContent
                ?: return@addTool errorResult("Missing required argument: sessionId")
            val startX = request.arguments?.get("startX")?.jsonFloat
                ?: return@addTool errorResult("Missing required argument: startX")
            val startY = request.arguments?.get("startY")?.jsonFloat
                ?: return@addTool errorResult("Missing required argument: startY")
            val endX = request.arguments?.get("endX")?.jsonFloat
                ?: return@addTool errorResult("Missing required argument: endX")
            val endY = request.arguments?.get("endY")?.jsonFloat
                ?: return@addTool errorResult("Missing required argument: endY")
            val steps = request.arguments?.get("steps")?.jsonFloat?.toInt() ?: 10

            val scene = pluginComposeSceneService.getOrCreatePluginScene(pluginId, sessionId)
            withContext(Dispatchers.Main) { dispatchDrag(scene, startX, startY, endX, endY, steps) }
            successResult()
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
suspend fun dispatchDrag(
    scene: PluginComposeScene,
    startX: Float,
    startY: Float,
    endX: Float,
    endY: Float,
    steps: Int,
) {
    scene.composeScene.sendPointerEvent(
        eventType = PointerEventType.Press,
        position = Offset(startX, startY),
    )
    yield()

    val stepCount = steps.coerceAtLeast(1)
    for (i in 1..stepCount) {
        val fraction = i.toFloat() / stepCount
        val x = startX + (endX - startX) * fraction
        val y = startY + (endY - startY) * fraction
        scene.composeScene.sendPointerEvent(
            eventType = PointerEventType.Move,
            position = Offset(x, y),
        )
        yield()
    }

    scene.composeScene.sendPointerEvent(
        eventType = PointerEventType.Release,
        position = Offset(endX, endY),
    )
    yield()
}
