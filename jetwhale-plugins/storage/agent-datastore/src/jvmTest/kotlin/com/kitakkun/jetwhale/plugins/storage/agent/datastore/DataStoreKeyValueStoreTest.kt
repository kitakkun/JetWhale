package com.kitakkun.jetwhale.plugins.storage.agent.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.kitakkun.jetwhale.plugins.storage.agent.KeyValueStore
import com.kitakkun.jetwhale.plugins.storage.protocol.KeyValueEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DataStoreKeyValueStoreTest {
    private val directory: File = createTempDirectory("datastore-key-value-store").toFile()

    @AfterTest
    fun deleteDirectory() {
        directory.deleteRecursively()
    }

    @Test
    fun `entries lists every value type with the names the host decoder uses`() = runTest {
        val dataStore = createDataStore()
        dataStore.edit {
            it[booleanPreferencesKey("boolean")] = true
            it[intPreferencesKey("int")] = 42
            it[longPreferencesKey("long")] = 1L shl 40
            it[floatPreferencesKey("float")] = 1.5f
            it[doublePreferencesKey("double")] = 2.25
            it[stringPreferencesKey("string")] = "hello"
            it[stringSetPreferencesKey("set")] = setOf("a", "b")
            it[byteArrayPreferencesKey("bytes")] = byteArrayOf(0x0F, 0xA0.toByte())
        }

        val entries = KeyValueStore.dataStore("settings", dataStore).entries().sortedBy(KeyValueEntry::key)

        assertEquals(
            listOf(
                KeyValueEntry(key = "boolean", value = "true", type = "Boolean"),
                KeyValueEntry(key = "bytes", value = "0fa0", type = "ByteArray"),
                KeyValueEntry(key = "double", value = "2.25", type = "Double"),
                KeyValueEntry(key = "float", value = "1.5", type = "Float"),
                KeyValueEntry(key = "int", value = "42", type = "Int"),
                KeyValueEntry(key = "long", value = "1099511627776", type = "Long"),
                KeyValueEntry(key = "set", value = "a, b", type = "Set<String>"),
                KeyValueEntry(key = "string", value = "hello", type = "String"),
            ),
            entries,
        )
    }

    @Test
    fun `remove deletes a key of a non-String type and the app's data flow sees it`() = runTest {
        val dataStore = createDataStore()
        dataStore.edit {
            it[intPreferencesKey("count")] = 3
            it[stringPreferencesKey("name")] = "kept"
        }

        KeyValueStore.dataStore("settings", dataStore).remove("count")

        val preferences = dataStore.data.first()
        assertNull(preferences[intPreferencesKey("count")])
        assertEquals("kept", preferences[stringPreferencesKey("name")])
    }

    @Test
    fun `remove of a key the store does not have leaves it unchanged`() = runTest {
        val dataStore = createDataStore()
        dataStore.edit { it[stringPreferencesKey("name")] = "kept" }
        val store = KeyValueStore.dataStore("settings", dataStore)

        store.remove("missing")

        assertEquals(listOf(KeyValueEntry(key = "name", value = "kept", type = "String")), store.entries())
    }

    private fun TestScope.createDataStore(): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = backgroundScope,
        produceFile = { File(directory, "settings.preferences_pb") },
    )
}
