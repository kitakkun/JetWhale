package com.kitakkun.jetwhale.host.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.kitakkun.jetwhale.host.data.DebuggerSettingsDataStoreQualifier
import com.kitakkun.jetwhale.host.model.McpHostToolGroup
import com.kitakkun.jetwhale.host.model.McpPermissionOverride
import com.kitakkun.jetwhale.host.model.McpPermissions
import com.kitakkun.jetwhale.host.model.McpPermissionsRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class DefaultMcpPermissionsRepository(
    @param:DebuggerSettingsDataStoreQualifier
    private val dataStore: DataStore<Preferences>,
    private val launchOverride: McpPermissionOverride,
) : McpPermissionsRepository {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    override val permissionsFlow = dataStore.data
        .map { preferences ->
            McpPermissions(
                // An entry that no longer names a group is dropped rather than failing the read, so
                // removing a group in a later release cannot brick the settings screen.
                allowedHostGroups = preferences[KEY_ALLOWED_HOST_GROUPS]
                    ?.mapNotNullTo(mutableSetOf()) { name -> McpHostToolGroup.entries.find { it.name == name } }
                    ?: McpPermissions.Default.allowedHostGroups,
                pluginsDeniedInspect = preferences[KEY_DENIED_PLUGIN_INSPECT].orEmpty(),
                pluginsDeniedInteract = preferences[KEY_DENIED_PLUGIN_INTERACT].orEmpty(),
                deniedPluginTools = preferences[KEY_DENIED_PLUGIN_TOOLS].orEmpty(),
            ).allOverriddenBy(launchOverride)
        }
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.Eagerly,
            // Until the stored value arrives the defaults apply, so the gap at startup denies rather
            // than grants.
            initialValue = McpPermissions.Default.allOverriddenBy(launchOverride),
        )

    override suspend fun setHostGroupAllowed(group: McpHostToolGroup, allowed: Boolean) {
        dataStore.edit { preferences ->
            val current = preferences[KEY_ALLOWED_HOST_GROUPS] ?: McpPermissions.Default.allowedHostGroups.map { it.name }.toSet()
            preferences[KEY_ALLOWED_HOST_GROUPS] = if (allowed) current + group.name else current - group.name
        }
    }

    override suspend fun setPluginInspectAllowed(pluginId: String, allowed: Boolean) {
        dataStore.editDenials(KEY_DENIED_PLUGIN_INSPECT, pluginId, allowed)
    }

    override suspend fun setPluginInteractAllowed(pluginId: String, allowed: Boolean) {
        dataStore.editDenials(KEY_DENIED_PLUGIN_INTERACT, pluginId, allowed)
    }

    override suspend fun setPluginToolAllowed(toolName: String, allowed: Boolean) {
        dataStore.editDenials(KEY_DENIED_PLUGIN_TOOLS, toolName, allowed)
    }

    private suspend fun DataStore<Preferences>.editDenials(
        key: Preferences.Key<Set<String>>,
        value: String,
        allowed: Boolean,
    ) {
        edit { preferences ->
            val denied = preferences[key].orEmpty()
            preferences[key] = if (allowed) denied - value else denied + value
        }
    }

    companion object Companion {
        // Host groups are stored as what is allowed and plugins as what is denied; see McpPermissions.
        private val KEY_ALLOWED_HOST_GROUPS = stringSetPreferencesKey("mcp_allowed_host_groups")
        private val KEY_DENIED_PLUGIN_INSPECT = stringSetPreferencesKey("mcp_denied_plugin_inspect")
        private val KEY_DENIED_PLUGIN_INTERACT = stringSetPreferencesKey("mcp_denied_plugin_interact")
        private val KEY_DENIED_PLUGIN_TOOLS = stringSetPreferencesKey("mcp_denied_plugin_tools")
    }
}
