package com.kitakkun.jetwhale.host.mcp.tools

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.KeyEvent
import com.kitakkun.jetwhale.host.mcp.JetWhaleMcpTool
import com.kitakkun.jetwhale.host.mcp.errorResult
import com.kitakkun.jetwhale.host.mcp.jsonContent
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
import kotlinx.serialization.json.JsonObject
import java.awt.Component
import java.awt.event.KeyEvent as AwtKeyEvent

@Inject
@ContributesIntoSet(AppScope::class)
class TypeMcpTool(
    private val pluginComposeSceneService: PluginComposeSceneService,
) : JetWhaleMcpTool {
    override fun register(server: Server) {
        server.addTool(
            name = "jetwhale.type",
            description = "Types text or dispatches a special key into a plugin's UI. " +
                "Use 'text' for printable characters. Use 'specialKey' for keys like ENTER, BACKSPACE, TAB, ESCAPE, " +
                "UP, DOWN, LEFT, RIGHT, HOME, END, PAGE_UP, PAGE_DOWN, DELETE.",
            inputSchema = ToolSchema(
                properties = JsonObject(mapOf(
                    "pluginId" to stringProperty("The plugin ID."),
                    "sessionId" to stringProperty("The session ID."),
                    "text" to stringProperty("Printable characters to type. Mutually exclusive with specialKey."),
                    "specialKey" to stringProperty(
                        "Name of a special key to press (e.g. ENTER, BACKSPACE). Mutually exclusive with text.",
                    ),
                )),
                required = listOf("pluginId", "sessionId"),
            ),
        ) { request ->
            val pluginId = request.arguments?.get("pluginId")?.jsonContent
                ?: return@addTool errorResult("Missing required argument: pluginId")
            val sessionId = request.arguments?.get("sessionId")?.jsonContent
                ?: return@addTool errorResult("Missing required argument: sessionId")
            val text = request.arguments?.get("text")?.jsonContent
            val specialKey = request.arguments?.get("specialKey")?.jsonContent

            val scene = pluginComposeSceneService.getOrCreatePluginScene(pluginId, sessionId)
            when {
                text != null -> dispatchTyping(scene, text)
                specialKey != null -> {
                    val keyCode = specialKeyCode(specialKey)
                        ?: return@addTool errorResult("Unknown special key: $specialKey")
                    dispatchSpecialKey(scene, keyCode)
                }
                else -> return@addTool errorResult("Either 'text' or 'specialKey' must be provided")
            }
            CallToolResult(content = listOf(TextContent("""{"success":true}""")))
        }
    }
}

/**
 * Dispatches keyboard input to a plugin's ComposeScene by sending KEY_TYPED events
 * for each character in [text].
 *
 * For printable characters this mirrors how the JVM/AWT keyboard model works: a
 * KEY_TYPED event carries the Unicode character directly without needing a key code.
 *
 * Special keys (Enter, Tab, Backspace, etc.) should be passed via [dispatchSpecialKey].
 *
 * @param scene The plugin ComposeScene to receive the events.
 * @param text  The string to type.
 */
@OptIn(InternalComposeUiApi::class)
fun dispatchTyping(
    scene: PluginComposeScene,
    text: String,
) {
    for (char in text) {
        val awtEvent = AwtKeyEvent(
            DUMMY_COMPONENT,
            AwtKeyEvent.KEY_TYPED,
            System.currentTimeMillis(),
            0,
            AwtKeyEvent.VK_UNDEFINED,
            char,
        )
        scene.composeScene.sendKeyEvent(KeyEvent(awtEvent))
    }
}

/**
 * Dispatches a single special key (press + release) to a plugin's ComposeScene.
 *
 * @param scene   The plugin ComposeScene to receive the events.
 * @param keyCode AWT virtual key code, e.g. [AwtKeyEvent.VK_ENTER], [AwtKeyEvent.VK_BACK_SPACE].
 * @param modifiers AWT modifier mask, e.g. [AwtKeyEvent.SHIFT_DOWN_MASK]. Defaults to 0.
 */
@OptIn(InternalComposeUiApi::class)
fun dispatchSpecialKey(
    scene: PluginComposeScene,
    keyCode: Int,
    modifiers: Int = 0,
) {
    val pressEvent = AwtKeyEvent(
        DUMMY_COMPONENT,
        AwtKeyEvent.KEY_PRESSED,
        System.currentTimeMillis(),
        modifiers,
        keyCode,
        AwtKeyEvent.CHAR_UNDEFINED,
    )
    val releaseEvent = AwtKeyEvent(
        DUMMY_COMPONENT,
        AwtKeyEvent.KEY_RELEASED,
        System.currentTimeMillis(),
        modifiers,
        keyCode,
        AwtKeyEvent.CHAR_UNDEFINED,
    )
    scene.composeScene.sendKeyEvent(KeyEvent(pressEvent))
    scene.composeScene.sendKeyEvent(KeyEvent(releaseEvent))
}

/**
 * Maps a human-readable special key name to the corresponding AWT virtual key code.
 * Used by the MCP tool to accept string-based key names from the AI agent.
 */
fun specialKeyCode(name: String): Int? = when (name.uppercase()) {
    "ENTER" -> AwtKeyEvent.VK_ENTER
    "BACKSPACE" -> AwtKeyEvent.VK_BACK_SPACE
    "DELETE" -> AwtKeyEvent.VK_DELETE
    "TAB" -> AwtKeyEvent.VK_TAB
    "ESCAPE" -> AwtKeyEvent.VK_ESCAPE
    "UP" -> AwtKeyEvent.VK_UP
    "DOWN" -> AwtKeyEvent.VK_DOWN
    "LEFT" -> AwtKeyEvent.VK_LEFT
    "RIGHT" -> AwtKeyEvent.VK_RIGHT
    "HOME" -> AwtKeyEvent.VK_HOME
    "END" -> AwtKeyEvent.VK_END
    "PAGE_UP" -> AwtKeyEvent.VK_PAGE_UP
    "PAGE_DOWN" -> AwtKeyEvent.VK_PAGE_DOWN
    else -> null
}

// AWT requires a non-null Component source; Compose ignores the nativeEvent source for input
private val DUMMY_COMPONENT: Component by lazy {
    object : Component() {}
}
