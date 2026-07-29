package com.kitakkun.jetwhale.host.data.settings

import com.kitakkun.jetwhale.host.mcp.McpServerService
import com.kitakkun.jetwhale.host.model.McpCapablePlugins
import com.kitakkun.jetwhale.host.model.McpPermissionPlugin
import com.kitakkun.jetwhale.host.model.McpPermissionsRepository
import com.kitakkun.jetwhale.host.model.McpPermissionsView
import com.kitakkun.jetwhale.host.model.McpPermissionsViewSubscriptionKey
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.combine
import soil.query.SubscriptionId
import soil.query.buildSubscriptionKey

@Inject
@ContributesBinding(AppScope::class)
class DefaultMcpPermissionsViewSubscriptionKey(
    private val permissionsRepository: McpPermissionsRepository,
    private val pluginFactoryRepository: PluginFactoryRepository,
    private val mcpServerService: McpServerService,
) : McpPermissionsViewSubscriptionKey by buildSubscriptionKey(
    id = SubscriptionId("mcp_permissions_view"),
    subscribe = {
        combine(
            permissionsRepository.permissionsFlow,
            pluginFactoryRepository.loadedPluginsFlow,
            mcpServerService.mcpCapablePluginsFlow,
        ) { permissions, loadedPlugins, capablePlugins ->
            McpPermissionsView(
                permissions = permissions,
                // Every installed plugin is listed, whether or not it currently publishes tools:
                // Inspect and Interact apply to any plugin's UI, so a plugin with no live instance
                // still has something to decide about.
                plugins = loadedPlugins.values
                    .map { plugin ->
                        McpPermissionPlugin(
                            pluginId = plugin.manifest.pluginId,
                            displayName = plugin.manifest.pluginName,
                            tools = capablePlugins.toolsForAnySession(plugin.manifest.pluginId),
                        )
                    }
                    .sortedBy { it.displayName },
            )
        }
    },
)

/**
 * The tools this plugin publishes, in any live session.
 *
 * A plugin publishes the same command list for every session it is instantiated in, so the first
 * session that has it is as good as any; taking the union would only duplicate.
 */
private fun McpCapablePlugins.toolsForAnySession(pluginId: String) = toolsBySessionAndPlugin.values
    .firstNotNullOfOrNull { byPlugin -> byPlugin[pluginId] }
    .orEmpty()
