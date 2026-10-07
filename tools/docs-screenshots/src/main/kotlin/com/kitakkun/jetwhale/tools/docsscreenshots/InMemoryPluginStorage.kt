package com.kitakkun.jetwhale.tools.docsscreenshots

import com.kitakkun.jetwhale.host.sdk.JetWhalePluginStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.yield
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Plugin storage held in memory, for a plugin's Root composable rendered outside a host. Values are
 * kept as the JSON the host's store would write, so [initial] seeds them in that form.
 */
class InMemoryPluginStorage(initial: Map<String, String>) : JetWhalePluginStorage {
    private val values = MutableStateFlow(initial)

    override suspend fun <T> put(key: String, value: T, serializer: KSerializer<T>) {
        values.update { it + (key to Json.encodeToString(serializer, value)) }
    }

    override suspend fun <T> get(key: String, serializer: KSerializer<T>): T? {
        // The host's store reads from disk, so a stored value arrives after the screen's own
        // effects have started; a Root that mirrors it into other state, like the Network
        // Inspector's split pane, counts on that order.
        yield()
        return values.value[key]?.let { Json.decodeFromString(serializer, it) }
    }

    override fun <T> getFlow(key: String, serializer: KSerializer<T>): Flow<T?> = values.map { stored -> stored[key]?.let { Json.decodeFromString(serializer, it) } }

    override suspend fun contains(key: String): Boolean = key in values.value

    override suspend fun remove(key: String) {
        values.update { it - key }
    }

    override suspend fun clear() {
        values.value = emptyMap()
    }

    override val keysFlow: Flow<Set<String>> = values.map { it.keys }
}
