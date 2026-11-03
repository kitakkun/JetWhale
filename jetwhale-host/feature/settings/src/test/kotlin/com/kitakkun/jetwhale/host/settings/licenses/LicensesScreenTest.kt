package com.kitakkun.jetwhale.host.settings.licenses

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
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class LicensesScreenTest {
    @Test
    fun `walking the libraries with the arrow keys opens no dialog, and Enter opens one`() = runComposeUiTest {
        setContent {
            JwTheme(darkTheme = false) {
                LicensesScreen(
                    libraries = Libs(libraries = listOf(library("first"), library("second")), licenses = emptySet()),
                    onClickBack = {},
                )
            }
        }

        onNodeWithText("first").requestFocus()
        press(Key.DirectionDown)
        onNodeWithText("second").assertIsFocused()
        onNodeWithText("About second").assertDoesNotExist()

        press(Key.Enter)
        onNodeWithText("About second").assertExists()
    }

    private fun ComposeUiTest.press(key: Key) {
        onNode(isFocused()).performKeyInput { pressKey(key) }
        mainClock.advanceTimeByFrame()
        waitForIdle()
    }

    private fun library(name: String) = Library(
        uniqueId = "com.example:lib-$name",
        artifactVersion = "1.0.0",
        name = name,
        description = "About $name",
        website = null,
        developers = emptyList(),
        organization = null,
        scm = null,
        licenses = emptySet(),
    )
}
