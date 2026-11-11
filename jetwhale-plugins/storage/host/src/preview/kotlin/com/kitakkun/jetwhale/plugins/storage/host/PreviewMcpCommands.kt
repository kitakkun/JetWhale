package com.kitakkun.jetwhale.plugins.storage.host

import com.kitakkun.jetwhale.plugins.storage.protocol.DirectoryListing
import com.kitakkun.jetwhale.plugins.storage.protocol.DirectoryMeasurement
import com.kitakkun.jetwhale.plugins.storage.protocol.FileContent
import com.kitakkun.jetwhale.plugins.storage.protocol.KeyValueStoreContent
import com.kitakkun.jetwhale.plugins.storage.protocol.StorageLocations
import com.kitakkun.jetwhale.plugins.storage.protocol.StorageOperationResult

/** The inspector's commands for previews and docs screenshots, which draw the panes but never run a command. */
internal val previewMcpCommands = StorageMcpCommands(NoStorageClient)

private object NoStorageClient : StorageClient {
    override suspend fun locations(): StorageLocations = error("A preview has no app")

    override suspend fun listDirectory(location: FileLocation): DirectoryListing = error("A preview has no app")

    override suspend fun readFile(location: FileLocation, offset: Long, maxBytes: Int): FileContent = error("A preview has no app")

    override suspend fun delete(location: FileLocation): StorageOperationResult = error("A preview has no app")

    override suspend fun measureDirectory(location: FileLocation): DirectoryMeasurement = error("A preview has no app")

    override suspend fun readKeyValueStore(storeName: String): KeyValueStoreContent = error("A preview has no app")

    override suspend fun removeKeyValue(storeName: String, key: String): StorageOperationResult = error("A preview has no app")
}
