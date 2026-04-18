package com.kitakkun.jetwhale.host.mcp.tools

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
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
class ScrollMcpTool(
    private val pluginComposeSceneService: PluginComposeSceneService,
) : JetWhaleMcpTool {
    override fun register(server: Server) {
        server.addTool(
            name = "jetwhale.scroll",
            description = "Dispatches a scroll event at the given pixel coordinates in a plugin's UI. " +
                "Positive deltaY scrolls down, negative scrolls up. " +
                "Positive deltaX scrolls right, negative scrolls left.",
            inputSchema = ToolSchema(
                properties = JsonObject(
                    mapOf(
                        "pluginId" to stringProperty("The plugin ID."),
                        "sessionId" to stringProperty("The session ID."),
                        "x" to numberProperty("X coordinate in pixels from the left edge of the plugin UI."),
                        "y" to numberProperty("Y coordinate in pixels from the top edge of the plugin UI."),
                        "deltaX" to numberProperty("Horizontal scroll delta in pixels. Positive scrolls right, negative scrolls left."),
                        "deltaY" to numberProperty("Vertical scroll delta in pixels. Positive scrolls down, negative scrolls up."),
                    ),
                ),
                required = listOf("pluginId", "sessionId", "x", "y", "deltaX", "deltaY"),
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
            val deltaX = request.arguments?.get("deltaX")?.jsonFloat
                ?: return@addTool errorResult("Missing required argument: deltaX")
            val deltaY = request.arguments?.get("deltaY")?.jsonFloat
                ?: return@addTool errorResult("Missing required argument: deltaY")

            val scene = pluginComposeSceneService.getOrCreatePluginScene(pluginId, sessionId)
            withContext(Dispatchers.Main) { dispatchScroll(scene, x, y, deltaX, deltaY) }
            CallToolResult(content = listOf(TextContent("""{"success":true}""")))
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
suspend fun dispatchScroll(scene: PluginComposeScene, x: Float, y: Float, deltaX: Float, deltaY: Float) {
    scene.composeScene.sendPointerEvent(
        eventType = PointerEventType.Scroll,
        position = Offset(x, y),
        scrollDelta = Offset(deltaX, deltaY),
    )
}
