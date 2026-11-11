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
import com.kitakkun.jetwhale.host.sdk.LocalAiOperatedUiTargets
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.rememberJwSplitPaneState
import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpRequest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class, ExperimentalJetWhaleApi::class)
class NetworkInspectorScreenTest {
    @Test
    fun `each control on the traffic tab is ringed only while an agent operates its target`() = runNetworkInspectorScreen { operatedUiTargets ->
        assertRingedControlsForEachTarget(
            controls = mapOf(
                "traffic list" to onNode(hasScrollToIndexAction()),
                "transaction detail" to onNode(isVerticallyScrollable and hasAnyDescendant(hasText("Select a request to see details"))),
                "clear button" to onNodeWithText("Clear"),
            ),
            ringedControlNamesByTarget = mapOf(
                NetworkUiTarget.TrafficList to listOf("traffic list"),
                NetworkUiTarget.TransactionDetail to listOf("transaction detail"),
                NetworkUiTarget.ClearButton to listOf("clear button"),
                NetworkUiTarget.MocksTab to emptyList(),
                NetworkUiTarget.MockingSwitch to emptyList(),
                NetworkUiTarget.MockRuleList to emptyList(),
            ),
            operatedUiTargets = operatedUiTargets,
        )
    }

    @Test
    fun `each control on the mocks tab is ringed only while an agent operates its target`() = runNetworkInspectorScreen { operatedUiTargets ->
        onNodeWithText("Mocks").performClick()
        mainClock.advanceTimeByFrame()

        assertRingedControlsForEachTarget(
            controls = mapOf(
                "mocks pane" to onNode(isVerticallyScrollable and hasAnyDescendant(hasText("Add rule"))),
                "mocking switch" to onNode(hasContentDescription("Mocking enabled")),
                "mock rules" to onNodeWithText("No mock rules. Add one to override matching responses."),
            ),
            ringedControlNamesByTarget = mapOf(
                NetworkUiTarget.MocksTab to listOf("mocks pane"),
                NetworkUiTarget.MockingSwitch to listOf("mocking switch"),
                NetworkUiTarget.MockRuleList to listOf("mock rules"),
                NetworkUiTarget.TrafficList to emptyList(),
            ),
            operatedUiTargets = operatedUiTargets,
        )
    }

    private fun ComposeUiTest.assertRingedControlsForEachTarget(
        controls: Map<String, SemanticsNodeInteraction>,
        ringedControlNamesByTarget: Map<NetworkUiTarget, List<String>>,
        operatedUiTargets: MutableState<Set<Any>>,
    ) {
        val idleEdgePixels = controls.mapValues { (_, control) -> control.captureEdgePixels() }
        ringedControlNamesByTarget.forEach { (target, ringedControlNames) ->
            operatedUiTargets.value = setOf(target)
            mainClock.advanceTimeByFrame()

            assertEquals(ringedControlNames, controls.filter { (controlName, control) -> control.captureEdgePixels() != idleEdgePixels.getValue(controlName) }.keys.toList(), "while $target is operated")
        }
    }

    /** The pixels along the node's edges, where the ring of a control the node fills is drawn. */
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

    private val isVerticallyScrollable = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)

    private companion object {
        /** Covers the ring's stroke, which lies just inside the ringed control's bounds. */
        const val EDGE_WIDTH_PX = 3
    }
}

@OptIn(ExperimentalTestApi::class, ExperimentalJetWhaleApi::class)
private fun runNetworkInspectorScreen(block: ComposeUiTest.(operatedUiTargets: MutableState<Set<Any>>) -> Unit) = runComposeUiTest {
    mainClock.autoAdvance = false
    val operatedUiTargets = mutableStateOf(emptySet<Any>())
    setContent {
        JwTheme(darkTheme = false) {
            CompositionLocalProvider(LocalAiOperatedUiTargets provides operatedUiTargets.value) {
                Box(Modifier.requiredSize(width = 900.dp, height = 600.dp)) {
                    NetworkInspectorScreen(
                        transactions = listOf(HttpTransaction(request = CapturedHttpRequest(txId = "tx-1", method = "GET", url = "https://example.com/items", timestampMs = 0L))),
                        mockRules = emptyList(),
                        mockingEnabled = true,
                        trafficSplitPaneState = rememberJwSplitPaneState(0.42f),
                        onClearTransactions = {},
                        onToggleMocking = {},
                        onMockRulesChanged = {},
                    )
                }
            }
        }
    }
    mainClock.advanceTimeByFrame()
    block(operatedUiTargets)
}
