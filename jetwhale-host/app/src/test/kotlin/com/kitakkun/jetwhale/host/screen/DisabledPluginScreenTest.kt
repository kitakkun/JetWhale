package com.kitakkun.jetwhale.host.screen

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.kitakkun.jetwhale.host.ui.JwTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class DisabledPluginScreenTest {
    @Test
    fun `a disabled plugin's screen enables it from its button`() = runComposeUiTest {
        var enableClicks = 0
        setContent {
            JwTheme(darkTheme = false) {
                DisabledPluginScreen(pluginName = "Network", notInApp = false, enableFailed = false, onClickEnable = { enableClicks++ })
            }
        }

        onNodeWithText("Network is disabled").assertExists()
        onNodeWithText("Enable").performClick()

        assertEquals(1, enableClicks)
    }

    @Test
    fun `a plugin the app doesn't include says so and offers no button`() = runComposeUiTest {
        setContent {
            JwTheme(darkTheme = false) {
                DisabledPluginScreen(pluginName = "Network", notInApp = true, enableFailed = false, onClickEnable = {})
            }
        }

        onNodeWithText("This app doesn't include this plugin").assertExists()
        onNodeWithText("Enable").assertDoesNotExist()
    }
}
