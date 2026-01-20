package com.kitakkun.jetwhale.host.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.kitakkun.jetwhale.host.data.DebuggerSettingsDataStoreQualifier
import com.kitakkun.jetwhale.host.model.DebuggerSettingsRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class DefaultDebuggerSettingsRepository(
    @param:DebuggerSettingsDataStoreQualifier
    private val dataStore: DataStore<Preferences>,
) : DebuggerSettingsRepository {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    override val adbAutoPortMappingEnabledFlow = dataStore.data
        .mapNotNull { it[KEY_ADB_AUTO_PORT_MAPPING_ENABLED] }
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.Eagerly,
            initialValue = false,
        )
    override val persistDataFlow = dataStore.data.mapNotNull { it[KEY_PERSIST_DATA] }
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.Eagerly,
            initialValue = false,
        )
    override val serverPortFlow = dataStore.data
        .map { it[KEY_SERVER_PORT] ?: DEFAULT_SERVER_PORT }
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.Eagerly,
            initialValue = DEFAULT_SERVER_PORT,
        )

    override suspend fun updateAdbAutoPortMappingEnabled(enabled: Boolean) {
        dataStore.updateData { prefs ->
            prefs.toMutablePreferences().apply {
                this[KEY_ADB_AUTO_PORT_MAPPING_ENABLED] = enabled
            }
        }
    }

    override suspend fun updatePersistData(enabled: Boolean) {
        dataStore.updateData { prefs ->
            prefs.toMutablePreferences().apply {
                this[KEY_PERSIST_DATA] = enabled
            }
        }
    }

    override suspend fun updateServerPort(port: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_SERVER_PORT] = port
        }
    }

    override suspend fun readServerPort(): Int {
        return dataStore.data.first()[KEY_SERVER_PORT] ?: DEFAULT_SERVER_PORT
    }

    companion object Companion {
        private val KEY_ADB_AUTO_PORT_MAPPING_ENABLED = booleanPreferencesKey("adb_auto_port_mapping_enabled")
        private val KEY_PERSIST_DATA = booleanPreferencesKey("persist_data")
        private val KEY_SERVER_PORT = intPreferencesKey("server_port")
        private const val DEFAULT_SERVER_PORT = 5080
    }
}
