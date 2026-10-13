package com.kitakkun.jetwhale.plugins.nav3.host

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.plugins.nav3.protocol.NavKeyTypeDescriptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    @Test
    fun `walking the key types with the arrow keys keeps the typed draft, and Enter fills the editor`() = runComposeUiTest {
        val typed = """{"type":"Typed","id":"7"}"""
        var draft by mutableStateOf(typed)
        setContent {
            JwTheme(darkTheme = false) {
                Box(Modifier.requiredSize(width = 900.dp, height = 600.dp)) {
                    Nav3NavigatorScreen(
                        stacks = listOf(snapshot("main", "Home")),
                        keyTypes = listOf(keyType("app.SearchKey"), keyType("app.DetailKey")),
                        selectedStackId = "main",
                        status = null,
                        draft = draft,
                        onSelectStack = {},
                        onApplyOperation = { _, _ -> },
                        onRefresh = {},
                        onDraftChange = { draft = it },
                    )
                }
            }
        }

        onNodeWithText("app.SearchKey").requestFocus()
        press(Key.DirectionDown)
        onNodeWithText("app.DetailKey").assertIsFocused()
        assertEquals(typed, draft)

        press(Key.Enter)
        assertTrue("app.DetailKey" in draft, "Enter fills the editor with the key type's template, now $draft")
    }

    private fun ComposeUiTest.press(key: Key) {
        onNode(isFocused()).performKeyInput { pressKey(key) }
        mainClock.advanceTimeByFrame()
        waitForIdle()
    }
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.keyTypeSearchField(): SemanticsNodeInteraction = onNode(hasSetTextAction() and hasText("Filter by type name"))

@OptIn(ExperimentalTestApi::class)
private fun runNav3NavigatorScreen(keyTypes: List<NavKeyTypeDescriptor>, block: ComposeUiTest.() -> Unit) = runComposeUiTest {
    setContent {
        JwTheme(darkTheme = false) {
            Box(Modifier.requiredSize(width = 900.dp, height = 600.dp)) {
                Nav3NavigatorScreen(
                    stacks = listOf(snapshot("main", "Home")),
                    keyTypes = keyTypes,
                    selectedStackId = "main",
                    status = null,
                    draft = "",
                    onSelectStack = {},
                    onApplyOperation = { _, _ -> },
                    onRefresh = {},
                    onDraftChange = {},
                )
            }
        }
    }
    block()
}
