package com.kitakkun.jetwhale.host.mcp.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.mcp.viewport.McpViewport
import com.kitakkun.jetwhale.host.model.PluginComposeScene
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(InternalComposeUiApi::class)
class AccessibilityTreeToolTest {

    @Test
    fun `captureAccessibilityTree on empty scene returns no meaningful nodes`() {
        val scene = createTestScene()

        val result = Json.decodeFromString<AccessibilityTreeResult>(captureAccessibilityTree(scene))

        val allNodes = result.nodes.flatMap { collectAllNodes(it) }
        assertTrue(allNodes.none { it.contentDescription != null || it.text != null || it.isClickable })
    }

    @Test
    fun `captureAccessibilityTree reflects clickable node properties`() {
        val scene = createTestScene {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .semantics { contentDescription = "test-button" }
                    .clickable {},
            )
        }
        renderTestScene(scene)

        val result = Json.decodeFromString<AccessibilityTreeResult>(captureAccessibilityTree(scene))

        val allNodes = result.nodes.flatMap { collectAllNodes(it) }
        val button = allNodes.find { it.contentDescription == "test-button" }
        assertNotNull(button, "Expected a node with contentDescription 'test-button'")
        assertTrue(button.isClickable, "Expected the node to be clickable")
    }

    @Test
    fun `captureAccessibilityTree includes correct bounds for elements`() {
        val scene = createTestScene {
            Box(modifier = Modifier.size(200.dp).semantics { contentDescription = "bounded" })
        }
        renderTestScene(scene)

        val result = Json.decodeFromString<AccessibilityTreeResult>(captureAccessibilityTree(scene))

        val allNodes = result.nodes.flatMap { collectAllNodes(it) }
        val target = allNodes.find { it.contentDescription == "bounded" }
        assertNotNull(target, "Expected a node with contentDescription 'bounded'")
        assertEquals(0f, target.bounds.left)
        assertEquals(0f, target.bounds.top)
        assertEquals(200f, target.bounds.right)
        assertEquals(200f, target.bounds.bottom)
    }

    @Test
    fun `captureAccessibilityTree leaves the window info it had`() {
        val scene = createTestScene()
        scene.composeScene.size = IntSize(320, 240)

        captureAccessibilityTree(scene)

        // The tree is captured at the scene's own size, but the window info is a separate piece of
        // state that the tool must not repoint at it.
        assertEquals(IntSize(TEST_SCENE_WIDTH, TEST_SCENE_HEIGHT), scene.windowInfoUpdater.currentIntSize)
        assertEquals(DpSize(TEST_SCENE_WIDTH.dp, TEST_SCENE_HEIGHT.dp), scene.windowInfoUpdater.currentDpSize)
    }

    @Test
    fun `bounds are unchanged by a screenshot taken at another density`() {
        val scene = createTestScene {
            Box(modifier = Modifier.size(100.dp).semantics { contentDescription = "bounded" })
        }
        scene.composeScene.density = Density(1f)
        renderTestScene(scene)
        val before = boundsOf(scene, "bounded")

        captureScreenshot(scene, McpViewport(size = IntSize(400, 300), density = Density(2f)))

        assertEquals(before, boundsOf(scene, "bounded"), "A screenshot's density must not move later bounds")
    }

    private fun boundsOf(scene: PluginComposeScene, contentDescription: String): BoundsInfo {
        val result = Json.decodeFromString<AccessibilityTreeResult>(captureAccessibilityTree(scene))
        val node = result.nodes.flatMap { collectAllNodes(it) }.find { it.contentDescription == contentDescription }
        assertNotNull(node, "Expected a node with contentDescription '$contentDescription'")
        return node.bounds
    }

    private fun collectAllNodes(node: NodeInfo): List<NodeInfo> = listOf(node) + node.children.flatMap { collectAllNodes(it) }
}
