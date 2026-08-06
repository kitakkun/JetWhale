package com.kitakkun.jetwhale.host.mcp.tools.host

import com.kitakkun.jetwhale.host.mcp.HostMcpCommand
import com.kitakkun.jetwhale.host.mcp.JetWhaleMcpTool
import com.kitakkun.jetwhale.host.model.DebugSessionRepository
import com.kitakkun.jetwhale.host.model.EnabledPluginsRepository
import com.kitakkun.jetwhale.host.model.HostDestination
import com.kitakkun.jetwhale.host.model.HostDestinationKind
import com.kitakkun.jetwhale.host.model.HostNavigator
import com.kitakkun.jetwhale.host.model.HostSettingsSection
import com.kitakkun.jetwhale.host.model.McpHostToolGroup
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.host.model.PluginSessionReconciliationService
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** How long to wait for the window to report that it applied the request before giving up on confirming it. */
private const val CONFIRMATION_TIMEOUT_MILLIS = 2_000L

enum class NavigationDestination { HOME, PLUGIN, SETTINGS, INFO, LOG_VIEWER }

/** The screen this call asked for, kept so the reported destination can be checked against it. */
private sealed interface RequestedScreen {
    data object Home : RequestedScreen
    data object Info : RequestedScreen
    data object LogViewer : RequestedScreen
    data class Settings(val section: HostSettingsSection) : RequestedScreen
    data class Plugin(val pluginId: String, val sessionId: String?) : RequestedScreen
}

@Inject
@ContributesIntoSet(AppScope::class, binding = binding<JetWhaleMcpTool>())
class HostNavigationCommand(
    private val hostNavigator: HostNavigator,
    private val debugSessionRepository: DebugSessionRepository,
    private val pluginFactoryRepository: PluginFactoryRepository,
    private val enabledPluginsRepository: EnabledPluginsRepository,
    private val reconciliationService: PluginSessionReconciliationService,
) : HostMcpCommand() {
    override val name: String = "jetwhale.navigate"
    override val group: McpHostToolGroup = McpHostToolGroup.NAVIGATE
    override val description: String =
        "Host-wide: switches the main JetWhale window to another screen. Navigating to PLUGIN also selects that session in the drawer, which is what jetwhale.screenshot of the same plugin will then show."

    private val destination by enum("Which screen to show.", NavigationDestination.entries)
    private val pluginId by stringOrNull("Required when destination is PLUGIN; from jetwhale.listInstalledPlugins.")
    private val sessionId by stringOrNull("Only for PLUGIN. Defaults to the session already selected in the drawer.")
    private val settingsSection by enumOrNull("Only for SETTINGS. Defaults to GENERAL.", HostSettingsSection.entries)

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val requested = arguments.toRequestedScreen()
        requested.navigate()

        // Report what the window actually shows rather than assuming the request landed: the drawer
        // resets its selection when the selected session goes inactive, and the window may not have
        // composed yet.
        val applied = withTimeoutOrNull(CONFIRMATION_TIMEOUT_MILLIS) {
            hostNavigator.currentView.filterNotNull().first { requested.matches(it.destination) }
        }?.destination
            ?: return Json.encodeToString(
                NavigateResult(
                    applied = false,
                    reason = "The host window did not report the requested destination within $CONFIRMATION_TIMEOUT_MILLIS ms. It may still be starting up.",
                ),
            )

        return Json.encodeToString(
            NavigateResult(
                applied = true,
                destination = applied.kind.name,
                pluginId = applied.pluginId,
                sessionId = applied.sessionId,
                settingsSection = applied.settingsSection?.name,
                poppedOut = applied.poppedOutPlugins.any { it.pluginId == applied.pluginId && it.sessionId == applied.sessionId },
            ),
        )
    }

    private fun RequestedScreen.navigate() = when (this) {
        RequestedScreen.Home -> hostNavigator.navigateHome()
        RequestedScreen.Info -> hostNavigator.navigateToInfo()
        RequestedScreen.LogViewer -> hostNavigator.navigateToLogViewer()
        is RequestedScreen.Settings -> hostNavigator.navigateToSettings(section)
        is RequestedScreen.Plugin -> hostNavigator.navigateToPlugin(pluginId, sessionId)
    }

    private suspend fun JetWhaleMcpArguments.toRequestedScreen(): RequestedScreen = when (this[destination]) {
        NavigationDestination.HOME -> RequestedScreen.Home

        NavigationDestination.INFO -> RequestedScreen.Info

        NavigationDestination.LOG_VIEWER -> RequestedScreen.LogViewer

        NavigationDestination.SETTINGS -> RequestedScreen.Settings(this[settingsSection] ?: HostSettingsSection.GENERAL)

        NavigationDestination.PLUGIN -> {
            val targetPluginId = this[pluginId]
                ?: throw JetWhaleMcpArgumentException("missing required argument: pluginId is required when destination is PLUGIN")
            validatePlugin(targetPluginId, this[sessionId])
            RequestedScreen.Plugin(targetPluginId, this[sessionId])
        }
    }

    /** Turns the three ways a plugin screen can silently fail to open into three distinct errors. */
    private suspend fun validatePlugin(targetPluginId: String, targetSessionId: String?) {
        if (targetPluginId !in pluginFactoryRepository.loadedPlugins) {
            throw JetWhaleMcpArgumentException("invalid pluginId: '$targetPluginId' is not installed. See jetwhale.listInstalledPlugins.")
        }
        if (targetPluginId !in enabledPluginsRepository.enabledPluginIdsFlow.first()) {
            throw JetWhaleMcpArgumentException("invalid pluginId: '$targetPluginId' is installed but disabled. Enable it with jetwhale.setPluginEnabled.")
        }
        if (targetSessionId == null) return

        val session = debugSessionRepository.debugSessionsFlow.firstOrNull()?.find { it.id == targetSessionId }
            ?: throw JetWhaleMcpArgumentException("invalid sessionId: no session '$targetSessionId'. See jetwhale.listSessions.")
        if (reconciliationService.requiresAgent(targetPluginId) && session.installedPlugins.none { it.pluginId == targetPluginId }) {
            throw JetWhaleMcpArgumentException("invalid sessionId: session '$targetSessionId' does not have '$targetPluginId' installed on its agent.")
        }
    }
}

private fun RequestedScreen.matches(destination: HostDestination): Boolean = when (this) {
    RequestedScreen.Home -> destination.kind == HostDestinationKind.HOME

    RequestedScreen.Info -> destination.kind == HostDestinationKind.INFO

    RequestedScreen.LogViewer -> destination.kind == HostDestinationKind.LOG_VIEWER

    is RequestedScreen.Settings -> destination.kind == HostDestinationKind.SETTINGS && destination.settingsSection == section

    is RequestedScreen.Plugin ->
        destination.kind == HostDestinationKind.PLUGIN &&
            destination.pluginId == pluginId &&
            (sessionId == null || destination.sessionId == sessionId)
}

@Serializable
data class NavigateResult(
    val applied: Boolean,
    val destination: String? = null,
    val pluginId: String? = null,
    val sessionId: String? = null,
    val settingsSection: String? = null,
    val poppedOut: Boolean = false,
    val reason: String? = null,
)
