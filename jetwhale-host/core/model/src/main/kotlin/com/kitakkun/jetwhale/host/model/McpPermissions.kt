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
     * Reads a plugin's UI without changing it — the screenshot and the semantics tree. Split from
     * [PluginInteract] because "look at my plugin but do not touch it" is a position a user can
     * reasonably hold. Which plugin is only known per call, from the request's `pluginId`.
     */
    data object PluginInspect : McpToolPermission

    /** Sends input to a plugin's UI: clicks, typing, scrolling, drags. */
    data object PluginInteract : McpToolPermission

    /**
     * One tool a plugin contributes, keyed by the tool's own name — plugin tools are permitted
     * individually, so an agent can be allowed to read a plugin's data without being allowed to
     * change it. Names are globally unique, which is also how [McpToolRegistry] keys them.
     */
    data class PluginTool(val toolName: String) : McpToolPermission
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
    val pluginsDeniedInspect: Set<String>,
    val pluginsDeniedInteract: Set<String>,
    val deniedPluginTools: Set<String>,
) {
    fun allows(permission: McpToolPermission, pluginId: String?): Boolean = when (permission) {
        McpToolPermission.Unrestricted -> true

        is McpToolPermission.HostGroup -> permission.group in allowedHostGroups

        // An unattributable call is denied: letting a tool through because its target could not be
        // resolved would make an unknown plugin id the way around the setting.
        McpToolPermission.PluginInspect -> pluginId != null && pluginId !in pluginsDeniedInspect

        McpToolPermission.PluginInteract -> pluginId != null && pluginId !in pluginsDeniedInteract

        // Keyed by the tool's own name, so this one needs no plugin attribution to decide.
        is McpToolPermission.PluginTool -> permission.toolName !in deniedPluginTools
    }

    /** The launch override, applied where the permissions are read so nothing sees a stale copy. */
    fun allOverriddenBy(override: McpPermissionOverride): McpPermissions = when {
        override.allowAll -> AllowAll
        else -> this
    }

    companion object {
        /**
         * Observation and navigation are on; managing plugins and touching servers are not.
         * Installing a plugin runs new code in the host, and restarting the debug server drops
         * every session — both are worth a deliberate opt-in, and the MCP port is unauthenticated.
         *
         * Everything a plugin offers starts allowed: the user installed and enabled it deliberately,
         * and can revoke any single tool of it afterwards.
         */
        val Default = McpPermissions(
            allowedHostGroups = setOf(McpHostToolGroup.OBSERVE, McpHostToolGroup.NAVIGATE),
            pluginsDeniedInspect = emptySet(),
            pluginsDeniedInteract = emptySet(),
            deniedPluginTools = emptySet(),
        )

        val AllowAll = McpPermissions(
            allowedHostGroups = McpHostToolGroup.entries.toSet(),
            pluginsDeniedInspect = emptySet(),
            pluginsDeniedInteract = emptySet(),
            deniedPluginTools = emptySet(),
        )
    }
}

interface McpPermissionsRepository {
    val permissionsFlow: StateFlow<McpPermissions>

    suspend fun setHostGroupAllowed(group: McpHostToolGroup, allowed: Boolean)

    suspend fun setPluginInspectAllowed(pluginId: String, allowed: Boolean)

    suspend fun setPluginInteractAllowed(pluginId: String, allowed: Boolean)

    suspend fun setPluginToolAllowed(toolName: String, allowed: Boolean)
}
