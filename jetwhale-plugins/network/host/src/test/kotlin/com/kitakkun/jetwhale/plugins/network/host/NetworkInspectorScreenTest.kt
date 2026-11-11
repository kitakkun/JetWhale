package com.kitakkun.jetwhale.plugins.network.host

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.host.sdk.LocalRunningMcpCommandNames
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.rememberJwSplitPaneState
import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpRequest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class, ExperimentalJetWhaleApi::class)
class NetworkInspectorScreenTest {
    @Test
    fun `each command rings exactly the controls on the traffic tab that do the same`() = runNetworkInspectorScreen { runningCommandNames ->
        assertEachCommandRings(
            controls = mapOf(
                "traffic list" to onNode(hasScrollToIndexAction()),
                "transaction detail" to onNode(isVerticallyScrollable and hasAnyDescendant(hasText("Select a request to see details"))),
                "clear button" to onNodeWithText("Clear"),
            ),
            ringedControlsByCommand = mapOf(
                testMcpCommands.listTransactions to listOf("traffic list"),
                testMcpCommands.getTransaction to listOf("transaction detail"),
                testMcpCommands.clearTransactions to listOf("clear button"),
                testMcpCommands.getMockConfig to emptyList(),
                testMcpCommands.setMockingEnabled to emptyList(),
                testMcpCommands.addMockRule to emptyList(),
            ),
            runningCommandNames = runningCommandNames,
        )
    }

    @Test
    fun `each command rings exactly the controls on the mocks tab that do the same`() = runNetworkInspectorScreen { runningCommandNames ->
        onNodeWithText("Mocks").performClick()
        mainClock.advanceTimeByFrame()

        assertEachCommandRings(
            controls = mapOf(
                "mocks pane" to onNode(isVerticallyScrollable and hasAnyDescendant(hasText("Add rule"))),
                "mocking switch" to onNode(hasContentDescription("Mocking enabled")),
                "mock rules" to onNodeWithText("No mock rules. Add one to override matching responses."),
            ),
            ringedControlsByCommand = mapOf(
                testMcpCommands.getMockConfig to listOf("mocks pane"),
                testMcpCommands.setMockingEnabled to listOf("mocking switch"),
                testMcpCommands.addMockRule to listOf("mock rules"),
                testMcpCommands.removeMockRule to listOf("mock rules"),
                testMcpCommands.setMockRules to listOf("mock rules"),
                testMcpCommands.listTransactions to emptyList(),
            ),
            runningCommandNames = runningCommandNames,
        )
    }

    private fun ComposeUiTest.assertEachCommandRings(
        controls: Map<String, SemanticsNodeInteraction>,
        ringedControlsByCommand: Map<JetWhaleMcpCommand, List<String>>,
        runningCommandNames: MutableState<Set<String>>,
    ) {
        val idleEdges = controls.mapValues { (_, control) -> control.edgePixels() }
        ringedControlsByCommand.forEach { (command, ringedControls) ->
            runningCommandNames.value = setOf(command.name)
            mainClock.advanceTimeByFrame()

            assertEquals(ringedControls, controls.filter { (name, control) -> control.edgePixels() != idleEdges.getValue(name) }.keys.toList(), "while ${command.name} runs")
        }
    }

    /** The pixels along the node's edges, where the ring of a control the node fills is drawn. */
    private fun SemanticsNodeInteraction.edgePixels(): List<Color> {
        val pixels = captureToImage().toPixelMap()
        return buildList {
            for (x in 0 until pixels.width) {
                for (y in 0 until pixels.height) {
                    if (x < EDGE_WIDTH_PX || y < EDGE_WIDTH_PX || x >= pixels.width - EDGE_WIDTH_PX || y >= pixels.height - EDGE_WIDTH_PX) add(pixels[x, y])
                }
            }
        }
    }

    private val isVerticallyScrollable = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)

    private companion object {
        /** Covers the ring's stroke, which lies just inside the ringed control's bounds. */
        const val EDGE_WIDTH_PX = 3
    }
}

@OptIn(ExperimentalTestApi::class, ExperimentalJetWhaleApi::class)
private fun runNetworkInspectorScreen(block: ComposeUiTest.(runningCommandNames: MutableState<Set<String>>) -> Unit) = runComposeUiTest {
    mainClock.autoAdvance = false
    val runningCommandNames = mutableStateOf(emptySet<String>())
    setContent {
        JwTheme(darkTheme = false) {
            CompositionLocalProvider(LocalRunningMcpCommandNames provides runningCommandNames.value) {
                Box(Modifier.requiredSize(width = 900.dp, height = 600.dp)) {
                    NetworkInspectorScreen(
                        transactions = listOf(HttpTransaction(request = CapturedHttpRequest(txId = "tx-1", method = "GET", url = "https://example.com/items", timestampMs = 0L))),
                        mockRules = emptyList(),
                        mockingEnabled = true,
                        trafficSplitPaneState = rememberJwSplitPaneState(0.42f),
                        mcpCommands = testMcpCommands,
                        onClearTransactions = {},
                        onToggleMocking = {},
                        onMockRulesChanged = {},
                    )
                }
            }
        }
    }
    mainClock.advanceTimeByFrame()
    block(runningCommandNames)
}
