package com.kitakkun.jetwhale.plugins.storage.host

import com.kitakkun.jetwhale.plugins.storage.protocol.DirectoryListing
import com.kitakkun.jetwhale.plugins.storage.protocol.DirectoryMeasurement
import com.kitakkun.jetwhale.plugins.storage.protocol.FileContent
import com.kitakkun.jetwhale.plugins.storage.protocol.FileEntry
import com.kitakkun.jetwhale.plugins.storage.protocol.FileRootInfo
import com.kitakkun.jetwhale.plugins.storage.protocol.KeyValueEntry
import com.kitakkun.jetwhale.plugins.storage.protocol.KeyValueStoreContent
import com.kitakkun.jetwhale.plugins.storage.protocol.KeyValueStoreInfo
import com.kitakkun.jetwhale.plugins.storage.protocol.StorageLocations
import com.kitakkun.jetwhale.plugins.storage.protocol.StorageOperationResult
import kotlin.io.encoding.Base64

/** An app whose storage is the maps below, answering the way the agent plugin does. */
internal class FakeStorageClient(
    private val directories: MutableMap<FileLocation, List<FileEntry>>,
    private val files: Map<FileLocation, ByteArray>,
    private val stores: MutableMap<String, List<KeyValueEntry>>,
) : StorageClient {
    val deleted = mutableListOf<FileLocation>()
    val fileReads = mutableListOf<Triple<FileLocation, Long, Int>>()

    override suspend fun locations(): StorageLocations = StorageLocations(
        fileRoots = directories.keys.filter { it.path.isEmpty() }.map { FileRootInfo(name = it.rootName, absolutePath = "/data/${it.rootName}") },
        keyValueStores = stores.keys.map(::KeyValueStoreInfo),
    )

    override suspend fun listDirectory(location: FileLocation): DirectoryListing = when (val entries = directories[location]) {
        null -> DirectoryListing(entries = emptyList(), error = "'${location.name}' is not a directory")
        else -> DirectoryListing(entries = entries, error = null)
    }

    override suspend fun readFile(location: FileLocation, offset: Long, maxBytes: Int): FileContent {
        fileReads += Triple(location, offset, maxBytes)
        val bytes = files[location] ?: return FileContent(contentBase64 = "", totalSizeBytes = 0, error = "'${location.name}' is not a file")
        val end = minOf(bytes.size.toLong(), offset + maxBytes).toInt()
        return FileContent(contentBase64 = Base64.encode(bytes.copyOfRange(offset.toInt(), end)), totalSizeBytes = bytes.size.toLong(), error = null)
    }

    override suspend fun delete(location: FileLocation): StorageOperationResult {
        deleted += location
        val parent = FileLocation(location.rootName, location.path.dropLast(1))
        directories[parent] = directories[parent].orEmpty().filterNot { it.name == location.name }
        return StorageOperationResult(error = null)
    }

    override suspend fun measureDirectory(location: FileLocation): DirectoryMeasurement {
        val entries = directories[location] ?: return DirectoryMeasurement(totalSizeBytes = 0, fileCount = 0, directoryCount = 0, truncated = false, error = "'${location.name}' is not a directory")
        val below = entries.filter(FileEntry::isDirectory).map { measureDirectory(location.child(it.name)) }
        return DirectoryMeasurement(
            totalSizeBytes = entries.filterNot(FileEntry::isDirectory).sumOf(FileEntry::sizeBytes) + below.sumOf(DirectoryMeasurement::totalSizeBytes),
            fileCount = entries.count { !it.isDirectory } + below.sumOf(DirectoryMeasurement::fileCount),
            directoryCount = entries.count(FileEntry::isDirectory) + below.sumOf(DirectoryMeasurement::directoryCount),
            truncated = false,
            error = null,
        )
    }

    override suspend fun readKeyValueStore(storeName: String): KeyValueStoreContent = when (val entries = stores[storeName]) {
        null -> KeyValueStoreContent(entries = emptyList(), error = "no key-value store is named '$storeName'")
        else -> KeyValueStoreContent(entries = entries, error = null)
    }

    override suspend fun removeKeyValue(storeName: String, key: String): StorageOperationResult {
        stores[storeName] = stores[storeName].orEmpty().filterNot { it.key == key }
        return StorageOperationResult(error = null)
    }
}

internal fun fileEntry(name: String, sizeBytes: Long): FileEntry = FileEntry(
    name = name,
    isDirectory = false,
    sizeBytes = sizeBytes,
    lastModifiedEpochMillis = null,
    isSymbolicLink = false,
    linkTarget = null,
    createdEpochMillis = null,
    readable = true,
    writable = true,
)

internal fun directoryEntry(name: String): FileEntry = fileEntry(name, sizeBytes = 0).copy(isDirectory = true)

internal fun location(rootName: String, vararg path: String): FileLocation = FileLocation(rootName, path.toList())
