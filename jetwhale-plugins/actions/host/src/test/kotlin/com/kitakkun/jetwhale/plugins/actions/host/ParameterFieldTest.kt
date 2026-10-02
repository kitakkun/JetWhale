package com.kitakkun.jetwhale.plugins.actions.host

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.plugins.actions.protocol.ParameterType
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ParameterFieldTest {
    private val email = parameter("email", ParameterType.STRING, optional = false, nullable = false)
    private val suggestions = listOf("qa@example.com", "pro@example.com", "Support@Example.org")

    @Test
    fun `typing narrows the suggestions to those containing the text regardless of case`() = runComposeUiTest {
        var value by mutableStateOf("")
        setContent {
            JwTheme(darkTheme = false) {
                ParameterField(parameter = email, value = value, error = null, suggestions = suggestions, onValueChange = { value = it })
            }
        }

        onNode(hasSetTextAction()).performTextInput("EXAMPLE.COM")

        onAllNodesWithText("qa@example.com").assertCountEquals(1)
        onAllNodesWithText("pro@example.com").assertCountEquals(1)
        onAllNodesWithText("Support@Example.org").assertCountEquals(0)
    }

    @Test
    fun `choosing a suggestion fills the field and closes the menu`() = runComposeUiTest {
        var value by mutableStateOf("")
        setContent {
            JwTheme(darkTheme = false) {
                ParameterField(parameter = email, value = value, error = null, suggestions = suggestions, onValueChange = { value = it })
            }
        }

        onNode(hasSetTextAction()).performTextInput("pro")
        onNodeWithText("pro@example.com").performClick()

        onNode(hasSetTextAction()).assertTextContains("pro@example.com")
        onAllNodesWithText("pro@example.com").assertCountEquals(1)
    }
}
