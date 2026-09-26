package com.kitakkun.jetwhale.host.screen

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.kitakkun.jetwhale.host.model.HostVersionInfo
import com.kitakkun.jetwhale.host.ui.JwTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DisabledPluginScreenTest {
    @Test
    fun `a disabled plugin's screen enables it from its button`() = runComposeUiTest {
        var enableClicks = 0
        setContent {
            JwTheme(darkTheme = false) {
                DisabledPluginScreen(pluginName = "Network", enableFailed = false, onClickEnable = { enableClicks++ })
            }
        }

        onNodeWithText("Network is disabled").assertExists()
        onNodeWithText("Enable").performClick()

        assertEquals(1, enableClicks)
    }

    @Test
    fun `a plugin the app doesn't include shows how to add it and offers no Enable`() = runComposeUiTest {
        setContent {
            JwTheme(darkTheme = false) {
                NotInAppPluginScreen(
                    pluginName = "Storage Inspector",
                    setup = AgentSetup.forPlugin("com.kitakkun.jetwhale.storage", HostVersionInfo("1.2.0")),
                )
            }
        }

        onNodeWithText("This app doesn't include Storage Inspector").assertExists()
        onNodeWithText("Enable").assertDoesNotExist()
        onNodeWithText("Open the plugin's guide").assertExists()
    }

    @Test
    fun `an official plugin's setup names its agent at the host's version`() {
        val setup = AgentSetup.forPlugin("com.kitakkun.jetwhale.storage", HostVersionInfo("1.2.0"))

        assertTrue("com.kitakkun.jetwhale:jetwhale-storage-inspector-agent:1.2.0" in setup.gradleDependencies)
        assertTrue("register(JetWhaleStorageAgentPlugin.platformDefaults())" in setup.registration)
        assertEquals("https://kitakkun.github.io/JetWhale/guide/storage-inspector", setup.guideUrl)
    }

    @Test
    fun `another plugin's setup uses placeholders and has no guide`() {
        val setup = AgentSetup.forPlugin("com.example.unknown", HostVersionInfo("1.2.0"))

        assertTrue("<group>:<plugin agent artifact>:<version>" in setup.gradleDependencies)
        assertEquals(null, setup.guideUrl)
    }
}
