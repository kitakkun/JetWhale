package com.kitakkun.jetwhale.plugins.nav3.host

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.plugins.nav3.protocol.NavKeyTypeDescriptor
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class Nav3NavigatorScreenTest {
    @Test
    fun `walking the key types with the arrow keys keeps the typed draft, and Enter fills the editor`() = runComposeUiTest {
        val typed = """{"type":"Typed","id":"7"}"""
        var draft by mutableStateOf(typed)
        setContent {
            JwTheme(darkTheme = false) {
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

    private fun keyType(serialName: String) = NavKeyTypeDescriptor(
        serialName = serialName,
        fields = emptyList(),
        template = buildJsonObject { put("type", serialName) },
    )
}
