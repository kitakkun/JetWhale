package com.kitakkun.jetwhale.host.mcp.viewport

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.mcp.tools.TEST_SCENE_HEIGHT
import com.kitakkun.jetwhale.host.mcp.tools.TEST_SCENE_WIDTH
import com.kitakkun.jetwhale.host.mcp.tools.createTestScene
import com.kitakkun.jetwhale.host.model.PluginComposeScene
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(InternalComposeUiApi::class)
class McpViewportUtilsTest {
    @Test
    fun `a frame whose pixels are discarded leaves the same semantics as a full-size render`() {
        val fullSize = layeredScene().apply { render(Canvas(ImageBitmap(TEST_SCENE_WIDTH, TEST_SCENE_HEIGHT))) }
        val discarded = layeredScene().apply { renderDiscardingPixels() }

        val expected = semanticsOf(fullSize)
        assertTrue(expected.size > 10, "The scene should lay out enough nodes to compare: $expected")
        assertEquals(expected, semanticsOf(discarded))
    }

    private fun layeredScene(): PluginComposeScene = createTestScene {
        Column {
            BasicText("heading", Modifier.padding(16.dp))
            Row {
                Box(Modifier.size(width = 120.dp, height = 40.dp).semantics { contentDescription = "left" })
                Box(Modifier.size(200.dp).semantics { contentDescription = "right" })
            }
            LazyColumn(Modifier.height(300.dp)) {
                items(50) { BasicText("row $it", Modifier.padding(8.dp)) }
            }
        }
    }.apply { composeScene.size = IntSize(TEST_SCENE_WIDTH, TEST_SCENE_HEIGHT) }

    private fun semanticsOf(scene: PluginComposeScene): List<Pair<Rect, String?>> = scene.semanticsOwners.flatMap { flatten(it.rootSemanticsNode) }

    private fun flatten(node: SemanticsNode): List<Pair<Rect, String?>> {
        val label = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(transform = AnnotatedString::text)
            ?: node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
        return listOf(node.boundsInRoot to label) + node.children.flatMap(::flatten)
    }
}
