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
    override val checkForUpdatesOnStartupFlow = dataStore.data
        .map { it[KEY_CHECK_FOR_UPDATES_ON_STARTUP] ?: DEFAULT_CHECK_FOR_UPDATES_ON_STARTUP }
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.Eagerly,
            initialValue = DEFAULT_CHECK_FOR_UPDATES_ON_STARTUP,
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
    override val mcpServerPortFlow = dataStore.data
        .map { it[KEY_MCP_SERVER_PORT] ?: DEFAULT_MCP_SERVER_PORT }
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.Eagerly,
            initialValue = DEFAULT_MCP_SERVER_PORT,
        )
    override val wssPortFlow = dataStore.data
        .map { it[KEY_WSS_PORT] ?: DEFAULT_WSS_PORT }
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.Eagerly,
            initialValue = DEFAULT_WSS_PORT,
        )
    override val wssEnabledFlow = dataStore.data
        .map { it[KEY_WSS_ENABLED] ?: DEFAULT_WSS_ENABLED }
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.Eagerly,
            initialValue = DEFAULT_WSS_ENABLED,
        )

    override suspend fun updateAdbAutoPortMappingEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_ADB_AUTO_PORT_MAPPING_ENABLED] = enabled
        }
    }

    override suspend fun readCheckForUpdatesOnStartup(): Boolean = dataStore.data.first()[KEY_CHECK_FOR_UPDATES_ON_STARTUP] ?: DEFAULT_CHECK_FOR_UPDATES_ON_STARTUP

    override suspend fun updateCheckForUpdatesOnStartup(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_CHECK_FOR_UPDATES_ON_STARTUP] = enabled
        }
    }

    override suspend fun updatePersistData(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_PERSIST_DATA] = enabled
        }
    }

    override suspend fun updateServerPort(port: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_SERVER_PORT] = port
        }
    }

    override suspend fun readServerPort(): Int = dataStore.data.first()[KEY_SERVER_PORT] ?: DEFAULT_SERVER_PORT

    override suspend fun readMcpServerPort(): Int = dataStore.data.first()[KEY_MCP_SERVER_PORT] ?: DEFAULT_MCP_SERVER_PORT

    override suspend fun updateMcpServerPort(port: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_MCP_SERVER_PORT] = port
        }
    }

    override suspend fun readWssPort(): Int = dataStore.data.first()[KEY_WSS_PORT] ?: DEFAULT_WSS_PORT

    override suspend fun updateWssPort(port: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_WSS_PORT] = port
        }
    }

    override suspend fun updateWssEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_WSS_ENABLED] = enabled
        }
    }

    companion object Companion {
        private val KEY_ADB_AUTO_PORT_MAPPING_ENABLED = booleanPreferencesKey("adb_auto_port_mapping_enabled")
        private val KEY_CHECK_FOR_UPDATES_ON_STARTUP = booleanPreferencesKey("check_for_updates_on_startup")
        private const val DEFAULT_CHECK_FOR_UPDATES_ON_STARTUP = true
        private val KEY_PERSIST_DATA = booleanPreferencesKey("persist_data")
        private val KEY_SERVER_PORT = intPreferencesKey("server_port")
        private val KEY_MCP_SERVER_PORT = intPreferencesKey("mcp_server_port")
        private val KEY_WSS_PORT = intPreferencesKey("wss_port")
        private val KEY_WSS_ENABLED = booleanPreferencesKey("wss_enabled")
        private const val DEFAULT_SERVER_PORT = 5080
        private const val DEFAULT_MCP_SERVER_PORT = 7080
        private const val DEFAULT_WSS_PORT = 5443
        private const val DEFAULT_WSS_ENABLED = true
    }
}
