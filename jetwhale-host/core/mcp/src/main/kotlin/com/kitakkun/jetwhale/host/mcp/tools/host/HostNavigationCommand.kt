package com.kitakkun.jetwhale.host.mcp.tools.host

import com.kitakkun.jetwhale.host.mcp.HostMcpCommand
import com.kitakkun.jetwhale.host.mcp.JetWhaleMcpTool
import com.kitakkun.jetwhale.host.model.DebugSessionRepository
import com.kitakkun.jetwhale.host.model.EnabledPluginsRepository
import com.kitakkun.jetwhale.host.model.HostDestination
import com.kitakkun.jetwhale.host.model.HostDestinationKind
import com.kitakkun.jetwhale.host.model.HostMcpToolsTab
import com.kitakkun.jetwhale.host.model.HostNavigationRequest
import com.kitakkun.jetwhale.host.model.HostNavigationService
import com.kitakkun.jetwhale.host.model.HostSession
import com.kitakkun.jetwhale.host.model.HostSettingsPage
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

enum class NavigationDestination { HOME, PLUGIN, SETTINGS, INFO, LOG_VIEWER, MCP_TOOLS }

@Inject
@ContributesIntoSet(AppScope::class, binding = binding<JetWhaleMcpTool>())
class HostNavigationCommand(
    private val hostNavigationService: HostNavigationService,
    private val debugSessionRepository: DebugSessionRepository,
    private val pluginFactoryRepository: PluginFactoryRepository,
    private val enabledPluginsRepository: EnabledPluginsRepository,
    private val reconciliationService: PluginSessionReconciliationService,
) : HostMcpCommand() {
    override val name: String = "jetwhale.navigate"
    override val group: McpHostToolGroup = McpHostToolGroup.NAVIGATE
    override val description: String =
        "Host-wide: switches the main JetWhale window to another screen. Navigating to PLUGIN also selects that session in the drawer, which is what jetwhale.screenshot of the same plugin will then show. The other screens, such as a Settings page or the MCP tools browser, are for the person at the window: jetwhale.screenshot captures plugins only."

    private val destination by enum("Which screen to show.", NavigationDestination.entries)
    private val pluginId by stringOrNull("Required when destination is PLUGIN; from jetwhale.listInstalledPlugins.")
    private val sessionId by stringOrNull(
        "Only for PLUGIN. Defaults to the session already selected in the drawer. A plugin that needs no app always opens in \"${HostSession.ID}\".",
    )
    private val settingsSection by enumOrNull("Only for SETTINGS: opens the first page of that section. Defaults to GENERAL.", HostSettingsSection.entries)
    private val settingsPage by enumOrNull(
        "Only for SETTINGS: the page to open, named as the settings menu labels it. Takes the place of settingsSection; if both are given, the page has to be in that section.",
        HostSettingsPage.entries,
    )
    private val mcpToolsTab by enumOrNull("Only for MCP_TOOLS: the tab to open. Defaults to TOOLS.", HostMcpToolsTab.entries)

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val request = arguments.toRequest()
        hostNavigationService.navigate(request)

        val applied = withTimeoutOrNull(CONFIRMATION_TIMEOUT_MILLIS) {
            hostNavigationService.currentView.filterNotNull().first { request.matches(it.destination) }
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
                settingsSection = applied.settingsPage?.section?.name,
                settingsPage = applied.settingsPage?.name,
                mcpToolsTab = applied.mcpToolsTab?.name,
                poppedOut = applied.poppedOutPlugins.any { it.pluginId == applied.pluginId && it.sessionId == applied.sessionId },
            ),
        )
    }

    private suspend fun JetWhaleMcpArguments.toRequest(): HostNavigationRequest = when (this[destination]) {
        NavigationDestination.HOME -> HostNavigationRequest.Home

        NavigationDestination.INFO -> HostNavigationRequest.Info

        NavigationDestination.LOG_VIEWER -> HostNavigationRequest.LogViewer

        NavigationDestination.SETTINGS -> {
            val section = this[settingsSection]
            val page = this[settingsPage] ?: (section ?: HostSettingsSection.GENERAL).firstPage
            if (section != null && page.section != section) {
                throw JetWhaleMcpArgumentException("invalid settingsPage: ${page.name} is in ${page.section.name}, not ${section.name}. Pass settingsPage alone to open it.")
            }
            HostNavigationRequest.Settings(page)
        }

        NavigationDestination.MCP_TOOLS -> HostNavigationRequest.McpTools(this[mcpToolsTab] ?: HostMcpToolsTab.TOOLS)

        NavigationDestination.PLUGIN -> {
            val targetPluginId = this[pluginId]
                ?: throw JetWhaleMcpArgumentException("missing required argument: pluginId is required when destination is PLUGIN")
            validatePlugin(targetPluginId, this[sessionId])
            val targetSessionId = if (reconciliationService.requiresAgent(targetPluginId)) this[sessionId] else HostSession.ID
            HostNavigationRequest.Plugin(targetPluginId, targetSessionId, followsAgent = false)
        }
    }

    /** Turns the ways a plugin screen can silently fail to open into distinct errors. */
    private suspend fun validatePlugin(targetPluginId: String, targetSessionId: String?) {
        if (targetPluginId !in pluginFactoryRepository.loadedPlugins) {
            throw JetWhaleMcpArgumentException("invalid pluginId: '$targetPluginId' is not installed. See jetwhale.listInstalledPlugins.")
        }
        if (targetPluginId !in enabledPluginsRepository.enabledPluginIdsFlow.first()) {
            throw JetWhaleMcpArgumentException("invalid pluginId: '$targetPluginId' is installed but disabled. Enable it with jetwhale.setPluginEnabled.")
        }
        if (targetSessionId == null) return

        if (!reconciliationService.requiresAgent(targetPluginId)) return
        if (HostSession.isHost(targetSessionId)) {
            throw JetWhaleMcpArgumentException("invalid sessionId: '$targetPluginId' needs an app; pass the id of an app session from jetwhale.listSessions.")
        }
        val session = debugSessionRepository.debugSessionsFlow.firstOrNull()?.find { it.id == targetSessionId }
            ?: throw JetWhaleMcpArgumentException("invalid sessionId: no session '$targetSessionId'. See jetwhale.listSessions.")
        if (!session.isActive) {
            throw JetWhaleMcpArgumentException("invalid sessionId: session '$targetSessionId' is disconnected. Pick one with isActive: true from jetwhale.listSessions.")
        }
        if (session.installedPlugins.none { it.pluginId == targetPluginId }) {
            throw JetWhaleMcpArgumentException("invalid sessionId: session '$targetSessionId' does not have '$targetPluginId' installed on its agent.")
        }
    }
}

private fun HostNavigationRequest.matches(destination: HostDestination): Boolean = when (this) {
    is HostNavigationRequest.Home -> destination.kind == HostDestinationKind.HOME

    is HostNavigationRequest.Info -> destination.kind == HostDestinationKind.INFO

    is HostNavigationRequest.LogViewer -> destination.kind == HostDestinationKind.LOG_VIEWER

    is HostNavigationRequest.Settings -> destination.kind == HostDestinationKind.SETTINGS && destination.settingsPage == page

    is HostNavigationRequest.McpTools ->
        destination.kind == HostDestinationKind.MCP_TOOLS &&
            destination.mcpToolsTab == tab &&
            destination.pluginId == null &&
            destination.sessionId == null

    is HostNavigationRequest.Plugin ->
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
    val settingsPage: String? = null,
    val mcpToolsTab: String? = null,
    val poppedOut: Boolean = false,
    val reason: String? = null,
)
