package com.kitakkun.jetwhale.host.mcp.tools

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.unit.IntSize
import com.kitakkun.jetwhale.host.mcp.JetWhaleMcpTool
import com.kitakkun.jetwhale.host.mcp.errorResult
import com.kitakkun.jetwhale.host.mcp.jsonContent
import com.kitakkun.jetwhale.host.mcp.jsonInt
import com.kitakkun.jetwhale.host.mcp.numberProperty
import com.kitakkun.jetwhale.host.mcp.stringProperty
import com.kitakkun.jetwhale.host.mcp.viewport.McpViewport
import com.kitakkun.jetwhale.host.mcp.viewport.applyViewport
import com.kitakkun.jetwhale.host.mcp.viewport.isValidForViewport
import com.kitakkun.jetwhale.host.model.PluginComposeScene
import com.kitakkun.jetwhale.host.model.PluginComposeSceneService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import org.jetbrains.skia.EncodedImageFormat
import java.util.Base64
import org.jetbrains.skia.Image as SkiaImage

@Inject
@ContributesIntoSet(AppScope::class)
class ScreenshotMcpTool(
    private val pluginComposeSceneService: PluginComposeSceneService,
) : JetWhaleMcpTool {
    override fun register(server: Server) {
        server.addTool(
            name = "jetwhale.screenshot",
            description = "Captures the current rendered frame of a plugin's Compose UI as a PNG image.",
            inputSchema = ToolSchema(
                properties = JsonObject(mapOf(
                    "pluginId" to stringProperty("The plugin ID."),
                    "sessionId" to stringProperty("The session ID."),
                    "width" to numberProperty("Image width in pixels. Defaults to the current UI width (fallback: 1280)."),
                    "height" to numberProperty("Image height in pixels. Defaults to the current UI height (fallback: 720)."),
                )),
                required = listOf("pluginId", "sessionId"),
            ),
        ) { request ->
            val pluginId = request.arguments?.get("pluginId")?.jsonContent
                ?: return@addTool errorResult("Missing required argument: pluginId")
            val sessionId = request.arguments?.get("sessionId")?.jsonContent
                ?: return@addTool errorResult("Missing required argument: sessionId")
            val requestedWidth = request.arguments?.get("width")?.jsonInt
            val requestedHeight = request.arguments?.get("height")?.jsonInt
            if ((requestedWidth == null) != (requestedHeight == null)) {
                return@addTool errorResult("Both 'width' and 'height' must be provided together.")
            }

            val scene = pluginComposeSceneService.getOrCreatePluginScene(pluginId, sessionId)
            val (viewport, pngBytes) = withContext(Dispatchers.Main) {
                val viewport = resolveViewport(scene, requestedWidth, requestedHeight)
                viewport to captureScreenshot(scene, viewport)
            }
            val base64 = Base64.getEncoder().encodeToString(pngBytes)
            CallToolResult(content = listOf(ImageContent(data = base64, mimeType = "image/png")))
        }
    }
}

/**
 * Renders the plugin's ComposeScene to an in-memory PNG image.
 *
 * Uses a CPU-only Skia surface (no GPU context required), safe to call from any coroutine.
 *
 * @param scene  The plugin ComposeScene to render.
 * @param viewport Output viewport (size + density) to render with.
 * @return PNG-encoded bytes.
 */
@OptIn(InternalComposeUiApi::class)
fun captureScreenshot(
    scene: PluginComposeScene,
    viewport: McpViewport,
): ByteArray {
    applyViewport(scene, viewport)

    val imageBitmap = ImageBitmap(viewport.size.width, viewport.size.height)
    val composeCanvas = Canvas(imageBitmap)
    scene.composeScene.render(composeCanvas, System.nanoTime())

    return SkiaImage.makeFromBitmap(imageBitmap.asSkiaBitmap())
        .encodeToData(EncodedImageFormat.PNG)
        ?.bytes
        ?: error("Failed to encode screenshot to PNG")
}

@OptIn(InternalComposeUiApi::class)
private fun resolveViewport(
    scene: PluginComposeScene,
    requestedWidth: Int?,
    requestedHeight: Int?,
): McpViewport {
    val density = scene.composeScene.density
    val requested = if (requestedWidth != null && requestedHeight != null) {
        IntSize(requestedWidth, requestedHeight)
    } else {
        null
    }
    val currentSize = runCatching { scene.composeScene.size }.getOrNull()
    val size =
        requested
            ?: currentSize?.takeIf { it.isValidForViewport() }
            ?: scene.windowInfoUpdater.currentIntSize.takeIf { it.isValidForViewport() }
            ?: IntSize(1280, 720)
    return McpViewport(size = size, density = density)
}
