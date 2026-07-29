package com.kitakkun.jetwhale.host.mcp.tools.host

import com.kitakkun.jetwhale.host.mcp.HostMcpCommand
import com.kitakkun.jetwhale.host.mcp.JetWhaleMcpTool
import com.kitakkun.jetwhale.host.mcp.McpServerStatusHolder
import com.kitakkun.jetwhale.host.model.DebugSessionRepository
import com.kitakkun.jetwhale.host.model.DebugWebSocketServer
import com.kitakkun.jetwhale.host.model.DebugWebSocketServerStatus
import com.kitakkun.jetwhale.host.model.DebuggerSettingsRepository
import com.kitakkun.jetwhale.host.model.EnabledPluginsRepository
import com.kitakkun.jetwhale.host.model.HostNavigationService
import com.kitakkun.jetwhale.host.model.HostOs
import com.kitakkun.jetwhale.host.model.HostVersionInfo
import com.kitakkun.jetwhale.host.model.HostViewState
import com.kitakkun.jetwhale.host.model.McpHostToolGroup
import com.kitakkun.jetwhale.host.model.McpPermissions
import com.kitakkun.jetwhale.host.model.McpPermissionsRepository
import com.kitakkun.jetwhale.host.model.McpServerStatus
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.host.model.PluginInstallProgressRepository
import com.kitakkun.jetwhale.host.model.PluginTrustService
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Inject
@ContributesIntoSet(AppScope::class, binding = binding<JetWhaleMcpTool>())
class HostStatusCommand(
    private val hostVersionInfo: HostVersionInfo,
    private val debugWebSocketServer: DebugWebSocketServer,
    private val mcpServerStatusHolder: McpServerStatusHolder,
    private val debugSessionRepository: DebugSessionRepository,
    private val pluginFactoryRepository: PluginFactoryRepository,
    private val enabledPluginsRepository: EnabledPluginsRepository,
    private val pluginTrustService: PluginTrustService,
    private val pluginInstallProgressRepository: PluginInstallProgressRepository,
    private val settingsRepository: DebuggerSettingsRepository,
    private val mcpPermissionsRepository: McpPermissionsRepository,
    private val hostNavigationService: HostNavigationService,
) : HostMcpCommand() {
    override val name: String = "jetwhale.getStatus"
    override val group: McpHostToolGroup = McpHostToolGroup.OBSERVE
    override val description: String =
        "Host-wide: one snapshot of the debug tool — its version, both servers, how many sessions and plugins are live, and the current settings. Call this first to orient yourself before any other jetwhale tool."

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val sessions = debugSessionRepository.debugSessionsFlow.firstOrNull().orEmpty()

        return Json.encodeToString(
            HostStatusResult(
                host = HostInfoJson(
                    version = hostVersionInfo.version,
                    isSnapshot = hostVersionInfo.isSnapshot,
                    os = HostOs.current.name,
                ),
                debugServer = debugWebSocketServer.statusFlow.value.toJson(),
                mcpServer = mcpServerStatusHolder.statusFlow.value.toJson(),
                sessions = SessionCountsJson(
                    total = sessions.size,
                    active = sessions.count { it.isActive },
                ),
                plugins = PluginCountsJson(
                    loaded = pluginFactoryRepository.loadedPlugins.size,
                    enabled = enabledPluginsRepository.enabledPluginIdsFlow.first().size,
                    failedJars = pluginFactoryRepository.failedJarsFlow.first().size,
                    untrustedJars = pluginTrustService.untrustedJarPathsFlow.first().size,
                    installInProgress = pluginInstallProgressRepository.progressFlow.first() != null,
                ),
                settings = SettingsJson(
                    serverPort = settingsRepository.serverPortFlow.value,
                    wssPort = settingsRepository.wssPortFlow.value,
                    wssEnabled = settingsRepository.wssEnabledFlow.value,
                    mcpServerPort = settingsRepository.mcpServerPortFlow.value,
                    adbAutoPortMappingEnabled = settingsRepository.adbAutoPortMappingEnabledFlow.value,
                    checkForUpdatesOnStartup = settingsRepository.checkForUpdatesOnStartupFlow.value,
                    persistData = settingsRepository.persistDataFlow.value,
                ),
                permissions = mcpPermissionsRepository.permissionsFlow.value.toJson(),
                ui = hostNavigationService.currentView.value?.toJson(),
            ),
        )
    }
}

