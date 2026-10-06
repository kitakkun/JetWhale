package com.kitakkun.jetwhale.plugins.nav3.host

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.plugins.nav3.protocol.NavKeyTypeDescriptor
import kotlin.test.Test

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
