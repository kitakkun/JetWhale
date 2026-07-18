package com.kitakkun.jetwhale.host.sdk

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

/**
 * The plugin's [JetWhalePluginStorage], provided by the host to the plugin's composition so that
 * [rememberPersistent] can reach it. `null` outside a host-managed plugin scene.
 */
public val LocalJetWhalePluginStorage: ProvidableCompositionLocal<JetWhalePluginStorage?> =
    staticCompositionLocalOf { null }

/**
 * A persistent counterpart to `rememberSaveable`: returns a [MutableState] whose value is loaded
 * from, and written back to, this plugin's [JetWhalePluginStorage] under [key].
 *
 * Because the value lives on disk, loading is asynchronous: the state starts at [default] and is
 * replaced with the stored value once it has been read. Subsequent writes to the state are persisted
 * automatically (debounced). Unlike `rememberSaveable`, [key] is required — an explicit key avoids
 * the silent collisions an implicit, position-derived key would cause across process restarts.
 */
@Composable
public inline fun <reified T> rememberPersistent(
    key: String,
    default: T,
): MutableState<T> = rememberPersistent(key = key, default = default, serializer = serializer<T>())

/**
 * Serializer-explicit variant of [rememberPersistent]; prefer the `reified` overload.
 */
@Composable
public fun <T> rememberPersistent(
    key: String,
    default: T,
    serializer: KSerializer<T>,
): MutableState<T> {
    val storage = LocalJetWhalePluginStorage.current
        ?: error(
            "rememberPersistent requires LocalJetWhalePluginStorage. " +
                "Is the plugin running inside a JetWhale host scene?",
        )
    val state = remember(key) { mutableStateOf(default) }

    LaunchedEffect(key, storage) {
        // Load the persisted value (if any) before observing writes, so the initial emission of
        // snapshotFlow below carries the loaded value, which drop(1) then skips.
        storage.get(key, serializer)?.let { state.value = it }
        snapshotFlow { state.value }
            .drop(1)
            .collectLatest { value ->
                // Debounce bursts of writes (e.g. typing) into a single persist.
                delay(PERSIST_DEBOUNCE_MILLIS)
                storage.put(key, value, serializer)
            }
    }

    return state
}

private const val PERSIST_DEBOUNCE_MILLIS: Long = 300L
