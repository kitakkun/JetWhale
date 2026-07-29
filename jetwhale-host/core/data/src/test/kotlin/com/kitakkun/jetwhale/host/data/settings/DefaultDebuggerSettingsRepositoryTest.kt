package com.kitakkun.jetwhale.host.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.kitakkun.jetwhale.host.model.ServerPortOverrides
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultDebuggerSettingsRepositoryTest {
    private val noOverrides = ServerPortOverrides(serverPort = null, wssPort = null, mcpServerPort = null)

    // Each repository gets its own store file, so stored ports never leak between tests.
    private fun newDataStore(): DataStore<Preferences> {
        val directory = Files.createTempDirectory("jetwhale-debugger-settings-test")
        return PreferenceDataStoreFactory.createWithPath(scope = CoroutineScope(Dispatchers.IO)) {
            "$directory/debugger_settings.preferences_pb".toPath()
        }
    }

    /** Awaits [expected] rather than sampling, since the store's first emission is asynchronous. */
    private suspend fun <T> assertEmits(expected: T, flow: Flow<T>) {
        withTimeout(5_000) { flow.first { it == expected } }
    }

    @Test
    fun `adb auto port mapping is on until it is turned off`() = runBlocking {
        val dataStore = newDataStore()

        assertEmits(true, DefaultDebuggerSettingsRepository(dataStore, noOverrides).adbAutoPortMappingEnabledFlow)

        DefaultDebuggerSettingsRepository(dataStore, noOverrides).updateAdbAutoPortMappingEnabled(false)

        assertEmits(false, DefaultDebuggerSettingsRepository(dataStore, noOverrides).adbAutoPortMappingEnabledFlow)
    }

    @Test
    fun `launch overrides win over the stored ports`() = runBlocking {
        val dataStore = newDataStore()
        DefaultDebuggerSettingsRepository(dataStore, noOverrides).run {
            updateServerPort(5080)
            updateWssPort(5443)
            updateMcpServerPort(7080)
        }

        val repository = DefaultDebuggerSettingsRepository(
            dataStore,
            ServerPortOverrides(serverPort = 5081, wssPort = 5444, mcpServerPort = 7081),
        )

        assertEquals(5081, repository.readServerPort())
        assertEquals(5444, repository.readWssPort())
        assertEquals(7081, repository.readMcpServerPort())
        assertEmits(5081, repository.serverPortFlow)
        assertEmits(5444, repository.wssPortFlow)
        assertEmits(7081, repository.mcpServerPortFlow)
    }

    @Test
    fun `a port left unoverridden keeps its stored value`() = runBlocking {
        val dataStore = newDataStore()
        DefaultDebuggerSettingsRepository(dataStore, noOverrides).updateMcpServerPort(7080)

        val repository = DefaultDebuggerSettingsRepository(
            dataStore,
            ServerPortOverrides(serverPort = 5081, wssPort = null, mcpServerPort = null),
        )

        assertEquals(7080, repository.readMcpServerPort())
        assertEmits(7080, repository.mcpServerPortFlow)
    }

    @Test
    fun `picking a port after launch retires its override`() = runBlocking {
        val repository = DefaultDebuggerSettingsRepository(
            newDataStore(),
            ServerPortOverrides(serverPort = 5081, wssPort = null, mcpServerPort = null),
        )

        repository.updateServerPort(5082)

        assertEquals(5082, repository.readServerPort())
        assertEmits(5082, repository.serverPortFlow)
    }

    @Test
    fun `retiring one override leaves the others in force`() = runBlocking {
        val repository = DefaultDebuggerSettingsRepository(
            newDataStore(),
            ServerPortOverrides(serverPort = 5081, wssPort = 5444, mcpServerPort = 7081),
        )

        repository.updateServerPort(5082)

        assertEquals(5444, repository.readWssPort())
        assertEquals(7081, repository.readMcpServerPort())
    }
}
