package com.kitakkun.jetwhale.host.shell

import androidx.navigation3.runtime.NavKey
import com.kitakkun.jetwhale.host.plugin.PluginNavKey
import com.kitakkun.jetwhale.host.plugin.PluginPopoutNavKey
import com.kitakkun.jetwhale.host.settings.SettingsNavKey
import com.kitakkun.jetwhale.host.settings.SettingsScreenPage
import com.kitakkun.jetwhale.host.settings.licenses.LicensesNavKey
import com.kitakkun.jetwhale.host.settings.logviewer.LogViewerNavKey
import kotlin.test.Test
import kotlin.test.assertEquals

class NavCommandTest {

    private fun backStackOf(vararg keys: NavKey) = mutableListOf(*keys)

    @Test
    fun `ShowSingleTop moves an entry that is already on the stack to the top`() {
        val backStack = backStackOf(EmptyPluginNavKey, InfoNavKey, LicensesNavKey)

        backStack.applyNavCommand(NavCommand.ShowSingleTop(InfoNavKey))

        assertEquals(listOf(EmptyPluginNavKey, LicensesNavKey, InfoNavKey), backStack)
    }

    @Test
    fun `ShowSingleTopAt puts an entry underneath the ones already on the stack`() {
        val backStack = backStackOf(EmptyPluginNavKey, SettingsNavKey())

        backStack.applyNavCommand(NavCommand.ShowSingleTopAt(index = 0, key = LogViewerNavKey))

        assertEquals(listOf(LogViewerNavKey, EmptyPluginNavKey, SettingsNavKey()), backStack)
    }

    @Test
    fun `PopTop removes only the top entry`() {
        val backStack = backStackOf(EmptyPluginNavKey, InfoNavKey, LicensesNavKey)

        backStack.applyNavCommand(NavCommand.PopTop)

        assertEquals(listOf(EmptyPluginNavKey, InfoNavKey), backStack)
    }

    @Test
    fun `CloseAllOfType removes every entry of that type whatever its arguments`() {
        val backStack = backStackOf(
            EmptyPluginNavKey,
            SettingsNavKey(),
            InfoNavKey,
            SettingsNavKey(initialPage = SettingsScreenPage.InstalledPlugins),
        )

        backStack.applyNavCommand(NavCommand.CloseAllOfType(SettingsNavKey::class))

        assertEquals(listOf(EmptyPluginNavKey, InfoNavKey), backStack)
    }

    @Test
    fun `CloseWindow removes the entry with that content key`() {
        val popout = PluginPopoutNavKey("com.example", "session-1", "Example")
        val backStack = backStackOf(EmptyPluginNavKey, popout)

        backStack.applyNavCommand(NavCommand.CloseWindow(popout.toString()))

        assertEquals(listOf<NavKey>(EmptyPluginNavKey), backStack)
    }

    @Test
    fun `GoHome closes the main window's screens and leaves popout windows open`() {
        val popout = PluginPopoutNavKey("com.example", "session-1", "Example")
        val backStack = backStackOf(EmptyPluginNavKey, popout, PluginNavKey("com.example", "session-1"), InfoNavKey)

        backStack.applyNavCommand(NavCommand.GoHome)

        assertEquals(listOf(EmptyPluginNavKey, popout), backStack)
    }

    @Test
    fun `OpenMcpTools re-seeds the browser rather than stacking a second one`() {
        val backStack = backStackOf(EmptyPluginNavKey, McpToolsNavKey("com.example", "session-1"))

        backStack.applyNavCommand(NavCommand.OpenMcpTools(pluginId = null, sessionId = null))

        assertEquals(listOf(EmptyPluginNavKey, McpToolsNavKey(null, null)), backStack)
    }

    @Test
    fun `OpenMcpTools with the scope already shown leaves the browser untouched`() {
        val browser = McpToolsNavKey("com.example", "session-1")
        val backStack = backStackOf(EmptyPluginNavKey, browser, InfoNavKey)

        backStack.applyNavCommand(NavCommand.OpenMcpTools(pluginId = "com.example", sessionId = "session-1"))

        assertEquals(listOf(EmptyPluginNavKey, browser, InfoNavKey), backStack)
    }

    @Test
    fun `BringPluginBackToMainWindow shows the plugin and closes its popout window`() {
        val backStack = backStackOf(
            EmptyPluginNavKey,
            PluginPopoutNavKey("com.example", "session-1", "Example"),
        )

        backStack.applyNavCommand(NavCommand.BringPluginBackToMainWindow("com.example", "session-1"))

        assertEquals(listOf(EmptyPluginNavKey, PluginNavKey("com.example", "session-1")), backStack)
    }

    @Test
    fun `FollowPluginToSession re-points the top plugin screen at the new session`() {
        val backStack = backStackOf(EmptyPluginNavKey, PluginNavKey("com.example", "session-1"))

        backStack.applyNavCommand(
            NavCommand.FollowPluginToSession(
                newSessionId = "session-2",
                availablePluginIds = setOf("com.example"),
            ),
        )

        assertEquals(listOf(EmptyPluginNavKey, PluginNavKey("com.example", "session-2")), backStack)
    }

    @Test
    fun `FollowPluginToSession closes a plugin the new session does not have`() {
        val backStack = backStackOf(EmptyPluginNavKey, PluginNavKey("com.example", "session-1"))

        backStack.applyNavCommand(
            NavCommand.FollowPluginToSession(
                newSessionId = "session-2",
                availablePluginIds = emptySet(),
            ),
        )

        assertEquals(listOf<NavKey>(EmptyPluginNavKey), backStack)
    }

    @Test
    fun `FollowPluginToSession leaves a top entry that is not a plugin screen alone`() {
        val backStack = backStackOf(EmptyPluginNavKey, InfoNavKey)

        backStack.applyNavCommand(
            NavCommand.FollowPluginToSession(
                newSessionId = "session-2",
                availablePluginIds = setOf("com.example"),
            ),
        )

        assertEquals(listOf(EmptyPluginNavKey, InfoNavKey), backStack)
    }

    @Test
    fun `CloseAllPluginScreens closes plugin screens and popouts alike`() {
        val backStack = backStackOf(
            EmptyPluginNavKey,
            PluginNavKey("com.example", "session-1"),
            PluginPopoutNavKey("com.other", "session-2", "Other"),
            InfoNavKey,
        )

        backStack.applyNavCommand(NavCommand.CloseAllPluginScreens)

        assertEquals(listOf(EmptyPluginNavKey, InfoNavKey), backStack)
    }

    @Test
    fun `ClosePluginScreensForSession closes only that session's plugin screens`() {
        val other = PluginNavKey("com.example", "session-2")
        val backStack = backStackOf(EmptyPluginNavKey, PluginNavKey("com.example", "session-1"), other)

        backStack.applyNavCommand(NavCommand.ClosePluginScreensForSession("session-1"))

        assertEquals(listOf(EmptyPluginNavKey, other), backStack)
    }

    @Test
    fun `ClosePluginScreensForPlugin closes that plugin's screens and popouts`() {
        val other = PluginNavKey("com.other", "session-1")
        val backStack = backStackOf(
            EmptyPluginNavKey,
            PluginNavKey("com.example", "session-1"),
            PluginPopoutNavKey("com.example", "session-2", "Example"),
            other,
        )

        backStack.applyNavCommand(NavCommand.ClosePluginScreensForPlugin("com.example"))

        assertEquals(listOf(EmptyPluginNavKey, other), backStack)
    }
}
