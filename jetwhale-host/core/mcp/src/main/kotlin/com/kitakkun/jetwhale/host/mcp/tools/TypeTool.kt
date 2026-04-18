package com.kitakkun.jetwhale.host.mcp.tools

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
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
                properties = JsonObject(
                    mapOf(
                        "pluginId" to stringProperty("The plugin ID."),
                        "sessionId" to stringProperty("The session ID."),
                        "text" to stringProperty("Printable characters to type. Mutually exclusive with specialKey."),
                        "specialKey" to stringProperty(
                            "Name of a special key to press (e.g. ENTER, BACKSPACE). Mutually exclusive with text.",
                        ),
                    ),
                ),
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
                text != null -> {
                    val success = dispatchTyping(scene, text)
                    if (!success) return@addTool errorResult("No editable text field found in the scene")
                }

                specialKey != null -> {
                    val key = specialKeyToComposeKey(specialKey)
                        ?: return@addTool errorResult("Unknown special key: $specialKey")
                    dispatchSpecialKey(scene, key)
                }

                else -> return@addTool errorResult("Either 'text' or 'specialKey' must be provided")
            }
            CallToolResult(content = listOf(TextContent("""{"success":true}""")))
        }
    }
}

/**
 * Inserts [text] into the first editable node in the scene via [SemanticsActions.InsertTextAtCursor].
 *
 * Using a semantics action avoids the need to simulate low-level key events and works
 * reliably in headless ComposeScenes where the platform event loop is not running.
 *
 * @return true if an editable node was found and the text was inserted, false otherwise.
 */
fun dispatchTyping(scene: PluginComposeScene, text: String): Boolean {
    val rootNodes = scene.semanticsOwners.map { it.rootSemanticsNode }
    val target = rootNodes.firstNotNullOfOrNull { findInsertableNode(it) } ?: return false
    target.config.getOrNull(SemanticsActions.InsertTextAtCursor)?.action?.invoke(AnnotatedString(text))
    return true
}

private fun findInsertableNode(node: SemanticsNode): SemanticsNode? {
    for (child in node.children) {
        val result = findInsertableNode(child)
        if (result != null) return result
    }
    return if (node.config.getOrNull(SemanticsActions.InsertTextAtCursor) != null) node else null
}

/**
 * Dispatches a single special key (press + release) to a plugin's ComposeScene using the
 * Skiko-based [KeyEvent] factory.
 *
 * @param scene    The plugin ComposeScene to receive the events.
 * @param key      The Compose [Key] to dispatch.
 * @param isAltPressed   Whether the Alt modifier is held.
 * @param isCtrlPressed  Whether the Ctrl modifier is held.
 * @param isMetaPressed  Whether the Meta/Command modifier is held.
 * @param isShiftPressed Whether the Shift modifier is held.
 */
@OptIn(InternalComposeUiApi::class, ExperimentalComposeUiApi::class)
fun dispatchSpecialKey(
    scene: PluginComposeScene,
    key: Key,
    isAltPressed: Boolean = false,
    isCtrlPressed: Boolean = false,
    isMetaPressed: Boolean = false,
    isShiftPressed: Boolean = false,
) {
    scene.composeScene.sendKeyEvent(
        KeyEvent(key, KeyEventType.KeyDown, 0, isAltPressed, isCtrlPressed, isMetaPressed, isShiftPressed),
    )
    scene.composeScene.sendKeyEvent(
        KeyEvent(key, KeyEventType.KeyUp, 0, isAltPressed, isCtrlPressed, isMetaPressed, isShiftPressed),
    )
}

/**
 * Maps a human-readable special key name to the corresponding Compose [Key].
 * Used by the MCP tool to accept string-based key names from the AI agent.
 */
fun specialKeyToComposeKey(name: String): Key? = when (name.uppercase()) {
    "ENTER" -> Key.Enter
    "BACKSPACE" -> Key.Backspace
    "DELETE" -> Key.Delete
    "TAB" -> Key.Tab
    "ESCAPE" -> Key.Escape
    "UP" -> Key.DirectionUp
    "DOWN" -> Key.DirectionDown
    "LEFT" -> Key.DirectionLeft
    "RIGHT" -> Key.DirectionRight
    "HOME" -> Key.MoveHome
    "END" -> Key.MoveEnd
    "PAGE_UP" -> Key.PageUp
    "PAGE_DOWN" -> Key.PageDown
    else -> null
}
