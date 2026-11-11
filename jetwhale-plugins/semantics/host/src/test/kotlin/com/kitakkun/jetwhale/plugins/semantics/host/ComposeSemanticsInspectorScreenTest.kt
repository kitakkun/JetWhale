package com.kitakkun.jetwhale.plugins.semantics.host

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.LocalAiOperatedUiTargets
import com.kitakkun.jetwhale.host.ui.JwTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class, ExperimentalJetWhaleApi::class)
class ComposeSemanticsInspectorScreenTest {
    @Test
    fun `each pane, row or group is ringed only while an agent operates its target`() = runComposeUiTest {
        mainClock.autoAdvance = false
        var operatedUiTargets by mutableStateOf(emptySet<Any>())
        val selectedView = viewNode(id = -2, viewClass = "android.widget.Button", text = "Send", actions = listOf("OnClick"), isClickable = true)
        setContent {
            JwTheme(darkTheme = false) {
                CompositionLocalProvider(LocalAiOperatedUiTargets provides operatedUiTargets) {
                    Box(Modifier.requiredSize(width = 1000.dp, height = 800.dp)) {
                        ComposeSemanticsInspectorScreen(
                            snapshot = snapshot(root("window", node = node(id = 1, text = "top", children = listOf(selectedView)))),
                            capturing = false,
                            roundTripMs = null,
                            errorMessage = null,
                            actionStatus = null,
                            highlightStatus = null,
                            viewAttributes = ViewAttributesUiState.Empty,
                            flags = SemanticsInspectorFlags(merged = true, interactiveOnly = false, includeInvisible = false, autoRefresh = false, highlightOnDevice = false),
                            selectedKey = NodeKey(rootId = "window", nodeId = selectedView.id),
                            onRefresh = {},
                            onFlagsChange = {},
                            onSelectKey = {},
                            onHoverChange = { _, _ -> },
                            onPerformAction = {},
                            onCommitViewAttribute = { _, _ -> },
                        )
                    }
                }
            }
        }
        mainClock.advanceTimeByFrame()
        // A node row's ring reaches the tree's sides but not its top, where the root's header row sits, and a pane's
        // ring never reaches a node row's top edge. A group's header starts at the group's left edge, below its top.
        val edges = mapOf(
            "node tree" to { onNode(hasScrollToIndexAction()).captureTopEdgePixels() },
            "top row" to { onNode(hasClickAction() and hasText("top", substring = true)).captureTopEdgePixels() },
            "selected row" to { onNode(hasClickAction() and hasText("Send", substring = true)).captureTopEdgePixels() },
            "node actions" to { onNodeWithText("Run an action on the app").captureLeftEdgePixels() },
            "view attributes" to { onNodeWithText("View attributes").captureLeftEdgePixels() },
        )
        val idleEdgePixels = edges.mapValues { (_, capture) -> capture() }

        mapOf(
            SemanticsUiTarget.NodeTree to listOf("node tree"),
            SemanticsUiTarget.Node(rootId = "window", nodeId = selectedView.id) to listOf("selected row", "node actions"),
            SemanticsUiTarget.Node(rootId = "window", nodeId = 1) to listOf("top row"),
            SemanticsUiTarget.ViewAttributes(rootId = "window", nodeId = selectedView.id) to listOf("view attributes"),
            SemanticsUiTarget.ViewAttributes(rootId = "window", nodeId = 1) to emptyList(),
        ).forEach { (target, ringedNames) ->
            operatedUiTargets = setOf(target)
            mainClock.advanceTimeByFrame()

            assertEquals(ringedNames, edges.filter { (name, capture) -> capture() != idleEdgePixels.getValue(name) }.keys.toList(), "while $target is operated")
        }
    }

    /** The pixels along the node's top edge, short of the corners, where the ring of an enclosing pane also falls. */
    private fun SemanticsNodeInteraction.captureTopEdgePixels(): List<Color> {
        val pixels = captureToImage().toPixelMap()
        return (0 until EDGE_WIDTH_PX).flatMap { y -> (EDGE_WIDTH_PX until pixels.width - EDGE_WIDTH_PX).map { x -> pixels[x, y] } }
    }

    private fun SemanticsNodeInteraction.captureLeftEdgePixels(): List<Color> {
        val pixels = captureToImage().toPixelMap()
        return (0 until EDGE_WIDTH_PX).flatMap { x -> (0 until pixels.height).map { y -> pixels[x, y] } }
    }

    private companion object {
        /** Covers the ring's stroke, which lies just inside the ringed element's bounds. */
        const val EDGE_WIDTH_PX = 3
    }
}
