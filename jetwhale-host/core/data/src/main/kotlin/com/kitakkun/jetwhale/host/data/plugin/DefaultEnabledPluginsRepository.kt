package com.kitakkun.jetwhale.host.data.plugin

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.kitakkun.jetwhale.host.data.EnabledPluginsDataStoreQualifier
import com.kitakkun.jetwhale.host.model.EnabledPluginsRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class DefaultEnabledPluginsRepository(
    @param:EnabledPluginsDataStoreQualifier
    private val dataStore: DataStore<Preferences>,
) : EnabledPluginsRepository {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    override val enabledPluginIdsFlow: Flow<Set<String>> = dataStore.data
        .map { preferences -> preferences[KEY_ENABLED_PLUGIN_IDS] ?: emptySet() }
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.Eagerly,
            initialValue = emptySet(),
        )

    private val mutableDisabledPluginIdFlow: MutableSharedFlow<String> = MutableSharedFlow(extraBufferCapacity = 1)
    override val disabledPluginIdFlow: Flow<String> = mutableDisabledPluginIdFlow

    override suspend fun setPluginEnabled(pluginId: String, enabled: Boolean) {
        dataStore.edit { preferences ->
            val currentSet = preferences[KEY_ENABLED_PLUGIN_IDS] ?: emptySet()
            preferences[KEY_ENABLED_PLUGIN_IDS] = if (enabled) {
                currentSet + pluginId
            } else {
                currentSet - pluginId
            }
        }
        if (!enabled) {
            mutableDisabledPluginIdFlow.emit(pluginId)
        }
    }

    override suspend fun isPluginEnabled(pluginId: String): Boolean {
        return dataStore.data.first()[KEY_ENABLED_PLUGIN_IDS]?.contains(pluginId) ?: false
    }

    companion object {
        private val KEY_ENABLED_PLUGIN_IDS = stringSetPreferencesKey("enabled_plugin_ids")
    }
}
