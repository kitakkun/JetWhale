package com.kitakkun.jetwhale.plugins.storage.protocol

import kotlinx.serialization.Serializable

/** The most bytes one [ReadFile] returns; a larger request is truncated to this. */
const val MAX_FILE_READ_BYTES: Int = 1024 * 1024

/**
 * A directory the app lets the host browse.
 *
 * @property name Identifies the root in requests, and labels it in the UI.
 * @property absolutePath Where the root is on the device, for display.
 */
@Serializable
data class FileRootInfo(
    val name: String,
    val absolutePath: String,
)

/**
 * One file or directory of a [DirectoryListing].
 *
 * @property sizeBytes The file's size; 0 for a directory.
 * @property lastModifiedEpochMillis When the entry last changed, or null when the platform does not say.
 */
@Serializable
data class FileEntry(
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModifiedEpochMillis: Long?,
)

/** A key-value store the app lets the host read: a SharedPreferences file, `NSUserDefaults`, `localStorage`. */
@Serializable
data class KeyValueStoreInfo(
    val name: String,
)

/**
 * One entry of a key-value store.
 *
 * @property value The value as text; a collection is written as its elements joined by ", ".
 * @property type The value's type as the store knows it — "String", "Int", "Boolean" and so on.
 */
@Serializable
data class KeyValueEntry(
    val key: String,
    val value: String,
    val type: String,
)
