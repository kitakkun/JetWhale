package com.kitakkun.jetwhale.host.data.plugin

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.core.okio.OkioSerializer
import androidx.datastore.core.okio.OkioStorage
import com.kitakkun.jetwhale.host.data.AppDataDirectoryProvider
import com.kitakkun.jetwhale.host.model.PluginDataStoreRepository
import com.kitakkun.jetwhale.host.sdk.JetWhalePluginStorage
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okio.BufferedSink
import okio.BufferedSource
import okio.FileSystem
import java.util.concurrent.ConcurrentHashMap

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class DefaultPluginDataStoreRepository(
    private val appDataDirectoryProvider: AppDataDirectoryProvider,
) : PluginDataStoreRepository {
    // One storage handle per pluginId, shared across that plugin's sessions. A single DataStore per
    // file is required anyway: DataStore forbids more than one active instance over the same file.
    private val storages: ConcurrentHashMap<String, JetWhalePluginStorage> = ConcurrentHashMap()

    override fun storageFor(pluginId: String): JetWhalePluginStorage = storages.computeIfAbsent(pluginId) {
        DataStorePluginStorage(createDataStore(pluginId))
    }

    private fun createDataStore(pluginId: String): DataStore<JsonObject> = DataStoreFactory.create(
        storage = OkioStorage(
            fileSystem = FileSystem.SYSTEM,
            serializer = JsonObjectOkioSerializer,
            producePath = { appDataDirectoryProvider.resolvePluginDataFilePath(pluginId) },
        ),
        corruptionHandler = ReplaceFileCorruptionHandler { EMPTY_JSON_OBJECT },
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    )

    private companion object {
        val EMPTY_JSON_OBJECT = JsonObject(emptyMap())
    }
}

/**
 * Backs a single plugin's [JetWhalePluginStorage] with a [DataStore] holding one [JsonObject]: each
 * key maps to a serialized JSON element. DataStore gives us atomic writes, corruption recovery and a
 * single-writer guarantee for free.
 */
private class DataStorePluginStorage(
    private val dataStore: DataStore<JsonObject>,
) : JetWhalePluginStorage {
    private val json = Json

    override suspend fun <T> put(key: String, value: T, serializer: KSerializer<T>) {
        val element = json.encodeToJsonElement(serializer, value)
        dataStore.updateData { current -> JsonObject(current + (key to element)) }
    }

    override suspend fun <T> get(key: String, serializer: KSerializer<T>): T? = getFlow(key, serializer).first()

    override fun <T> getFlow(key: String, serializer: KSerializer<T>): Flow<T?> = dataStore.data.map { obj -> obj[key]?.let { json.decodeFromJsonElement(serializer, it) } }

    override suspend fun contains(key: String): Boolean = dataStore.data.first().containsKey(key)

    override suspend fun remove(key: String) {
        dataStore.updateData { current -> JsonObject(current - key) }
    }

    override suspend fun clear() {
        dataStore.updateData { JsonObject(emptyMap()) }
    }

    override val keysFlow: Flow<Set<String>> = dataStore.data.map { it.keys }
}

private object JsonObjectOkioSerializer : OkioSerializer<JsonObject> {
    private val json = Json
    override val defaultValue: JsonObject = JsonObject(emptyMap())

    override suspend fun readFrom(source: BufferedSource): JsonObject {
        val text = source.readUtf8()
        if (text.isBlank()) return defaultValue
        return json.decodeFromString(JsonObject.serializer(), text)
    }

    override suspend fun writeTo(t: JsonObject, sink: BufferedSink) {
        sink.writeUtf8(json.encodeToString(JsonObject.serializer(), t))
    }
}
