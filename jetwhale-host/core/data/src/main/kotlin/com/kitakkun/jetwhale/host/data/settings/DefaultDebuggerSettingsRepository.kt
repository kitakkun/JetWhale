package com.kitakkun.jetwhale.host.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.kitakkun.jetwhale.host.data.DebuggerSettingsDataStoreQualifier
import com.kitakkun.jetwhale.host.model.DebuggerSettingsRepository
import com.kitakkun.jetwhale.host.model.ServerPortOverrides
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class DefaultDebuggerSettingsRepository(
    @param:DebuggerSettingsDataStoreQualifier
    private val dataStore: DataStore<Preferences>,
    launchPortOverrides: ServerPortOverrides,
) : DebuggerSettingsRepository {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    // Ports named on the command line shadow the stored ones for the rest of the session, so every
    // reader of this repository — the servers, the settings screen, the restart flows — sees the
    // ports actually in use. Picking a port in the settings screen drops the matching override.
    private val portOverrides = MutableStateFlow(launchPortOverrides)

    /**
     * Completes once [preferences] has read the store, or once that read has failed. Writes wait on
     * it, see [editPreferences].
     */
    private val storeRead = CompletableDeferred<Unit>()

    /**
     * The one collection of the store behind every flow below. Sharing a single collection is what
     * makes [storeRead] enough to order writes after all of them.
     */
    private val preferences: Flow<Preferences> = dataStore.data
        .onEach { storeRead.complete(Unit) }
        .onCompletion { storeRead.complete(Unit) }
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.Eagerly,
            initialValue = null,
        )
        .filterNotNull()

    override val adbAutoPortMappingEnabledFlow = preferences
        .mapNotNull { it[KEY_ADB_AUTO_PORT_MAPPING_ENABLED] }
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.Eagerly,
            initialValue = false,
        )
    override val checkForUpdatesOnStartupFlow = preferences
        .map { it[KEY_CHECK_FOR_UPDATES_ON_STARTUP] ?: DEFAULT_CHECK_FOR_UPDATES_ON_STARTUP }
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.Eagerly,
            initialValue = DEFAULT_CHECK_FOR_UPDATES_ON_STARTUP,
        )
    override val persistDataFlow = preferences.mapNotNull { it[KEY_PERSIST_DATA] }
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.Eagerly,
            initialValue = false,
        )
    override val serverPortFlow = preferences
        .map { it[KEY_SERVER_PORT] ?: DEFAULT_SERVER_PORT }
        .overriddenBy(ServerPortOverrides::serverPort)
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.Eagerly,
            initialValue = launchPortOverrides.serverPort ?: DEFAULT_SERVER_PORT,
        )
    override val mcpServerPortFlow = preferences
        .map { it[KEY_MCP_SERVER_PORT] ?: DEFAULT_MCP_SERVER_PORT }
        .overriddenBy(ServerPortOverrides::mcpServerPort)
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.Eagerly,
            initialValue = launchPortOverrides.mcpServerPort ?: DEFAULT_MCP_SERVER_PORT,
        )
    override val wssPortFlow = preferences
        .map { it[KEY_WSS_PORT] ?: DEFAULT_WSS_PORT }
        .overriddenBy(ServerPortOverrides::wssPort)
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.Eagerly,
            initialValue = launchPortOverrides.wssPort ?: DEFAULT_WSS_PORT,
        )
    override val wssEnabledFlow = preferences
        .map { it[KEY_WSS_ENABLED] ?: DEFAULT_WSS_ENABLED }
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.Eagerly,
            initialValue = DEFAULT_WSS_ENABLED,
        )
    override val mcpPluginInstallAllowedFlow = preferences
        .map { it[KEY_MCP_PLUGIN_INSTALL_ALLOWED] ?: DEFAULT_MCP_PLUGIN_INSTALL_ALLOWED }
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.Eagerly,
            initialValue = DEFAULT_MCP_PLUGIN_INSTALL_ALLOWED,
        )

    /**
     * Writes to the store, but not before [preferences] has read it. DataStore stamps a read that
     * overlaps a write with the version the write is about to publish, so the collection behind
     * [preferences] would discard that write and every flow above would serve the pre-write value
     * for the rest of the process.
     */
    private suspend fun editPreferences(transform: suspend (MutablePreferences) -> Unit) {
        storeRead.await()
        dataStore.edit(transform)
    }

    override suspend fun updateAdbAutoPortMappingEnabled(enabled: Boolean) {
        editPreferences { prefs ->
            prefs[KEY_ADB_AUTO_PORT_MAPPING_ENABLED] = enabled
        }
    }

    override suspend fun readCheckForUpdatesOnStartup(): Boolean = dataStore.data.first()[KEY_CHECK_FOR_UPDATES_ON_STARTUP] ?: DEFAULT_CHECK_FOR_UPDATES_ON_STARTUP

    override suspend fun updateCheckForUpdatesOnStartup(enabled: Boolean) {
        editPreferences { prefs ->
            prefs[KEY_CHECK_FOR_UPDATES_ON_STARTUP] = enabled
        }
    }

    override suspend fun updatePersistData(enabled: Boolean) {
        editPreferences { prefs ->
            prefs[KEY_PERSIST_DATA] = enabled
        }
    }

    override suspend fun updateServerPort(port: Int) {
        editPreferences { prefs ->
            prefs[KEY_SERVER_PORT] = port
        }
        dropOverride { it.copy(serverPort = null) }
    }

    override suspend fun readServerPort(): Int = portOverrides.value.serverPort ?: dataStore.data.first()[KEY_SERVER_PORT] ?: DEFAULT_SERVER_PORT

    override suspend fun readMcpServerPort(): Int = portOverrides.value.mcpServerPort ?: dataStore.data.first()[KEY_MCP_SERVER_PORT] ?: DEFAULT_MCP_SERVER_PORT

    override suspend fun updateMcpServerPort(port: Int) {
        editPreferences { prefs ->
            prefs[KEY_MCP_SERVER_PORT] = port
        }
        dropOverride { it.copy(mcpServerPort = null) }
    }

    override suspend fun readWssPort(): Int = portOverrides.value.wssPort ?: dataStore.data.first()[KEY_WSS_PORT] ?: DEFAULT_WSS_PORT

    override suspend fun updateWssPort(port: Int) {
        editPreferences { prefs ->
            prefs[KEY_WSS_PORT] = port
        }
        dropOverride { it.copy(wssPort = null) }
    }

    override suspend fun updateWssEnabled(enabled: Boolean) {
        editPreferences { prefs ->
            prefs[KEY_WSS_ENABLED] = enabled
        }
    }

    override suspend fun updateMcpPluginInstallAllowed(enabled: Boolean) {
        editPreferences { prefs ->
            prefs[KEY_MCP_PLUGIN_INSTALL_ALLOWED] = enabled
        }
    }

    /** Lets a launch override win over the stored value this flow carries. */
    private fun Flow<Int>.overriddenBy(selectOverride: (ServerPortOverrides) -> Int?): Flow<Int> = combine(portOverrides) { storedPort, overrides -> selectOverride(overrides) ?: storedPort }

    /**
     * Retires the launch override for a port the user just picked in the settings screen. Without
     * this the saved value would keep losing to the override for the rest of the session, leaving
     * the settings screen showing a port the user did not choose.
     *
     * Call this only once the new port is stored: retiring the override first would uncover the
     * previously stored port until the write lands, publishing an effective port nobody asked for.
     */
    private fun dropOverride(drop: (ServerPortOverrides) -> ServerPortOverrides) {
        portOverrides.update(drop)
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
        private val KEY_MCP_PLUGIN_INSTALL_ALLOWED = booleanPreferencesKey("mcp_plugin_install_allowed")
        private const val DEFAULT_SERVER_PORT = 5080
        private const val DEFAULT_MCP_SERVER_PORT = 7080
        private const val DEFAULT_WSS_PORT = 5443
        private const val DEFAULT_WSS_ENABLED = true
        private const val DEFAULT_MCP_PLUGIN_INSTALL_ALLOWED = false
    }
}
