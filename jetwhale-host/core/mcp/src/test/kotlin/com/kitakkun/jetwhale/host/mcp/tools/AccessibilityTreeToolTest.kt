package com.kitakkun.jetwhale.host.mcp.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(InternalComposeUiApi::class)
class AccessibilityTreeToolTest {

    @Test
    fun `captureAccessibilityTree returns valid JSON on empty scene`() {
        val scene = createTestScene()

        val json = captureAccessibilityTree(scene)

        // Should be parseable and contain the "nodes" key
        val root = Json.parseToJsonElement(json).jsonObject
        assertTrue(root.containsKey("nodes"), "Expected 'nodes' key in result")
    }

    @Test
    fun `captureAccessibilityTree nodes list is present and is an array`() {
        val scene = createTestScene()

        val json = captureAccessibilityTree(scene)

        val root = Json.parseToJsonElement(json).jsonObject
        val nodes = root["nodes"]?.jsonArray
        assertTrue(nodes != null, "Expected 'nodes' to be a JSON array")
    }

    @Test
    fun `captureAccessibilityTree reflects clickable elements`() {
        val scene = createTestScene {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .semantics { contentDescription = "test-button" }
                    .clickable {},
            )
        }
        scene.composeScene.size = IntSize(1280, 720)
        scene.composeScene.render(Canvas(ImageBitmap(1280, 720)), System.nanoTime())

        val json = captureAccessibilityTree(scene)
        val root = Json.parseToJsonElement(json).jsonObject

        // At least one node should be present
        val nodes = root["nodes"]?.jsonArray
        assertTrue(nodes?.isNotEmpty() ?: false, "Expected at least one semantics node")
    }

    @Test
    fun `captureAccessibilityTree includes bounds for each node`() {
        val scene = createTestScene {
            Box(modifier = Modifier.size(200.dp).semantics { contentDescription = "bounded" })
        }
        scene.composeScene.size = IntSize(1280, 720)
        scene.composeScene.render(Canvas(ImageBitmap(1280, 720)), System.nanoTime())

        val json = captureAccessibilityTree(scene)

        fun checkBounds(nodes: kotlinx.serialization.json.JsonArray) {
            for (node in nodes) {
                val obj = node.jsonObject
                val bounds = obj["bounds"]?.jsonObject
                assertTrue(bounds != null, "Expected 'bounds' in each node")
                assertTrue(bounds.containsKey("left"))
                assertTrue(bounds.containsKey("top"))
                assertTrue(bounds.containsKey("right"))
                assertTrue(bounds.containsKey("bottom"))
                val children = obj["children"]?.jsonArray
                if (children != null) checkBounds(children)
            }
        }
        checkBounds(Json.parseToJsonElement(json).jsonObject["nodes"]!!.jsonArray)
    }
}
