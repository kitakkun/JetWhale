package com.kitakkun.jetwhale.host.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.kitakkun.jetwhale.host.data.DebuggerSettingsDataStoreQualifier
import com.kitakkun.jetwhale.host.model.DebuggerSettingsRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
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

    companion object Companion {
        private val KEY_ADB_AUTO_PORT_MAPPING_ENABLED = booleanPreferencesKey("adb_auto_port_mapping_enabled")
        private val KEY_PERSIST_DATA = booleanPreferencesKey("persist_data")
    }
}
