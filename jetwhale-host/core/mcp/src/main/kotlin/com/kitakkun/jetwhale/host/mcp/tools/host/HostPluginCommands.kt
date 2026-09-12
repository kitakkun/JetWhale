package com.kitakkun.jetwhale.host.mcp.tools.host

import com.kitakkun.jetwhale.host.mcp.HostMcpCommand
import com.kitakkun.jetwhale.host.mcp.JetWhaleMcpTool
import com.kitakkun.jetwhale.host.model.DebugSessionRepository
import com.kitakkun.jetwhale.host.model.EnabledPluginsRepository
import com.kitakkun.jetwhale.host.model.McpHostToolGroup
import com.kitakkun.jetwhale.host.model.OfficialPluginCatalog
import com.kitakkun.jetwhale.host.model.OfficialPluginInstallService
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.host.model.PluginInstallProgressRepository
import com.kitakkun.jetwhale.host.model.PluginInstanceEvent
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import com.kitakkun.jetwhale.host.model.PluginTrustService
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginScope
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpResult
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Inject
@ContributesIntoSet(AppScope::class, binding = binding<JetWhaleMcpTool>())
class ListInstalledPluginsCommand(
    private val pluginFactoryRepository: PluginFactoryRepository,
    private val enabledPluginsRepository: EnabledPluginsRepository,
    private val pluginTrustService: PluginTrustService,
) : HostMcpCommand() {
    override val name: String = "jetwhale.listInstalledPlugins"
    override val group: McpHostToolGroup = McpHostToolGroup.OBSERVE
    override val description: String =
        "Host-wide: lists every plugin installed into the debug tool and whether it is enabled, plus the official plugins that could still be installed and any jar that failed to load or is awaiting trust. Use jetwhale.listPlugins instead to see what a particular debug session advertises."

    override suspend fun execute(arguments: JetWhaleMcpArguments): JetWhaleMcpResult {
        val loaded = pluginFactoryRepository.loadedPlugins
        val enabledPluginIds = enabledPluginsRepository.enabledPluginIdsFlow.first()

        val installed = loaded.values.map { plugin ->
            InstalledPluginJson(
                pluginId = plugin.manifest.pluginId,
                name = plugin.manifest.pluginName,
                version = plugin.manifest.version,
                requiresAgent = plugin.manifest.requiresAgent,
                scope = if (plugin.manifest.scope == JetWhaleHostPluginScope.HOST) "host" else "session",
                enabled = plugin.manifest.pluginId in enabledPluginIds,
            )
        }.sortedBy { it.pluginId }

        return JetWhaleMcpResult.text(
            Json.encodeToString(
                ListInstalledPluginsResult(
                    installed = installed,
                    availableOfficial = OfficialPluginCatalog.plugins.map { official ->
                        OfficialPluginJson(
                            pluginId = official.pluginId,
                            displayName = official.displayName,
                            description = official.description,
                            installed = official.pluginId in loaded,
                        )
                    },
                    failedJars = pluginFactoryRepository.failedJarsFlow.first().map { FailedJarJson(it.jarPath, it.reason) },
                    untrustedJars = pluginTrustService.untrustedJarPathsFlow.first(),
                ),
            ),
        )
    }
}

/** How long to wait for reconciliation to instantiate a freshly enabled plugin before reporting what happened. */
private const val INSTANTIATION_TIMEOUT_MILLIS = 2_000L

@Inject
@ContributesIntoSet(AppScope::class, binding = binding<JetWhaleMcpTool>())
class SetPluginEnabledCommand(
    private val pluginFactoryRepository: PluginFactoryRepository,
    private val enabledPluginsRepository: EnabledPluginsRepository,
    private val pluginInstanceService: PluginInstanceService,
    private val debugSessionRepository: DebugSessionRepository,
) : HostMcpCommand() {
    override val name: String = "jetwhale.setPluginEnabled"
    override val group: McpHostToolGroup = McpHostToolGroup.MANAGE_PLUGINS
    override val description: String =
        "Host-wide: enables or disables an installed plugin across the whole debug tool, exactly like the toggle in the plugin drawer. A newly enabled plugin's own MCP tools only become visible after you reconnect to this MCP server."

    private val pluginId by string("The plugin to toggle; from jetwhale.listInstalledPlugins.")
    private val enabled by boolean("True to enable the plugin, false to disable it.")

    override suspend fun execute(arguments: JetWhaleMcpArguments): JetWhaleMcpResult {
        val targetPluginId = arguments[pluginId]
        if (targetPluginId !in pluginFactoryRepository.loadedPlugins) {
            throw JetWhaleMcpArgumentException("invalid pluginId: '$targetPluginId' is not installed. See jetwhale.listInstalledPlugins.")
        }
        val shouldEnable = arguments[enabled]
        // A host-scoped plugin is instantiated whether or not anything is connected, so it is worth
        // waiting for even with no session.
        val isHostScoped = pluginFactoryRepository.loadedPlugins[targetPluginId]?.manifest?.scope == JetWhaleHostPluginScope.HOST

        // Reconciliation runs asynchronously, so collect the Ready events before flipping the flag —
        // otherwise "ok" would be reported before any instance actually exists. With nothing
        // connected and nothing host-scoped there is nothing to instantiate, so skip the wait entirely.
        val hasActiveSession = debugSessionRepository.debugSessionsFlow.firstOrNull().orEmpty().any { it.isActive }
        val instantiatedSessions = mutableSetOf<String>()
        var instantiatedForHost = false
        coroutineScope {
            val collector = launch {
                pluginInstanceService.pluginInstanceEventFlow
                    .filterIsInstance<PluginInstanceEvent.Ready>()
                    .filter { it.pluginId == targetPluginId }
                    // A host-scoped instance reports a null sessionId: it belongs to the host itself.
                    .collect { event -> event.sessionId?.let { instantiatedSessions += it } ?: run { instantiatedForHost = true } }
            }
            enabledPluginsRepository.setPluginEnabled(targetPluginId, shouldEnable)
            if (shouldEnable && (hasActiveSession || isHostScoped)) delay(INSTANTIATION_TIMEOUT_MILLIS)
            collector.cancel()
        }

        return JetWhaleMcpResult.text(
            Json.encodeToString(
                SetPluginEnabledResult(
                    pluginId = targetPluginId,
                    enabled = shouldEnable,
                    instantiatedForSessions = instantiatedSessions.sorted(),
                    instantiatedForHost = instantiatedForHost,
                    reconnectRequiredForNewTools = shouldEnable,
                ),
            ),
        )
    }
}

