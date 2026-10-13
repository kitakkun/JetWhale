package com.kitakkun.jetwhale.host.mcp.viewport

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import com.kitakkun.jetwhale.host.mcp.tools.AccessibilityTreeResult
import com.kitakkun.jetwhale.host.mcp.tools.NodeInfo
import com.kitakkun.jetwhale.host.mcp.tools.captureAccessibilityTree
import com.kitakkun.jetwhale.host.mcp.tools.createTestScene
import com.kitakkun.jetwhale.host.mcp.tools.renderTestScene
import com.kitakkun.jetwhale.host.model.PluginComposeScene
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class RenderDiscardingPixelsTest {
    @Test
    fun `a click or type reads the tree after the pending recomposition has run`() = runBlocking(Dispatchers.Main) {
        var label by mutableStateOf("before")
        val scene = createTestScene { BasicText(label) }
        renderTestScene(scene)
        label = "after"
        Snapshot.sendApplyNotifications()

        ensureSceneRendered(scene)

        assertEquals(listOf("after"), textsOf(scene))
    }

    @Test
    fun `the accessibility tree is captured after the pending recomposition has run`() = runBlocking(Dispatchers.Main) {
        var label by mutableStateOf("before")
        val scene = createTestScene { BasicText(label) }
        renderTestScene(scene)
        label = "after"
        Snapshot.sendApplyNotifications()

        val tree = Json.decodeFromString<AccessibilityTreeResult>(captureAccessibilityTree(scene))

        assertEquals(listOf("after"), tree.nodes.flatMap(::textsOf))
    }

    private fun textsOf(node: NodeInfo): List<String> = listOfNotNull(node.text) + node.children.flatMap(::textsOf)

    private fun textsOf(scene: PluginComposeScene): List<String> = scene.semanticsOwners.flatMap { textsOf(it.rootSemanticsNode) }

    private fun textsOf(node: SemanticsNode): List<String> = listOfNotNull(node.config.getOrNull(SemanticsProperties.Text)?.joinToString(transform = AnnotatedString::text)) + node.children.flatMap(::textsOf)
}
