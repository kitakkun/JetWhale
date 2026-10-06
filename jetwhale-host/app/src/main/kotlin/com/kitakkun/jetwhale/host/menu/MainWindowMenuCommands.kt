package com.kitakkun.jetwhale.host.menu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.window.MenuBarScope
import com.kitakkun.jetwhale.host.Res
import com.kitakkun.jetwhale.host.drawer.DrawerPluginItemUiState
import com.kitakkun.jetwhale.host.log_viewer_window_title
import com.kitakkun.jetwhale.host.menu_go
import com.kitakkun.jetwhale.host.menu_home
import com.kitakkun.jetwhale.host.menu_no_plugins
import com.kitakkun.jetwhale.host.model.HostNavigationRequest
import com.kitakkun.jetwhale.host.model.HostNavigationService
import com.kitakkun.jetwhale.host.model.HostSettingsSection
import com.kitakkun.jetwhale.host.model.PluginAvailability
import com.kitakkun.jetwhale.host.plugins
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * What the main window's menus and shortcuts open, and how each one gets there.
 *
 * Every destination goes through [HostNavigationService], the channel the window already takes
 * navigation requests from, so a menu item lands exactly where the same request from an agent
 * would. A destination the main window shows also brings that window to the front, since the menus
 * are used from pop-outs and the log viewer too. The plugins come from the drawer, which publishes
 * the ones it can open.
 */
internal class MainWindowMenuCommands(
    private val hostNavigationService: HostNavigationService,
    private val coroutineScope: CoroutineScope,
    val quit: () -> Unit,
    private val bringMainWindowToFront: () -> Unit,
) {
    var pluginMenuItems: ImmutableList<PluginMenuItem> by mutableStateOf(persistentListOf())
        private set

    /** Lists the drawer's enabled plugins in its order, the ones that need no app first. */
    fun updatePluginMenuItems(drawerPlugins: List<DrawerPluginItemUiState>, hasSelectedApp: Boolean) {
        pluginMenuItems = drawerPlugins
            .filter { it.pluginAvailability == PluginAvailability.Enabled && (!it.needsApp || hasSelectedApp) }
            .sortedBy(DrawerPluginItemUiState::needsApp)
            .mapIndexed { position, plugin -> PluginMenuItem(pluginId = plugin.id, pluginName = plugin.name, shortcut = HostShortcuts.forPluginAt(position)) }
            .toImmutableList()
    }

    fun openSettings() = showInMainWindow(HostNavigationRequest.Settings(HostSettingsSection.GENERAL))

    fun openInfo() = showInMainWindow(HostNavigationRequest.Info)

    fun goHome() = showInMainWindow(HostNavigationRequest.Home)

    fun openLogViewer() = navigate(HostNavigationRequest.LogViewer)

    fun openPlugin(pluginId: String) = showInMainWindow(HostNavigationRequest.Plugin(pluginId, sessionId = null, followsAgent = false))

    /**
     * Runs what [event] is the shortcut of, for a window that has no menu bar to catch its own
     * shortcuts, and says whether it ran anything.
     */
    fun runShortcut(event: KeyEvent): Boolean {
        val pluginMenuItem = pluginMenuItems.firstOrNull { it.shortcut?.matches(event) == true }
        when {
            HostShortcuts.settings.matches(event) -> openSettings()
            HostShortcuts.quit.matches(event) -> quit()
            HostShortcuts.home.matches(event) -> goHome()
            HostShortcuts.logViewer.matches(event) -> openLogViewer()
            pluginMenuItem != null -> openPlugin(pluginMenuItem.pluginId)
            else -> return false
        }
        return true
    }

    private fun showInMainWindow(request: HostNavigationRequest) {
        navigate(request)
        bringMainWindowToFront()
    }

    private fun navigate(request: HostNavigationRequest) {
        coroutineScope.launch { hostNavigationService.navigate(request) }
    }
}

internal data class PluginMenuItem(val pluginId: String, val pluginName: String, val shortcut: HostShortcut?)

/** The main window's menu commands, for the windows that show its menus; null outside the host's windows. */
internal val LocalMainWindowMenuCommands = staticCompositionLocalOf<MainWindowMenuCommands?> { null }

/** The menus every host window shows on macOS: where to go, and which plugin to open. */
// Menus live only inside a window's MenuBar, which a preview has no way to host.
@Suppress("KOTRAIL_COMPOSABLE_WITHOUT_PREVIEW")
@Composable
internal fun MenuBarScope.MainWindowMenus(
    pluginMenuItems: ImmutableList<PluginMenuItem>,
    onGoHome: () -> Unit,
    onOpenLogViewer: () -> Unit,
    onOpenPlugin: (pluginId: String) -> Unit,
) {
    Menu(text = stringResource(Res.string.menu_go)) {
        Item(text = stringResource(Res.string.menu_home), shortcut = HostShortcuts.home.toKeyShortcut(), onClick = onGoHome)
        Item(text = stringResource(Res.string.log_viewer_window_title), shortcut = HostShortcuts.logViewer.toKeyShortcut(), onClick = onOpenLogViewer)
    }
    Menu(text = stringResource(Res.string.plugins)) {
        if (pluginMenuItems.isEmpty()) {
            Item(text = stringResource(Res.string.menu_no_plugins), enabled = false, onClick = {})
        }
        pluginMenuItems.forEach { pluginMenuItem ->
            key(pluginMenuItem.pluginId) {
                Item(text = pluginMenuItem.pluginName, shortcut = pluginMenuItem.shortcut?.toKeyShortcut(), onClick = { onOpenPlugin(pluginMenuItem.pluginId) })
            }
        }
    }
}
