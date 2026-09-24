package com.kitakkun.jetwhale.plugins.storage.protocol

import com.kitakkun.jetwhale.protocol.messaging.JetWhaleRequest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The pluginId shared by the Storage Inspector agent and host plugins. */
const val STORAGE_PLUGIN_ID: String = "com.kitakkun.jetwhale.storage"

/**
 * Asks the agent which file roots and key-value stores the app exposes. The answer is computed
 * afresh each time, so a store the app created since the last request shows up.
 */
@SerialName("storage/get_locations")
@Serializable
data object GetStorageLocations : JetWhaleRequest<StorageLocations>

@SerialName("storage/locations")
@Serializable
data class StorageLocations(
    val fileRoots: List<FileRootInfo>,
    val keyValueStores: List<KeyValueStoreInfo>,
)

/**
 * Lists the directory at [path] under the file root named [rootName].
 *
 * @property path The directory's segments below the root; empty for the root itself. The agent
 *   refuses any segment that would step outside the root.
 */
@SerialName("storage/list_directory")
@Serializable
data class ListDirectory(
    val rootName: String,
    val path: List<String>,
) : JetWhaleRequest<DirectoryListing>

/** Reply to [ListDirectory]. [error] is null exactly when [entries] is the directory's content. */
@SerialName("storage/directory_listing")
@Serializable
data class DirectoryListing(
    val entries: List<FileEntry>,
    val error: String?,
)

/**
 * Reads up to [maxBytes] bytes of the file at [path], starting at [offset]. The agent caps
 * [maxBytes] at [MAX_FILE_READ_BYTES], so a large file is read in pages.
 */
@SerialName("storage/read_file")
@Serializable
data class ReadFile(
    val rootName: String,
    val path: List<String>,
    val offset: Long,
    val maxBytes: Int,
) : JetWhaleRequest<FileContent>

/**
 * Reply to [ReadFile].
 *
 * @property contentBase64 The bytes read, Base64-encoded; empty when [error] is set.
 * @property totalSizeBytes The whole file's size, so the reader knows whether there is more.
 */
@SerialName("storage/file_content")
@Serializable
data class FileContent(
    val contentBase64: String,
    val totalSizeBytes: Long,
    val error: String?,
)

/** Deletes the file or directory (with everything in it) at [path]. The root itself cannot be deleted. */
@SerialName("storage/delete_file_entry")
@Serializable
data class DeleteFileEntry(
    val rootName: String,
    val path: List<String>,
) : JetWhaleRequest<StorageOperationResult>

/** Reads every entry of the key-value store named [storeName]. */
@SerialName("storage/read_key_value_store")
@Serializable
data class ReadKeyValueStore(
    val storeName: String,
) : JetWhaleRequest<KeyValueStoreContent>

/** Reply to [ReadKeyValueStore]. [error] is null exactly when [entries] is the store's content. */
@SerialName("storage/key_value_store_content")
@Serializable
data class KeyValueStoreContent(
    val entries: List<KeyValueEntry>,
    val error: String?,
)

/** Removes [key] from the key-value store named [storeName]. */
@SerialName("storage/remove_key_value")
@Serializable
data class RemoveKeyValue(
    val storeName: String,
    val key: String,
) : JetWhaleRequest<StorageOperationResult>

/** Reply to a request that changes storage: [error] is null when the change was made. */
@SerialName("storage/operation_result")
@Serializable
data class StorageOperationResult(
    val error: String?,
)
