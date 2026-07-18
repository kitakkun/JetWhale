package com.kitakkun.jetwhale.host.data.window

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.kitakkun.jetwhale.host.model.PersistedWindowState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DefaultWindowStateRepositoryTest {
    private fun newRepository(): DefaultWindowStateRepository {
        val tempDir = Files.createTempDirectory("jetwhale-window-state-test")
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath(
            scope = CoroutineScope(Dispatchers.IO),
        ) { tempDir.resolve("window_state.preferences_pb").toString().toPath() }
        return DefaultWindowStateRepository(dataStore)
    }

    @Test
    fun `returns null when nothing is saved`() = runBlocking {
        assertNull(newRepository().loadWindowState())
    }

    @Test
    fun `saves and restores size and position`() = runBlocking {
        val repository = newRepository()

        repository.saveWindowState(PersistedWindowState(width = 1440f, height = 900f, x = 10f, y = 20f))

        assertEquals(
            PersistedWindowState(width = 1440f, height = 900f, x = 10f, y = 20f),
            repository.loadWindowState(),
        )
    }

    @Test
    fun `saving without a position clears a previously saved position`() = runBlocking {
        val repository = newRepository()
        repository.saveWindowState(PersistedWindowState(width = 1280f, height = 800f, x = 10f, y = 20f))

        repository.saveWindowState(PersistedWindowState(width = 1024f, height = 768f, x = null, y = null))

        assertEquals(
            PersistedWindowState(width = 1024f, height = 768f, x = null, y = null),
            repository.loadWindowState(),
        )
    }

    @Test
    fun `partial position is not persisted`() = runBlocking {
        val repository = newRepository()

        repository.saveWindowState(PersistedWindowState(width = 1280f, height = 800f, x = 10f, y = null))

        assertEquals(
            PersistedWindowState(width = 1280f, height = 800f, x = null, y = null),
            repository.loadWindowState(),
        )
    }
}