@Inject
@ContributesIntoSet(AppScope::class, binding = binding<JetWhaleMcpTool>())
class InstallOfficialPluginCommand(
    private val officialPluginInstallService: OfficialPluginInstallService,
    private val pluginFactoryRepository: PluginFactoryRepository,
    private val pluginInstallProgressRepository: PluginInstallProgressRepository,
) : HostMcpCommand() {
    override val name: String = "jetwhale.installOfficialPlugin"
    override val group: McpHostToolGroup = McpHostToolGroup.MANAGE_PLUGINS
    override val description: String =
        "Host-wide: downloads and installs a plugin from JetWhale's official catalog, then enable it with jetwhale.setPluginEnabled. Only catalog plugins can be installed this way, and only when the user has allowed the Manage plugins permission."

    private val pluginId by string("The official plugin to install; from the availableOfficial list of jetwhale.listInstalledPlugins.")

    override suspend fun execute(arguments: JetWhaleMcpArguments): JetWhaleMcpResult {
        // Whether an agent may install at all is the Manage plugins permission, enforced for every
        // tool in the group by McpToolRegistrar before this runs.
        val targetPluginId = arguments[pluginId]
        val plugin = OfficialPluginCatalog.plugins.find { it.pluginId == targetPluginId }
            ?: throw JetWhaleMcpArgumentException(
                "invalid pluginId: '$targetPluginId' is not an official plugin. Only ${OfficialPluginCatalog.plugins.joinToString { it.pluginId }} can be installed over MCP.",
            )
        if (targetPluginId in pluginFactoryRepository.loadedPlugins) {
            return JetWhaleMcpResult.text(
                Json.encodeToString(
                    InstallOfficialPluginResult(
                        pluginId = targetPluginId,
                        installed = true,
                        alreadyInstalled = true,
                        nextStep = "jetwhale.setPluginEnabled",
                    ),
                ),
            )
        }
        // A single in-flight slot is shared with the settings screen's install flow.
        if (pluginInstallProgressRepository.progressFlow.first() != null) {
            throw JetWhaleMcpArgumentException("another plugin installation is already in progress; try again once it finishes")
        }

        withContext(Dispatchers.IO) { officialPluginInstallService.install(plugin) }

        return JetWhaleMcpResult.text(
            Json.encodeToString(
                InstallOfficialPluginResult(
                    pluginId = targetPluginId,
                    installed = true,
                    alreadyInstalled = false,
                    nextStep = "jetwhale.setPluginEnabled",
                ),
            ),
        )
    }
}

@Serializable
data class SetPluginEnabledResult(
    val pluginId: String,
    val enabled: Boolean,
    val instantiatedForSessions: List<String>,
    /** True when this call brought up the single instance of a host-scoped plugin. */
    val instantiatedForHost: Boolean,
    val reconnectRequiredForNewTools: Boolean,
)

@Serializable
data class InstallOfficialPluginResult(
    val pluginId: String,
    val installed: Boolean,
    val alreadyInstalled: Boolean,
    val nextStep: String,
    val enabled: Boolean = false,
    val reconnectRequiredForNewTools: Boolean = true,
)

@Serializable
data class ListInstalledPluginsResult(
    val installed: List<InstalledPluginJson>,
    val availableOfficial: List<OfficialPluginJson>,
    val failedJars: List<FailedJarJson>,
    val untrustedJars: List<String>,
)

@Serializable
data class InstalledPluginJson(
    val pluginId: String,
    val name: String,
    val version: String,
    val requiresAgent: Boolean,
    /** "session" for one instance per debug session, "host" for a single host-wide instance. */
    val scope: String,
    val enabled: Boolean,
)

@Serializable
data class OfficialPluginJson(
    val pluginId: String,
    val displayName: String,
    val description: String,
    val installed: Boolean,
)

@Serializable
data class FailedJarJson(
    val jarPath: String,
    val reason: String,
)
