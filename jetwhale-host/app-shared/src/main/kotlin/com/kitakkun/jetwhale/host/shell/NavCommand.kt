package com.kitakkun.jetwhale.host.shell

import androidx.navigation3.runtime.NavKey
import com.kitakkun.jetwhale.host.plugin.PluginNavKey
import com.kitakkun.jetwhale.host.plugin.PluginPopoutNavKey
import kotlin.reflect.KClass

/**
 * One navigation intent, as sent by a navigator and applied to the back stack by the navigation
 * host.
 *
 * This is the transport behind the screen-specific navigator interfaces: screens describe what they
 * want in their own vocabulary, and it arrives here as one of these. Every mutation the back stack
 * can undergo is named by a command — there is deliberately no "run this lambda on the back stack"
 * escape hatch, so the whole set of mutations stays readable in one place.
 */
sealed interface NavCommand {
    /** Shows [key], moving it to the top if it is already somewhere on the stack. */
    data class ShowSingleTop(val key: NavKey) : NavCommand

    /**
     * Shows [key] at [index] instead of on top, so a screen can open another one underneath itself
     * (the settings dialog opening the log viewer window, say).
     */
    data class ShowSingleTopAt(val index: Int, val key: NavKey) : NavCommand

    data object PopTop : NavCommand

    /** Closes every entry of [type], however many of them are on the stack. */
    data class CloseAllOfType(val type: KClass<out NavKey>) : NavCommand

    /** Closes the window whose entry has this content key; sent when the window itself is dismissed. */
    data class CloseWindow(val contentKey: Any) : NavCommand

    /** Returns the main window to the home screen, leaving popout windows open. */
    data object GoHome : NavCommand

    /**
     * Shows the MCP tools browser, seeded with the scope it was opened from.
     *
     * At most one browser window exists: opening it from a different scope re-seeds the filters
     * rather than stacking a second window, and re-opening it with the same scope leaves the window
     * as it is so the user does not lose its position or their own filter changes.
     */
    data class OpenMcpTools(val pluginId: String?, val sessionId: String?) : NavCommand

    /** Docks a popped-out plugin: shows it in the main window and closes its popout window. */
    data class BringPluginBackToMainWindow(val pluginId: String, val sessionId: String) : NavCommand

    /**
     * Makes the plugin screen currently on top of the back stack follow a session switch.
     *
     * If the top entry is a [PluginNavKey] targeting a different session, it is replaced with one
     * for [newSessionId] so the same plugin is shown for the newly-selected session. A plugin that
     * is not in [availablePluginIds] is popped instead, so the screen underneath is shown rather
     * than a dead plugin screen.
     */
    data class FollowPluginToSession(
        val newSessionId: String,
        val availablePluginIds: Set<String>,
    ) : NavCommand

    data object CloseAllPluginScreens : NavCommand

    data class ClosePluginScreensForSession(val sessionId: String) : NavCommand

    data class ClosePluginScreensForPlugin(val pluginId: String) : NavCommand
}

/**
 * Applies [command] to the back stack.
 *
 * Defined over a plain [MutableList] so the composite semantics can be exercised without a
 * composition; the navigation host calls it with the real `NavBackStack`.
 */
fun MutableList<NavKey>.applyNavCommand(command: NavCommand) {
    when (command) {
        is NavCommand.ShowSingleTop -> {
            removeIf { it == command.key }
            add(command.key)
        }

        is NavCommand.ShowSingleTopAt -> {
            removeIf { it == command.key }
            add(command.index, command.key)
        }

        NavCommand.PopTop -> {
            removeLastOrNull()
        }

        is NavCommand.CloseAllOfType -> {
            removeAll { command.type.isInstance(it) }
        }

        is NavCommand.CloseWindow -> {
            removeAll { it.toString() == command.contentKey.toString() }
        }

        NavCommand.GoHome -> {
            // Popouts live in their own windows; going home in the main window must not close them.
            removeAll { it !is EmptyPluginNavKey && it !is PluginPopoutNavKey }
        }

        is NavCommand.OpenMcpTools -> {
            val navKey = McpToolsNavKey(pluginId = command.pluginId, sessionId = command.sessionId)
            if (none { it == navKey }) {
                removeAll { it is McpToolsNavKey }
                add(navKey)
            }
        }

        is NavCommand.BringPluginBackToMainWindow -> {
            applyNavCommand(
                NavCommand.ShowSingleTop(
                    PluginNavKey(
                        pluginId = command.pluginId,
                        sessionId = command.sessionId,
                    ),
                ),
            )
            removeAll {
                it is PluginPopoutNavKey &&
                    it.pluginId == command.pluginId &&
                    it.sessionId == command.sessionId
            }
        }

        is NavCommand.FollowPluginToSession -> {
            val top = lastOrNull() as? PluginNavKey ?: return
            if (top.sessionId == command.newSessionId) return

            removeLastOrNull()
            if (top.pluginId in command.availablePluginIds) {
                // Plain add (not single-top): we only replace the top entry, so earlier entries for
                // the same plugin/session deeper in the back stack must be left intact.
                add(PluginNavKey(pluginId = top.pluginId, sessionId = command.newSessionId))
            }
        }

        NavCommand.CloseAllPluginScreens -> {
            removeAll { it is PluginNavKey || it is PluginPopoutNavKey }
        }

        is NavCommand.ClosePluginScreensForSession -> {
            removeAll { it is PluginNavKey && it.sessionId == command.sessionId }
        }

        is NavCommand.ClosePluginScreensForPlugin -> {
            removeAll {
                when (it) {
                    is PluginNavKey -> it.pluginId == command.pluginId
                    is PluginPopoutNavKey -> it.pluginId == command.pluginId
                    else -> false
                }
            }
        }
    }
}
