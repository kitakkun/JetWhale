package com.kitakkun.jetwhale.host.data.window

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import com.kitakkun.jetwhale.host.data.WindowStateDataStoreQualifier
import com.kitakkun.jetwhale.host.model.PersistedWindowState
import com.kitakkun.jetwhale.host.model.WindowStateRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.first

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class DefaultWindowStateRepository(
    @param:WindowStateDataStoreQualifier
    private val dataStore: DataStore<Preferences>,
) : WindowStateRepository {
    override suspend fun loadWindowState(): PersistedWindowState? {
        val preferences = dataStore.data.first()
        val width = preferences[widthPreferencesKey] ?: return null
        val height = preferences[heightPreferencesKey] ?: return null
        return PersistedWindowState(
            width = width,
            height = height,
            x = preferences[xPreferencesKey],
            y = preferences[yPreferencesKey],
        )
    }

    override suspend fun saveWindowState(state: PersistedWindowState) {
        val x = state.x
        val y = state.y
        dataStore.edit { prefs ->
            prefs[widthPreferencesKey] = state.width
            prefs[heightPreferencesKey] = state.height
            if (x != null && y != null) {
                prefs[xPreferencesKey] = x
                prefs[yPreferencesKey] = y
            }
        }
    }

    companion object {
        private val widthPreferencesKey = floatPreferencesKey("window_width")
        private val heightPreferencesKey = floatPreferencesKey("window_height")
        private val xPreferencesKey = floatPreferencesKey("window_x")
        private val yPreferencesKey = floatPreferencesKey("window_y")
    }
}
