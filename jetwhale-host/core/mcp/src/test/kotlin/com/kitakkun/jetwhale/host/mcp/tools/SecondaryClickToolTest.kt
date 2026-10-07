package com.kitakkun.jetwhale.host.mcp.tools

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.mcp.FakeMcpActivityRepository
import com.kitakkun.jetwhale.host.mcp.FakeMcpPermissionsRepository
import com.kitakkun.jetwhale.host.mcp.McpToolRegistrar
import com.kitakkun.jetwhale.host.mcp.viewport.McpViewport
import com.kitakkun.jetwhale.host.mcp.viewport.ensureSceneRendered
import com.kitakkun.jetwhale.host.mcp.viewport.renderDiscardingPixels
import com.kitakkun.jetwhale.host.model.McpPermissions
import com.kitakkun.jetwhale.host.model.PluginComposeScene
import com.kitakkun.jetwhale.host.model.PluginComposeSceneService
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import io.modelcontextprotocol.kotlin.sdk.server.ClientConnection
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(InternalComposeUiApi::class, ExperimentalComposeUiApi::class)
class SecondaryClickToolTest {

    private var copyUrlActionRan = false

    @Test
    fun `a secondary click on a ContextMenuArea opens its menu and lists the items`(): Unit = runBlocking(Dispatchers.Main) {
        val scene = contextMenuScene()

        val outcome = dispatchSecondaryClick(scene, 100f, 100f)

        assertTrue(outcome.consumed, "ContextMenuArea consumes the press that opens it")
        assertTrue(outcome.openedPopup)
        assertEquals(listOf("Copy URL", "Copy as cURL"), outcome.popupClickableNodes?.map(NodeInfo::text))
        assertEquals(true, outcome.popupClickableNodes?.all(NodeInfo::isClickable))
    }

