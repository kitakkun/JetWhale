package com.kitakkun.jetwhale.host.mcp.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
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

    private fun collectAllNodes(node: NodeInfo): List<NodeInfo> =
        listOf(node) + node.children.flatMap { collectAllNodes(it) }
}
