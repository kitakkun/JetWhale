package com.kitakkun.jetwhale.plugins.storage.agent.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kitakkun.jetwhale.plugins.storage.agent.KeyValueStore
import com.kitakkun.jetwhale.plugins.storage.protocol.KeyValueEntry
import kotlinx.coroutines.flow.first

/**
 * Shows [dataStore] in the host as the key-value store [name].
 *
 * The host reads the values through [dataStore] itself, so it sees what the app sees, including
 * writes that are still being flushed to disk. Removing an entry goes through
 * [DataStore.updateData], so the app's collectors of [DataStore.data] observe the change.
 *
 * ```kotlin
 * JetWhaleStorageAgentPlugin(
 *     fileRoots = { FileRoot.platformDefaults() },
 *     keyValueStores = { KeyValueStore.platformDefaults() + KeyValueStore.dataStore("settings", settingsDataStore) },
 * )
 * ```
 */
fun KeyValueStore.Companion.dataStore(name: String, dataStore: DataStore<Preferences>): KeyValueStore =
    DataStoreKeyValueStore(name, dataStore)

private class DataStoreKeyValueStore(
    override val name: String,
    private val dataStore: DataStore<Preferences>,
) : KeyValueStore {
    override suspend fun entries(): List<KeyValueEntry> = dataStore.data.first().asMap().map { (key, value) ->
        KeyValueEntry(
            key = key.name,
            value = when (value) {
                is Set<*> -> value.joinToString()
                is ByteArray -> value.toHexString()
                else -> value.toString()
            },
            // The names the host's Preferences DataStore file decoder uses. On JS every number is
            // the same runtime type, so an integral Double shows as Int there and a Float as Double.
            type = when (value) {
                is Boolean -> "Boolean"
                is String -> "String"
                is Int -> "Int"
                is Long -> "Long"
                is Double -> "Double"
                is Float -> "Float"
                is Set<*> -> "Set<String>"
                is ByteArray -> "ByteArray"
                else -> value::class.simpleName ?: "Unknown"
            },
        )
    }

    override suspend fun remove(key: String) {
        // Preferences.Key compares by name alone, so a String key removes a value of any type.
        dataStore.edit { it -= stringPreferencesKey(key) }
    }
}