    @Test
    fun `the press and release reach a pointer handler as the secondary button`(): Unit = runBlocking(Dispatchers.Main) {
        val receivedEvents = mutableListOf<PointerEvent>()
        val scene = createTestScene {
            Box(
                Modifier.size(200.dp).pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Press || event.type == PointerEventType.Release) receivedEvents += event
                        }
                    }
                },
            )
        }
        renderTestScene(scene)

        dispatchSecondaryClick(scene, 100f, 100f)

        assertEquals(listOf(PointerEventType.Press, PointerEventType.Release), receivedEvents.map(PointerEvent::type))
        assertEquals(listOf(PointerButton.Secondary, PointerButton.Secondary), receivedEvents.map(PointerEvent::button))
        val press = receivedEvents.first()
        assertTrue(press.buttons.isSecondaryPressed, "The press should hold the secondary button")
        assertFalse(press.buttons.isPrimaryPressed, "The press should not hold the primary button")
        assertFalse(receivedEvents.last().buttons.isSecondaryPressed, "The release should let go of the secondary button")
    }

    @Test
    fun `the opened menu's items are in the accessibility tree`(): Unit = runBlocking(Dispatchers.Main) {
        val scene = contextMenuScene()
        dispatchSecondaryClick(scene, 100f, 100f)

        val nodes = Json.decodeFromString<AccessibilityTreeResult>(captureAccessibilityTree(scene)).nodes.flatMap(::selfAndDescendants)

        val copyUrlNode = nodes.singleOrNull { it.text == "Copy URL" }
        assertNotNull(copyUrlNode, "Expected the menu item in the accessibility tree")
        assertTrue(copyUrlNode.isClickable)
    }

    @Test
    fun `the opened menu is in the screenshot`(): Unit = runBlocking(Dispatchers.Main) {
        val scene = contextMenuScene()
        val viewport = McpViewport(size = IntSize(TEST_SCENE_WIDTH, TEST_SCENE_HEIGHT), density = Density(1f))
        dispatchSecondaryClick(scene, 100f, 100f)

        // Compose opens a context menu with its top-left corner at the pointer.
        val pixel = renderScreenshot(scene, viewport).toPixelMap()[108, 108]

        assertTrue(pixel.red > 0.8f, "Expected the light menu over the black content, but the pixel was $pixel")
    }

    @Test
    fun `clicking a menu item runs its action and closes the menu`(): Unit = runBlocking(Dispatchers.Main) {
        val scene = contextMenuScene()
        val copyUrlBounds = checkNotNull(dispatchSecondaryClick(scene, 100f, 100f).popupClickableNodes).single { it.text == "Copy URL" }.bounds

        ensureSceneRendered(scene)
        val clicked = dispatchClick(scene, copyUrlBounds.centerX, copyUrlBounds.centerY)
        scene.renderDiscardingPixels()

        assertTrue(clicked)
        assertTrue(copyUrlActionRan, "Expected the menu item's action to run")
        assertEquals(1, scene.semanticsOwners.size, "Expected the menu to close after its item was picked")
    }

    @Test
    fun `a secondary click on an empty area reports that nothing took it`(): Unit = runBlocking(Dispatchers.Main) {
        val scene = contextMenuScene()

        val outcome = dispatchSecondaryClick(scene, 600f, 400f)

        assertEquals(SecondaryClickOutcome(consumed = false, openedPopup = false, closedPopup = false, popupClickableNodes = emptyList()), outcome)
    }

    @Test
    fun `a secondary click inside an open menu lists the items of the menu still open`(): Unit = runBlocking(Dispatchers.Main) {
        val scene = contextMenuScene()
        val copyUrlBounds = checkNotNull(dispatchSecondaryClick(scene, 100f, 100f).popupClickableNodes).single { it.text == "Copy URL" }.bounds

        val outcome = dispatchSecondaryClick(scene, copyUrlBounds.centerX, copyUrlBounds.centerY)

        assertFalse(outcome.openedPopup)
        assertFalse(outcome.closedPopup)
        assertEquals(listOf("Copy URL", "Copy as cURL"), outcome.popupClickableNodes?.map(NodeInfo::text))
    }

    @Test
    fun `a secondary click outside an open menu reports that it closed the menu`(): Unit = runBlocking(Dispatchers.Main) {
        val scene = contextMenuScene()
        dispatchSecondaryClick(scene, 100f, 100f)

        val outcome = dispatchSecondaryClick(scene, 600f, 400f)

        assertTrue(outcome.closedPopup)
        assertFalse(outcome.openedPopup)
        assertEquals(1, scene.semanticsOwners.size)
    }

    @Test
    fun `secondaryClick answers with the opened menu's items`(): Unit = runBlocking(Dispatchers.Main) {
        val result = callSecondaryClickTool(contextMenuScene(), x = 100, y = 100, permissions = McpPermissions.AllowAll)

        assertFalse(result.isError == true, "Expected a successful result, but was $result")
        val outcome = Json.decodeFromString<SecondaryClickOutcome>((result.content.single() as TextContent).text)
        assertEquals(listOf("Copy URL", "Copy as cURL"), outcome.popupClickableNodes?.map(NodeInfo::text))
    }

    @Test
    fun `secondaryClick answers with an error when nothing at the point takes the click`(): Unit = runBlocking(Dispatchers.Main) {
        val result = callSecondaryClickTool(contextMenuScene(), x = 600, y = 400, permissions = McpPermissions.AllowAll)

        assertEquals(true, result.isError)
        assertContains((result.content.single() as TextContent).text, "Nothing at (600.0, 400.0) consumed the secondary click")
    }

    @Test
    fun `secondaryClick inside an open menu answers with the menu's items rather than an error`(): Unit = runBlocking(Dispatchers.Main) {
        val scene = contextMenuScene()
        callSecondaryClickTool(scene, x = 100, y = 100, permissions = McpPermissions.AllowAll)

        val result = callSecondaryClickTool(scene, x = 120, y = 120, permissions = McpPermissions.AllowAll)

        assertFalse(result.isError == true, "Expected a successful result, but was $result")
        val outcome = Json.decodeFromString<SecondaryClickOutcome>((result.content.single() as TextContent).text)
        assertEquals(listOf("Copy URL", "Copy as cURL"), outcome.popupClickableNodes?.map(NodeInfo::text))
    }

    @Test
    fun `secondaryClick leaves out the popup's nodes when the plugin's UI may not be inspected`(): Unit = runBlocking(Dispatchers.Main) {
        val interactOnly = McpPermissions.AllowAll.copy(pluginsDeniedInspect = setOf("plugin"))

        val result = callSecondaryClickTool(contextMenuScene(), x = 100, y = 100, permissions = interactOnly)

        assertFalse(result.isError == true, "Expected a successful result, but was $result")
        val texts = result.content.map { (it as TextContent).text }
        val outcome = Json.parseToJsonElement(texts.first()).jsonObject
        assertEquals(setOf("consumed", "openedPopup", "closedPopup"), outcome.keys)
        assertEquals(true, outcome.getValue("openedPopup").jsonPrimitive.boolean)
        assertTrue(texts.none { "Copy URL" in it }, "Expected no node data, but the result was $texts")
        assertContains(texts.last(), "popupClickableNodes is left out")
    }

    /** A rendered scene whose top-left 200×200 black box is clickable, like a table row, and opens a two-item context menu. */
    private fun contextMenuScene(): PluginComposeScene {
        val scene = createTestScene {
            ContextMenuArea(items = { listOf(ContextMenuItem("Copy URL") { copyUrlActionRan = true }, ContextMenuItem("Copy as cURL") {}) }) {
                Box(Modifier.size(200.dp).background(Color.Black).clickable {})
            }
        }
        renderTestScene(scene)
        return scene
    }

    private suspend fun callSecondaryClickTool(scene: PluginComposeScene, x: Int, y: Int, permissions: McpPermissions): CallToolResult {
        val server = Server(
            serverInfo = Implementation(name = "test", version = "1.0.0"),
            options = ServerOptions(ServerCapabilities(tools = ServerCapabilities.Tools())),
        )
        val sceneService = mock<PluginComposeSceneService> {
            everySuspend { getOrCreatePluginScene(any(), any()) } returns scene
        }
        val permissionsRepository = FakeMcpPermissionsRepository(permissions)
        SecondaryClickMcpTool(sceneService, permissionsRepository).register(McpToolRegistrar(server, FakeMcpActivityRepository(), permissionsRepository))
        val request = CallToolRequest(
            CallToolRequestParams(
                name = "jetwhale.secondaryClick",
                arguments = buildJsonObject {
                    put("pluginId", "plugin")
                    put("sessionId", "session")
                    put("x", x)
                    put("y", y)
                },
            ),
        )
        return server.tools.getValue("jetwhale.secondaryClick").handler(mock<ClientConnection>(), request)
    }
}

private fun selfAndDescendants(node: NodeInfo): List<NodeInfo> = listOf(node) + node.children.flatMap(::selfAndDescendants)
