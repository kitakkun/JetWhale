package com.kitakkun.jetwhale.host.mcp.tools.host

import com.kitakkun.jetwhale.host.mcp.HostMcpCommand
import com.kitakkun.jetwhale.host.mcp.JetWhaleMcpTool
import com.kitakkun.jetwhale.host.model.DebugSessionRepository
import com.kitakkun.jetwhale.host.model.DebuggerSettingsRepository
import com.kitakkun.jetwhale.host.model.EnabledPluginsRepository
import com.kitakkun.jetwhale.host.model.OfficialPluginCatalog
import com.kitakkun.jetwhale.host.model.OfficialPluginInstallService
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.host.model.PluginInstallProgressRepository
import com.kitakkun.jetwhale.host.model.PluginInstanceEvent
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import com.kitakkun.jetwhale.host.model.PluginTrustService
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
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
    override val description: String =
        "Host-wide: lists every plugin installed into the debug tool and whether it is enabled, plus the official plugins that could still be installed and any jar that failed to load or is awaiting trust. Use jetwhale.listPlugins instead to see what a particular debug session advertises."

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val loaded = pluginFactoryRepository.loadedPlugins
        val enabledPluginIds = enabledPluginsRepository.enabledPluginIdsFlow.first()

        val installed = loaded.values.map { plugin ->
            InstalledPluginJson(
                pluginId = plugin.manifest.pluginId,
                name = plugin.manifest.pluginName,
                version = plugin.manifest.version,
                requiresAgent = plugin.manifest.requiresAgent,
                enabled = plugin.manifest.pluginId in enabledPluginIds,
            )
        }.sortedBy { it.pluginId }

        return Json.encodeToString(
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
    override val description: String =
        "Host-wide: enables or disables an installed plugin across the whole debug tool, exactly like the toggle in the plugin drawer. A newly enabled plugin's own MCP tools only become visible after you reconnect to this MCP server."

    private val pluginId by string("The plugin to toggle; from jetwhale.listInstalledPlugins.")
    private val enabled by boolean("True to enable the plugin, false to disable it.")

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val targetPluginId = arguments[pluginId]
        if (targetPluginId !in pluginFactoryRepository.loadedPlugins) {
            throw JetWhaleMcpArgumentException("invalid pluginId: '$targetPluginId' is not installed. See jetwhale.listInstalledPlugins.")
        }
        val shouldEnable = arguments[enabled]

        // Reconciliation runs asynchronously, so collect the Ready events before flipping the flag —
        // otherwise "ok" would be reported before any instance actually exists. With nothing
        // connected there is nothing to instantiate, so skip the wait entirely.
        val hasActiveSession = debugSessionRepository.debugSessionsFlow.firstOrNull().orEmpty().any { it.isActive }
        val instantiatedSessions = mutableSetOf<String>()
        coroutineScope {
            val collector = launch {
                pluginInstanceService.pluginInstanceEventFlow
                    .filterIsInstance<PluginInstanceEvent.Ready>()
                    .filter { it.pluginId == targetPluginId }
                    .collect { instantiatedSessions += it.sessionId }
            }
            enabledPluginsRepository.setPluginEnabled(targetPluginId, shouldEnable)
            if (shouldEnable && hasActiveSession) delay(INSTANTIATION_TIMEOUT_MILLIS)
            collector.cancel()
        }

        return Json.encodeToString(
            SetPluginEnabledResult(
                pluginId = targetPluginId,
                enabled = shouldEnable,
                instantiatedForSessions = instantiatedSessions.sorted(),
                reconnectRequiredForNewTools = shouldEnable,
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
    private val settingsRepository: DebuggerSettingsRepository,
) : HostMcpCommand() {
    override val name: String = "jetwhale.installOfficialPlugin"
    override val description: String =
        "Host-wide: downloads and installs a plugin from JetWhale's official catalog, then enable it with jetwhale.setPluginEnabled. Only catalog plugins can be installed this way, and only when the user has allowed it in Settings → Server → MCP Server."

    private val pluginId by string("The official plugin to install; from the availableOfficial list of jetwhale.listInstalledPlugins.")

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        if (!settingsRepository.mcpPluginInstallAllowedFlow.value) {
            // Listed but refused on purpose: an agent that can read the reason can tell the user
            // which switch to flip, where a hidden tool would only produce confusion.
            throw JetWhaleMcpArgumentException(
                "plugin installation over MCP is disabled. Ask the user to enable it in Settings → Server → MCP Server.",
            )
        }

        val targetPluginId = arguments[pluginId]
        val plugin = OfficialPluginCatalog.plugins.find { it.pluginId == targetPluginId }
            ?: throw JetWhaleMcpArgumentException(
                "invalid pluginId: '$targetPluginId' is not an official plugin. Only ${OfficialPluginCatalog.plugins.joinToString { it.pluginId }} can be installed over MCP.",
            )
        if (targetPluginId in pluginFactoryRepository.loadedPlugins) {
            return Json.encodeToString(
                InstallOfficialPluginResult(
                    pluginId = targetPluginId,
                    installed = true,
                    alreadyInstalled = true,
                    nextStep = "jetwhale.setPluginEnabled",
                ),
            )
        }
        // A single in-flight slot is shared with the settings screen's install flow.
        if (pluginInstallProgressRepository.progressFlow.first() != null) {
            throw JetWhaleMcpArgumentException("another plugin installation is already in progress; try again once it finishes")
        }

        withContext(Dispatchers.IO) { officialPluginInstallService.install(plugin) }

        return Json.encodeToString(
            InstallOfficialPluginResult(
                pluginId = targetPluginId,
                installed = true,
                alreadyInstalled = false,
                nextStep = "jetwhale.setPluginEnabled",
            ),
        )
    }
}

@Serializable
data class SetPluginEnabledResult(
    val pluginId: String,
    val enabled: Boolean,
    val instantiatedForSessions: List<String>,
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
