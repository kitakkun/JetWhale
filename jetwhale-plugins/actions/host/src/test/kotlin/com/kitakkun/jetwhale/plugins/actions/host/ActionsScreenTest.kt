package com.kitakkun.jetwhale.plugins.actions.host

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.LocalAiOperatedUiTargets
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionCatalog
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class, ExperimentalJetWhaleApi::class)
class ActionsScreenTest {
    private val signIn = action("Account / Sign in", destructive = false, parameters = emptyList())
    private val signOut = action("Account / Sign out", destructive = false, parameters = emptyList())
    private val browser = ActionsBrowser(FakeActionsClient(actions = listOf(signIn, signOut), options = emptyMap(), result = succeeded), CoroutineScope(EmptyCoroutineContext))

    @Test
    fun `the action list is ringed while an agent operates it, and an action's row while the agent runs that action`() = runComposeUiTest {
        mainClock.autoAdvance = false
        var operatedUiTargets by mutableStateOf(emptySet<Any>())
        setContent {
            JwTheme(darkTheme = false) {
                CompositionLocalProvider(LocalAiOperatedUiTargets provides operatedUiTargets) {
                    ActionsScreen(
                        catalog = ActionCatalog(listOf(signIn, signOut)),
                        selectedAction = null,
                        tab = ActionsTab.ACTIONS,
                        history = emptyList(),
                        selectedRunId = null,
                        prefill = null,
                        options = emptyMap(),
                        status = null,
                        query = "",
                        pinnedIds = emptySet(),
                        rememberedArguments = emptyMap(),
                        actions = browser,
                        onQueryChange = {},
                        onTogglePin = {},
                        onRun = { _, _, _ -> },
                    )
                }
            }
        }
        mainClock.advanceTimeByFrame()
        val edges = mapOf(
            "action list" to { onNode(hasScrollToIndexAction() and hasAnyDescendant(hasText(signIn.title))).captureBottomEdgePixels() },
            "sign-in row" to { actionRow(signIn.title).captureTopEdgePixels() },
            "sign-out row" to { actionRow(signOut.title).captureTopEdgePixels() },
        )
        val idleEdgePixels = edges.mapValues { (_, capture) -> capture() }

        mapOf(
            ActionsUiTarget.ActionList to listOf("action list"),
            ActionsUiTarget.ActionRow(signIn.id) to listOf("sign-in row"),
            ActionsUiTarget.ActionRow("Account / Delete") to emptyList(),
        ).forEach { (target, ringedNames) ->
            operatedUiTargets = setOf(target)
            mainClock.advanceTimeByFrame()

            assertEquals(ringedNames, edges.filter { (name, capture) -> capture() != idleEdgePixels.getValue(name) }.keys.toList(), "while $target is operated")
        }
    }

    private fun SemanticsNodeInteractionsProvider.actionRow(title: String): SemanticsNodeInteraction = onNode(hasClickAction() and hasText(title))

    private fun SemanticsNodeInteraction.captureTopEdgePixels(): List<Color> = captureHorizontalBandPixels { 0 until EDGE_WIDTH_PX }

    private fun SemanticsNodeInteraction.captureBottomEdgePixels(): List<Color> = captureHorizontalBandPixels { height -> height - EDGE_WIDTH_PX until height }

    /** The pixels of the rows [rowsOf] picks, short of the corners, where the ring of an enclosing pane also falls. */
    private fun SemanticsNodeInteraction.captureHorizontalBandPixels(rowsOf: (height: Int) -> IntRange): List<Color> {
        val pixels = captureToImage().toPixelMap()
        return rowsOf(pixels.height).flatMap { y -> (EDGE_WIDTH_PX until pixels.width - EDGE_WIDTH_PX).map { x -> pixels[x, y] } }
    }

    private companion object {
        /** Covers the ring's stroke, which lies just inside the ringed element's bounds. */
        const val EDGE_WIDTH_PX = 3
    }
}
