@file:OptIn(ExperimentalWasmJsInterop::class)

package com.kitakkun.jetwhale.plugins.storage.agent

import com.kitakkun.jetwhale.plugins.storage.protocol.FileEntry
import com.kitakkun.jetwhale.plugins.storage.protocol.KeyValueEntry
import kotlin.js.ExperimentalWasmJsInterop

actual fun FileRoot.Companion.platformDefaults(): List<FileRoot> = emptyList()

// Node has neither storage area, so each is offered only where it is defined.
actual fun KeyValueStore.Companion.platformDefaults(): List<KeyValueStore> = listOf("localStorage", "sessionStorage").filter(::isWebStorageDefined).map(::WebStorageStore)

private class WebStorageStore(override val name: String) : KeyValueStore {
    override suspend fun entries(): List<KeyValueEntry> = (0 until webStorageLength(name)).mapNotNull { index ->
        val key = webStorageKey(name, index) ?: return@mapNotNull null
        KeyValueEntry(key = key, value = webStorageGetItem(name, key) ?: "", type = "String")
    }

    override suspend fun remove(key: String) {
        webStorageRemoveItem(name, key)
    }
}

private fun isWebStorageDefined(area: String): Boolean = js("typeof globalThis[area] !== 'undefined'")

private fun webStorageLength(area: String): Int = js("globalThis[area].length")

private fun webStorageKey(area: String, index: Int): String? = js("globalThis[area].key(index)")

private fun webStorageGetItem(area: String, key: String): String? = js("globalThis[area].getItem(key)")

private fun webStorageRemoveItem(area: String, key: String): Unit = js("globalThis[area].removeItem(key)")

internal actual fun listDirectoryEntries(path: String): List<FileEntry> = throw UnsupportedOperationException(NO_FILE_SYSTEM)

internal actual fun readFileBytes(path: String, offset: Long, maxBytes: Int): ByteArray = throw UnsupportedOperationException(NO_FILE_SYSTEM)

internal actual fun fileSize(path: String): Long = throw UnsupportedOperationException(NO_FILE_SYSTEM)

internal actual fun deleteRecursively(path: String): Unit = throw UnsupportedOperationException(NO_FILE_SYSTEM)

internal actual fun writeFileBytes(path: String, bytes: ByteArray, append: Boolean): Unit = throw UnsupportedOperationException(NO_FILE_SYSTEM)

internal actual fun moveReplacing(source: String, target: String): Unit = throw UnsupportedOperationException(NO_FILE_SYSTEM)

// The web has no file system, so there is no symbolic link to lead anywhere.
internal actual fun resolvesInside(path: String, root: String): Boolean = true

internal actual fun isSymbolicLink(path: String): Boolean = throw UnsupportedOperationException(NO_FILE_SYSTEM)

internal actual fun isDirectory(path: String): Boolean = throw UnsupportedOperationException(NO_FILE_SYSTEM)

private const val NO_FILE_SYSTEM = "the web has no file system to browse"
