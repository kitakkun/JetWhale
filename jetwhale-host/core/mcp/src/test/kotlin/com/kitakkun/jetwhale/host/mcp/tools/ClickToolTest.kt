package com.kitakkun.jetwhale.host.mcp.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.kitakkun.jetwhale.host.mcp.viewport.ensureSceneRendered
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(InternalComposeUiApi::class)
class ClickToolTest {

    @Test
    fun `dispatchClick returns false when scene has no clickable elements`() {
        val scene = createTestScene()
        renderTestScene(scene)

        val result = dispatchClick(scene, 100f, 100f)
        assertFalse(result)
    }

    @Test
    fun `dispatchClick returns true when a clickable element is at the given position`() {
        var clicked = false
        val scene = createTestScene {
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clickable { clicked = true },
            )
        }
        renderTestScene(scene)

        val result = dispatchClick(scene, 100f, 100f)

        assertTrue(result, "Expected click to be dispatched to the clickable element")
        assertTrue(clicked, "Expected onClick callback to have been invoked")
    }

    @Test
    fun `dispatchClick returns false when coordinates are outside clickable element`() {
        var clicked = false
        val scene = createTestScene {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clickable { clicked = true },
            )
        }
        renderTestScene(scene)

        // Element is 50dp x 50dp = 50px x 50px at density 1; click far outside
        val result = dispatchClick(scene, 500f, 500f)

        assertFalse(result)
        assertFalse(clicked)
    }

    @Test
    fun `a click on a dialog button reaches the dialog rather than the clickable content beneath it`() {
        var clicked = "nothing"
        val scene = createTestScene {
            Box(Modifier.fillMaxSize().clickable { clicked = "content" })
            Dialog(onDismissRequest = {}) {
                BasicText("Confirm", Modifier.clickable { clicked = "confirm" }.padding(16.dp))
            }
        }
        renderTestScene(scene)
        val confirm = Json.decodeFromString<AccessibilityTreeResult>(captureAccessibilityTree(scene)).nodes
            .flatMap(::selfAndDescendants)
            .single { it.text == "Confirm" }
            .bounds

        ensureSceneRendered(scene)
        dispatchClick(scene, (confirm.left + confirm.right) / 2, (confirm.top + confirm.bottom) / 2)

        assertEquals("confirm", clicked)
    }
}

private fun selfAndDescendants(node: NodeInfo): List<NodeInfo> = listOf(node) + node.children.flatMap(::selfAndDescendants)
