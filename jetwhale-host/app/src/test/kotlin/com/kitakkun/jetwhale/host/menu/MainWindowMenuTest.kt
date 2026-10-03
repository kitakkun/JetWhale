package com.kitakkun.jetwhale.host.menu

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.KeyInjectionScope
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.drawer.DrawerPluginItemUiState
import com.kitakkun.jetwhale.host.model.HostDestination
import com.kitakkun.jetwhale.host.model.HostNavigationRequest
import com.kitakkun.jetwhale.host.model.HostNavigationService
import com.kitakkun.jetwhale.host.model.HostOs
import com.kitakkun.jetwhale.host.model.HostSettingsSection
import com.kitakkun.jetwhale.host.model.HostViewState
import com.kitakkun.jetwhale.host.model.PluginAvailability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MainWindowMenuTest {
    private val navigation = RecordingHostNavigationService()
    private var quitCount = 0
    private var raisedCount = 0
    private val menu = MainWindowMenu(
        hostNavigationService = navigation,
        coroutineScope = CoroutineScope(Dispatchers.Unconfined),
        quit = { quitCount++ },
        raiseMainWindow = { raisedCount++ },
    )

    @Test
    fun `the Plugins menu lists the drawer's enabled plugins with the ones that need no app first`() {
        menu.updatePlugins(
            drawerPlugins = listOf(
                drawerPlugin(id = "network", availability = PluginAvailability.Enabled, needsApp = true),
                drawerPlugin(id = "mirror", availability = PluginAvailability.Enabled, needsApp = false),
                drawerPlugin(id = "storage", availability = PluginAvailability.Disabled, needsApp = true),
                drawerPlugin(id = "semantics", availability = PluginAvailability.Unavailable, needsApp = true),
                drawerPlugin(id = "nav3", availability = PluginAvailability.Enabled, needsApp = true),
            ),
            hasSelectedApp = true,
        )

        assertEquals(listOf("mirror", "network", "nav3"), menu.plugins.map(MenuPlugin::id))
        assertEquals(listOf(Key.One, Key.Two, Key.Three), menu.plugins.map { it.shortcut?.key })
    }

    @Test
    fun `with no app selected the Plugins menu lists only the plugins that need no app`() {
        menu.updatePlugins(
            drawerPlugins = listOf(
                drawerPlugin(id = "network", availability = PluginAvailability.Enabled, needsApp = true),
                drawerPlugin(id = "mirror", availability = PluginAvailability.Enabled, needsApp = false),
            ),
            hasSelectedApp = false,
        )

        assertEquals(listOf("mirror"), menu.plugins.map(MenuPlugin::id))
    }

    @Test
    fun `only the first nine plugins get a shortcut`() {
        menu.updatePlugins(
            drawerPlugins = List(10) { drawerPlugin(id = "plugin-$it", availability = PluginAvailability.Enabled, needsApp = false) },
            hasSelectedApp = false,
        )

        assertEquals(Key.Nine, menu.plugins[8].shortcut?.key)
        assertEquals(null, menu.plugins[9].shortcut)
    }

    @Test
    fun `each shortcut opens its destination through the navigation channel`() = runComposeUiTest {
        menu.updatePlugins(
            drawerPlugins = listOf(drawerPlugin(id = "network", availability = PluginAvailability.Enabled, needsApp = true)),
            hasSelectedApp = true,
        )
        showShortcutTarget()

        pressShortcut { pressKey(Key.Comma) }
        pressShortcut { withKeyDown(Key.ShiftLeft) { pressKey(Key.H) } }
        pressShortcut { withKeyDown(Key.ShiftLeft) { pressKey(Key.L) } }
        pressShortcut { pressKey(Key.One) }
        pressShortcut { pressKey(Key.Q) }

        assertEquals(
            listOf(
                HostNavigationRequest.Settings(HostSettingsSection.GENERAL),
                HostNavigationRequest.Home,
                HostNavigationRequest.LogViewer,
                HostNavigationRequest.Plugin("network", sessionId = null, followsAgent = false),
            ),
            navigation.navigated,
        )
        assertEquals(1, quitCount)
    }

    @Test
    fun `a destination the main window shows brings it to the front and the log viewer leaves it where it is`() {
        menu.openSettings()
        menu.showAbout()
        menu.goHome()
        menu.openPlugin("network")
        assertEquals(4, raisedCount)

        menu.openLogViewer()

        assertEquals(4, raisedCount)
    }

    @Test
    fun `a key pressed without the platform modifier or with Alt or without its Shift runs nothing`() = runComposeUiTest {
        showShortcutTarget()

        onNodeWithTag(TARGET).performKeyInput { pressKey(Key.Comma) }
        pressShortcut { withKeyDown(Key.AltLeft) { pressKey(Key.Comma) } }
        pressShortcut { pressKey(Key.H) }

        assertTrue(navigation.navigated.isEmpty())
    }

    private fun ComposeUiTest.showShortcutTarget() {
        setContent {
            Box(Modifier.size(10.dp).testTag(TARGET).onKeyEvent(menu::runShortcut).focusable())
        }
        onNodeWithTag(TARGET).requestFocus()
    }

    private fun ComposeUiTest.pressShortcut(keys: KeyInjectionScope.() -> Unit) {
        val shortcutModifier = if (HostOs.current == HostOs.MAC) Key.MetaLeft else Key.CtrlLeft
        onNodeWithTag(TARGET).performKeyInput { withKeyDown(shortcutModifier, keys) }
    }
}

private const val TARGET = "shortcut-target"

private fun drawerPlugin(id: String, availability: PluginAvailability, needsApp: Boolean) = DrawerPluginItemUiState(
    name = id,
    id = id,
    activeIconResource = null,
    inactiveIconResource = null,
    pluginAvailability = availability,
    underAiControl = false,
    exposesMcpTools = false,
    isHeadless = false,
    needsApp = needsApp,
)

private class RecordingHostNavigationService : HostNavigationService {
    val navigated = mutableListOf<HostNavigationRequest>()
    override val requests: Flow<HostNavigationRequest> = emptyFlow()
    override val currentView: StateFlow<HostViewState?> = MutableStateFlow(null)

    override suspend fun navigate(request: HostNavigationRequest) {
        navigated += request
    }

    override fun updateDestination(destination: HostDestination) = Unit

    override fun updateSelection(selectedSessionId: String?, selectedPluginId: String?) = Unit
}
