package com.kitakkun.jetwhale.host.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class MainWindowBackHandlerTest {
    private val firstPluginNavKey = PluginNavKey(pluginId = "com.example.network", sessionId = "app-1")
    private val secondPluginNavKey = PluginNavKey(pluginId = "com.example.storage", sessionId = "app-1")

    @Test
    fun `one back with the log viewer open goes back one step in the main window`() = runComposeUiTest {
        val backStack = NavBackStack<NavKey>(EmptyPluginNavKey, firstPluginNavKey, secondPluginNavKey, LogViewerNavKey)
        val backInput = setMainWindowContent(backStack)

        runOnUiThread(backInput::backCompleted)
        waitForIdle()

        assertEquals(listOf(EmptyPluginNavKey, firstPluginNavKey, LogViewerNavKey), backStack.toList())
    }

    @Test
    fun `one back with a popped-out plugin goes back one step in the main window`() = runComposeUiTest {
        val pluginPopoutNavKey = PluginPopoutNavKey(pluginId = "com.example.device", sessionId = "app-1", pluginName = "Device")
        val backStack = NavBackStack<NavKey>(EmptyPluginNavKey, firstPluginNavKey, secondPluginNavKey, pluginPopoutNavKey)
        val backInput = setMainWindowContent(backStack)

        runOnUiThread(backInput::backCompleted)
        waitForIdle()

        assertEquals(listOf(EmptyPluginNavKey, firstPluginNavKey, pluginPopoutNavKey), backStack.toList())
    }
}

/**
 * Composes the main window's NavDisplay and back handler as `JetWhaleNavDisplay` does, and returns
 * the back input that stands in for the window's Esc. The entries with a window of their own become
 * overlay scenes, as with `WindowSceneStrategy`, but open no window.
 */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.setMainWindowContent(backStack: NavBackStack<NavKey>): DirectNavigationEventInput {
    val dispatcherOwner = object : NavigationEventDispatcherOwner {
        override val navigationEventDispatcher = NavigationEventDispatcher()
    }
    val backInput = DirectNavigationEventInput()
    dispatcherOwner.navigationEventDispatcher.addInput(backInput)
    setContent {
        CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides dispatcherOwner) {
            NavDisplay(
                backStack = backStack,
                onBack = backStack::popMainWindowEntry,
                sceneStrategies = listOf(OwnWindowSceneStrategy(), SinglePaneSceneStrategy()),
                entryProvider = { key ->
                    NavEntry(key, metadata = if (key.showsInMainWindow) emptyMap() else mapOf(OWN_WINDOW to true)) {}
                },
            )
            MainWindowBackHandler(backStack)
        }
    }
    return backInput
}

private const val OWN_WINDOW = "ownWindow"

private class OwnWindowSceneStrategy : SceneStrategy<NavKey> {
    override fun SceneStrategyScope<NavKey>.calculateScene(entries: List<NavEntry<NavKey>>): Scene<NavKey>? {
        val windowIndex = entries.indexOfLast { OWN_WINDOW in it.metadata }
        if (windowIndex == -1) return null
        val others = entries.filterIndexed { index, _ -> index != windowIndex }
        return OwnWindowScene(entries[windowIndex], others)
    }
}

private class OwnWindowScene(entry: NavEntry<NavKey>, others: List<NavEntry<NavKey>>) : OverlayScene<NavKey> {
    override val key: Any = entry.contentKey
    override val entries: List<NavEntry<NavKey>> = listOf(entry)
    override val previousEntries: List<NavEntry<NavKey>> = others
    override val overlaidEntries: List<NavEntry<NavKey>> = others
    override val content: @Composable () -> Unit = {}

    override fun equals(other: Any?): Boolean = other is OwnWindowScene && key == other.key

    override fun hashCode(): Int = key.hashCode()
}
