package com.kitakkun.jetwhale.host.model

import kotlinx.coroutines.flow.StateFlow

/**
 * The host-scoped tools an agent may call, grouped by what going wrong would cost.
 *
 * Groups rather than individual tools: the set of tools grows, and a user deciding "may an agent
 * restart my server" should not have to re-decide it every release.
 */
enum class McpHostToolGroup {
    /** Read-only: status, host logs, the installed-plugin list. */
    OBSERVE,

    /** Moves the main window to another screen. */
    NAVIGATE,

    /** Enables, disables and installs plugins — installing loads new code into the host process. */
    MANAGE_PLUGINS,

    /** Changes settings and restarts the debug server, which disconnects every session. */
    SETTINGS_AND_SERVERS,
}

/** What a tool needs permission for. Declared per tool; the plugin id is resolved per call. */
sealed interface McpToolPermission {
    /**
     * Always allowed. Reserved for the discovery primitives every other tool is described in terms
     * of — denying them would leave an agent unable to find the ids it needs to ask about anything.
     */
    data object Unrestricted : McpToolPermission

    data class HostGroup(val group: McpHostToolGroup) : McpToolPermission

    /**
     * Drives some plugin's UI. Which plugin is only known per call, from the request's `pluginId`,
     * so the check happens at invocation time rather than at registration.
     */
    data object PluginUi : McpToolPermission

    /** A tool the plugin itself contributes. The plugin is resolved from the call's `sessionId`. */
    data object PluginOwnTools : McpToolPermission
}

/**
 * Which MCP tools the user has allowed.
 *
 * Host groups are stored as the **allowed** set and plugins as the **denied** set, deliberately.
 * The host groups are a closed list that grows as JetWhale adds tools, so a group nobody has opted
 * into — including one that did not exist when the user last looked — is denied. Plugins are an
 * open list the user populates by installing and enabling them, so a plugin they have not ruled out
 * is allowed.
 */
data class McpPermissions(
    val allowedHostGroups: Set<McpHostToolGroup>,
    val pluginsDeniedUi: Set<String>,
    val pluginsDeniedOwnTools: Set<String>,
) {
    fun allows(permission: McpToolPermission, pluginId: String?): Boolean = when (permission) {
        McpToolPermission.Unrestricted -> true

        is McpToolPermission.HostGroup -> permission.group in allowedHostGroups

        // An unattributable call is denied: letting a tool through because its target could not be
        // resolved would make an unknown plugin id the way around the setting.
        McpToolPermission.PluginUi -> pluginId != null && pluginId !in pluginsDeniedUi

        McpToolPermission.PluginOwnTools -> pluginId != null && pluginId !in pluginsDeniedOwnTools
    }

    companion object {
        /**
         * Observation and navigation are on; managing plugins and touching servers are not.
         * Installing a plugin runs new code in the host, and restarting the debug server drops
         * every session — both are worth a deliberate opt-in, and the MCP port is unauthenticated.
         */
        val Default = McpPermissions(
            allowedHostGroups = setOf(McpHostToolGroup.OBSERVE, McpHostToolGroup.NAVIGATE),
            pluginsDeniedUi = emptySet(),
            pluginsDeniedOwnTools = emptySet(),
        )
    }
}

interface McpPermissionsRepository {
    val permissionsFlow: StateFlow<McpPermissions>

    suspend fun setHostGroupAllowed(group: McpHostToolGroup, allowed: Boolean)

    suspend fun setPluginUiAllowed(pluginId: String, allowed: Boolean)

    suspend fun setPluginOwnToolsAllowed(pluginId: String, allowed: Boolean)
}
