package com.kitakkun.jetwhale.plugins.actions.host

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.LocalRunningMcpCommandNames
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionCatalog
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class, ExperimentalJetWhaleApi::class)
class ActionsScreenTest {
    private val signIn = action("Account / Sign in", destructive = false, parameters = emptyList())
    private val browser = ActionsBrowser(FakeActionsClient(actions = listOf(signIn), options = emptyMap(), result = succeeded), CoroutineScope(EmptyCoroutineContext))
    private val listActions = ListActionsCommand(browser)
    private val runAction = RunActionCommand(browser)

    @Test
    fun `the action list is ringed only while an agent lists or runs actions`() = runComposeUiTest {
        mainClock.autoAdvance = false
        var runningCommandNames by mutableStateOf(emptySet<String>())
        setContent {
            JwTheme(darkTheme = false) {
                CompositionLocalProvider(LocalRunningMcpCommandNames provides runningCommandNames) {
                    ActionsScreen(
                        catalog = ActionCatalog(listOf(signIn)),
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
                        listActions = listActions,
                        runAction = runAction,
                        onQueryChange = {},
                        onTogglePin = {},
                        onRun = { _, _, _ -> },
                    )
                }
            }
        }
        mainClock.advanceTimeByFrame()
        val actionList = onNode(hasScrollToIndexAction() and hasAnyDescendant(hasText(signIn.title)))
        val idleEdges = actionList.edgePixels()

        listOf(listActions, runAction).forEach { command ->
            runningCommandNames = setOf(command.name)
            mainClock.advanceTimeByFrame()

            assertTrue(actionList.edgePixels() != idleEdges, "the action list is not ringed while ${command.name} runs")
        }

        runningCommandNames = emptySet()
        mainClock.advanceTimeByFrame()

        assertTrue(actionList.edgePixels() == idleEdges, "the action list stays ringed once no command runs")
    }

    /** The pixels along the node's edges, where the ring of a pane the node fills is drawn. */
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

    private companion object {
        /** Covers the ring's stroke, which lies just inside the ringed pane's bounds. */
        const val EDGE_WIDTH_PX = 3
    }
}
