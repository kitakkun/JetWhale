package com.kitakkun.jetwhale.plugins.nav3.host

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.LocalRunningMcpCommandNames
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.plugins.nav3.protocol.NavKeyTypeDescriptor
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class Nav3NavigatorScreenTest {
    private val keyTypes = listOf(
        keyType("com.example.navigation.ProfileKey"),
        keyType("com.example.settings.SettingsKey"),
        keyType("Detail"),
    )

    @Test
    fun `typing a query lists only the matching key types and counts them`() = runNav3NavigatorScreen(keyTypes) {
        keyTypeSearchField().performTextInput("settings")

        onNodeWithText("com.example.settings.SettingsKey").assertExists()
        onNodeWithText("com.example.navigation.ProfileKey").assertDoesNotExist()
        onNodeWithText("Detail").assertDoesNotExist()
        onNodeWithText("1 / 3").assertExists()
    }

    @Test
    fun `a query no key type matches is named in the empty state`() = runNav3NavigatorScreen(keyTypes) {
        keyTypeSearchField().performTextInput("Checkout")

        onNodeWithText("No key type matches \"Checkout\".").assertExists()
        onNodeWithText("0 / 3").assertExists()
    }

    @Test
    fun `clearing the query lists every key type again`() = runNav3NavigatorScreen(keyTypes) {
        keyTypeSearchField().performTextInput("settings")

        onNodeWithContentDescription("Clear filter").performClick()

        keyTypes.forEach { onNodeWithText(it.serialName).assertExists() }
        onNodeWithText("3 / 3").assertExists()
    }

    @Test
    fun `an app without key types keeps its explanation and offers no search`() = runNav3NavigatorScreen(keyTypes = emptyList()) {
        onNodeWithText("The app exposed no constructible key types", substring = true).assertExists()
        keyTypeSearchField().assertDoesNotExist()
    }

    @OptIn(ExperimentalJetWhaleApi::class)
    @Test
    fun `each command rings exactly the pane that shows what it reads or changes`() = runComposeUiTest {
        mainClock.autoAdvance = false
        var runningCommandNames by mutableStateOf(emptySet<String>())
        setContent {
            CompositionLocalProvider(LocalRunningMcpCommandNames provides runningCommandNames) {
                Nav3NavigatorScreenContent(keyTypes)
            }
        }
        mainClock.advanceTimeByFrame()
        val panes = mapOf(
            "back stack" to onNode(hasScrollToIndexAction() and hasAnyDescendant(hasText("Home()"))),
            "push pane" to onNode(hasScrollToIndexAction() and hasAnyDescendant(hasText("Push a NavKey"))),
        )
        val idleEdges = panes.mapValues { (_, pane) -> pane.edgePixels() }

        mapOf(
            screenMcpCommands.getBackStack to listOf("back stack"),
            screenMcpCommands.pushNavKey to listOf("back stack"),
            screenMcpCommands.popBackStack to listOf("back stack"),
            screenMcpCommands.removeNavKey to listOf("back stack"),
            screenMcpCommands.moveNavKeyToTop to listOf("back stack"),
            screenMcpCommands.replaceBackStack to listOf("back stack"),
            screenMcpCommands.listNavKeyTypes to listOf("push pane"),
        ).forEach { (command, ringedPanes) ->
            runningCommandNames = setOf(command.name)
            mainClock.advanceTimeByFrame()

            assertEquals(ringedPanes, panes.filter { (name, pane) -> pane.edgePixels() != idleEdges.getValue(name) }.keys.toList(), "while ${command.name} runs")
        }
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

private val screenMcpCommands = Nav3McpCommands(FakeNav3BackStackController(stacks = emptyList()))

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.keyTypeSearchField(): SemanticsNodeInteraction = onNode(hasSetTextAction() and hasText("Filter by type name"))

@OptIn(ExperimentalTestApi::class)
private fun runNav3NavigatorScreen(keyTypes: List<NavKeyTypeDescriptor>, block: ComposeUiTest.() -> Unit) = runComposeUiTest {
    setContent { Nav3NavigatorScreenContent(keyTypes) }
    block()
}

@Composable
private fun Nav3NavigatorScreenContent(keyTypes: List<NavKeyTypeDescriptor>) {
    JwTheme(darkTheme = false) {
        Box(Modifier.requiredSize(width = 900.dp, height = 600.dp)) {
            Nav3NavigatorScreen(
                stacks = listOf(snapshot("main", "Home")),
                keyTypes = keyTypes,
                selectedStackId = "main",
                status = null,
                draft = "",
                mcpCommands = screenMcpCommands,
                onSelectStack = {},
                onApplyOperation = { _, _ -> },
                onRefresh = {},
                onDraftChange = {},
            )
        }
    }
}
