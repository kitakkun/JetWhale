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
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.LocalRunningMcpCommandNames
import com.kitakkun.jetwhale.host.ui.JwTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class, ExperimentalJetWhaleApi::class)
class ComposeSemanticsInspectorScreenTest {
    @Test
    fun `each command rings exactly the pane or controls that show or do the same`() = runComposeUiTest {
        mainClock.autoAdvance = false
        var runningCommandNames by mutableStateOf(emptySet<String>())
        val selectedView = viewNode(id = -2, viewClass = "android.widget.Button", text = "Send", actions = listOf("OnClick"), isClickable = true)
        setContent {
            JwTheme(darkTheme = false) {
                CompositionLocalProvider(LocalRunningMcpCommandNames provides runningCommandNames) {
                    Box(Modifier.requiredSize(width = 1000.dp, height = 800.dp)) {
                        ComposeSemanticsInspectorScreen(
                            snapshot = snapshot(root("window", node = node(id = 1, text = "top", children = listOf(selectedView)))),
                            capturing = false,
                            roundTripMs = null,
                            errorMessage = null,
                            actionStatus = null,
                            highlightStatus = null,
                            viewAttributes = ViewAttributesUiState.Empty,
                            mcpCommands = testMcpCommands,
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
        val controls = mapOf(
            "node tree" to onNode(hasScrollToIndexAction()),
            "node actions" to onNodeWithText("Run an action on the app"),
            "view attributes" to onNodeWithText("View attributes"),
        )
        val idleEdgePixels = controls.mapValues { (_, control) -> control.captureEdgePixels() }

        mapOf(
            testMcpCommands.getNodeTree to listOf("node tree"),
            testMcpCommands.findNodes to listOf("node tree"),
            testMcpCommands.nodeAt to listOf("node tree"),
            testMcpCommands.performNodeAction to listOf("node actions"),
            testMcpCommands.getViewAttributes to listOf("view attributes"),
            testMcpCommands.setViewAttribute to listOf("view attributes"),
        ).forEach { (command, ringedControlNames) ->
            runningCommandNames = setOf(command.name)
            mainClock.advanceTimeByFrame()

            assertEquals(ringedControlNames, controls.filter { (controlName, control) -> control.captureEdgePixels() != idleEdgePixels.getValue(controlName) }.keys.toList(), "while ${command.name} runs")
        }
    }

    /**
     * The pixels along the node's edges, where the ring shows of a pane or group the node fills, or that the
     * node starts at the left edge of.
     */
    private fun SemanticsNodeInteraction.captureEdgePixels(): List<Color> {
        val pixels = captureToImage().toPixelMap()
        return buildList {
            for (x in 0 until pixels.width) {
                for (y in 0 until pixels.height) {
                    if (x < EDGE_WIDTH_PX || y < EDGE_WIDTH_PX || x >= pixels.width - EDGE_WIDTH_PX || y >= pixels.height - EDGE_WIDTH_PX) add(pixels[x, y])
                }
            }
        }
    }

    private companion object {
        /** Covers the ring's stroke, which lies just inside the ringed element's bounds. */
        const val EDGE_WIDTH_PX = 3
    }
}
