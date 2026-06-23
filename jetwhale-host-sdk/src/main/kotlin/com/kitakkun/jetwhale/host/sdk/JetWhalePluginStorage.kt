package com.kitakkun.jetwhale.host.sdk

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

/**
 * Per-plugin persistent key-value storage handed to a plugin via [JetWhaleHostContext.storage].
 *
 * Every handle a plugin receives is already scoped to that plugin's own `pluginId` by the host: a
 * plugin can neither name another plugin's id nor reach its data. Values are stored on disk and
 * survive across debug sessions and host restarts.
 *
 * Both primitives and structured data are supported through `kotlinx.serialization`: any value whose
 * type has a [KSerializer] can be stored. Prefer the `reified` extension overloads
 * ([put], [get], [getFlow]) which resolve the serializer from the type for you; the explicit
 * [KSerializer] members exist so the interface can be implemented without inlining.
 */
public interface JetWhalePluginStorage {
    /** Stores [value] under [key], replacing any existing value. */
    public suspend fun <T> put(key: String, value: T, serializer: KSerializer<T>)

    /** Reads the current value for [key], or `null` if absent. */
    public suspend fun <T> get(key: String, serializer: KSerializer<T>): T?

    /** Emits the value for [key] and re-emits whenever it changes; `null` while absent. */
    public fun <T> getFlow(key: String, serializer: KSerializer<T>): Flow<T?>

    /** Returns whether a value is currently stored under [key]. */
    public suspend fun contains(key: String): Boolean

    /** Removes the value stored under [key], if any. */
    public suspend fun remove(key: String)

    /** Removes every value owned by this plugin. */
    public suspend fun clear()

    /** Emits the set of keys currently stored, re-emitting on every change. */
    public val keysFlow: Flow<Set<String>>
}

/** Stores [value] under [key], resolving the serializer from the reified type [T]. */
public suspend inline fun <reified T> JetWhalePluginStorage.put(key: String, value: T): Unit = put(key, value, serializer())

/** Reads the current value for [key], resolving the serializer from the reified type [T]. */
public suspend inline fun <reified T> JetWhalePluginStorage.get(key: String): T? = get(key, serializer<T>())

/** Observes the value for [key], resolving the serializer from the reified type [T]. */
public inline fun <reified T> JetWhalePluginStorage.getFlow(key: String): Flow<T?> = getFlow(key, serializer<T>())
