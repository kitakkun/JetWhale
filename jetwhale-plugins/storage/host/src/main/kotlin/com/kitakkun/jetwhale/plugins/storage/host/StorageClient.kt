package com.kitakkun.jetwhale.plugins.storage.host

import com.kitakkun.jetwhale.plugins.storage.protocol.DirectoryListing
import com.kitakkun.jetwhale.plugins.storage.protocol.DirectoryMeasurement
import com.kitakkun.jetwhale.plugins.storage.protocol.FileContent
import com.kitakkun.jetwhale.plugins.storage.protocol.KeyValueStoreContent
import com.kitakkun.jetwhale.plugins.storage.protocol.StorageLocations
import com.kitakkun.jetwhale.plugins.storage.protocol.StorageOperationResult

/**
 * The agent's storage as the UI and the MCP commands reach it; the host plugin is the only real
 * implementation. Each call throws `JetWhaleMessagingException` when the app cannot be reached.
 */
internal interface StorageClient {
    suspend fun locations(): StorageLocations

    suspend fun listDirectory(location: FileLocation): DirectoryListing

    suspend fun readFile(location: FileLocation, offset: Long, maxBytes: Int): FileContent

    suspend fun delete(location: FileLocation): StorageOperationResult

    /** Sends one chunk of an upload; see `WriteFileChunk` for how the agent assembles them. */
    suspend fun writeFileChunk(location: FileLocation, uploadId: String, offset: Long, bytes: ByteArray, isLast: Boolean): StorageOperationResult

    suspend fun measureDirectory(location: FileLocation): DirectoryMeasurement

    suspend fun readKeyValueStore(storeName: String): KeyValueStoreContent

    suspend fun removeKeyValue(storeName: String, key: String): StorageOperationResult
}
