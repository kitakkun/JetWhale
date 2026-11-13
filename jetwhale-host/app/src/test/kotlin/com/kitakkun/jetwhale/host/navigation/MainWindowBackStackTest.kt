package com.kitakkun.jetwhale.host.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlin.test.Test
import kotlin.test.assertEquals

class MainWindowBackStackTest {
    private val pluginPopoutNavKey = PluginPopoutNavKey(pluginId = "com.example.network", sessionId = "app-1", pluginName = "Network")

    @Test
    fun `going back from a dialog opened before the log viewer closes the dialog and keeps the log viewer window`() {
        val backStack = NavBackStack<NavKey>(EmptyPluginNavKey, SettingsNavKey(), LogViewerNavKey)

        backStack.popMainWindowEntry()

        assertEquals(listOf(EmptyPluginNavKey, LogViewerNavKey), backStack.toList())
    }

    @Test
    fun `going back from a dialog keeps a popped-out plugin's window`() {
        val backStack = NavBackStack<NavKey>(EmptyPluginNavKey, InfoNavKey, pluginPopoutNavKey)

        backStack.popMainWindowEntry()

        assertEquals(listOf(EmptyPluginNavKey, pluginPopoutNavKey), backStack.toList())
    }

    @Test
    fun `going back on the home screen leaves it and the windows of their own`() {
        val backStack = NavBackStack<NavKey>(EmptyPluginNavKey, LogViewerNavKey)

        backStack.popMainWindowEntry()

        assertEquals(listOf(EmptyPluginNavKey, LogViewerNavKey), backStack.toList())
    }
}