private fun McpPermissions.toJson() = PermissionsJson(
    allowedHostGroups = allowedHostGroups.map { it.name }.sorted(),
    deniedHostGroups = McpHostToolGroup.entries.filterNot { it in allowedHostGroups }.map { it.name },
    pluginsWithUiDenied = pluginsDeniedUi.sorted(),
    pluginsWithOwnToolsDenied = pluginsDeniedOwnTools.sorted(),
)

private fun HostViewState.toJson() = UiStateJson(
    destination = destination.kind.name,
    pluginId = destination.pluginId,
    sessionId = destination.sessionId,
    settingsSection = destination.settingsSection?.name,
    poppedOutPlugins = destination.poppedOutPlugins.map { PoppedOutPluginJson(it.pluginId, it.sessionId) },
    selectedSessionId = selectedSessionId,
    selectedPluginId = selectedPluginId,
)

internal fun DebugWebSocketServerStatus.toJson(): ServerStateJson = when (this) {
    is DebugWebSocketServerStatus.Started -> ServerStateJson("Started", host = host, port = port, wssPort = wssPort)
    is DebugWebSocketServerStatus.Error -> ServerStateJson("Error", message = message)
    DebugWebSocketServerStatus.Starting -> ServerStateJson("Starting")
    DebugWebSocketServerStatus.Stopping -> ServerStateJson("Stopping")
    DebugWebSocketServerStatus.Stopped -> ServerStateJson("Stopped")
}

private fun McpServerStatus.toJson(): ServerStateJson = when (this) {
    is McpServerStatus.Running -> ServerStateJson("Running", host = host, port = port)
    is McpServerStatus.Error -> ServerStateJson("Error", message = message)
    McpServerStatus.Starting -> ServerStateJson("Starting")
    McpServerStatus.Stopping -> ServerStateJson("Stopping")
    McpServerStatus.Stopped -> ServerStateJson("Stopped")
}

@Serializable
data class HostStatusResult(
    val host: HostInfoJson,
    val debugServer: ServerStateJson,
    val mcpServer: ServerStateJson,
    val sessions: SessionCountsJson,
    val plugins: PluginCountsJson,
    val settings: SettingsJson,
    val permissions: PermissionsJson,
    /** Null until the host window has composed. */
    val ui: UiStateJson? = null,
)

/**
 * What this agent is allowed to do. Reported so a refusal can be anticipated — and explained to the
 * user — rather than only discovered by calling a tool and being turned away.
 */
@Serializable
data class PermissionsJson(
    val allowedHostGroups: List<String>,
    val deniedHostGroups: List<String>,
    val pluginsWithUiDenied: List<String>,
    val pluginsWithOwnToolsDenied: List<String>,
    val changeableIn: String = "Settings → Server → MCP Server → Permissions",
)

@Serializable
data class UiStateJson(
    val destination: String,
    val pluginId: String? = null,
    val sessionId: String? = null,
    val settingsSection: String? = null,
    val poppedOutPlugins: List<PoppedOutPluginJson> = emptyList(),
    val selectedSessionId: String? = null,
    val selectedPluginId: String? = null,
)

@Serializable
data class PoppedOutPluginJson(
    val pluginId: String,
    val sessionId: String,
)

@Serializable
data class HostInfoJson(
    val version: String,
    val isSnapshot: Boolean,
    val os: String,
)

@Serializable
data class ServerStateJson(
    val state: String,
    val host: String? = null,
    val port: Int? = null,
    val wssPort: Int? = null,
    val message: String? = null,
)

@Serializable
data class SessionCountsJson(
    val total: Int,
    val active: Int,
)

@Serializable
data class PluginCountsJson(
    val loaded: Int,
    val enabled: Int,
    val failedJars: Int,
    val untrustedJars: Int,
    val installInProgress: Boolean,
)

@Serializable
data class SettingsJson(
    val serverPort: Int,
    val wssPort: Int,
    val wssEnabled: Boolean,
    val mcpServerPort: Int,
    val adbAutoPortMappingEnabled: Boolean,
    val checkForUpdatesOnStartup: Boolean,
    val persistData: Boolean,
)
