package com.kitakkun.jetwhale.plugins.storage.agent

import com.kitakkun.jetwhale.agent.sdk.JetWhaleAgentPlugin
import com.kitakkun.jetwhale.plugins.storage.protocol.DeleteFileEntry
import com.kitakkun.jetwhale.plugins.storage.protocol.DirectoryListing
import com.kitakkun.jetwhale.plugins.storage.protocol.DirectoryMeasurement
import com.kitakkun.jetwhale.plugins.storage.protocol.FileContent
import com.kitakkun.jetwhale.plugins.storage.protocol.FileEntry
import com.kitakkun.jetwhale.plugins.storage.protocol.FileRootInfo
import com.kitakkun.jetwhale.plugins.storage.protocol.GetStorageLocations
import com.kitakkun.jetwhale.plugins.storage.protocol.KeyValueEntry
import com.kitakkun.jetwhale.plugins.storage.protocol.KeyValueStoreContent
import com.kitakkun.jetwhale.plugins.storage.protocol.KeyValueStoreInfo
import com.kitakkun.jetwhale.plugins.storage.protocol.ListDirectory
import com.kitakkun.jetwhale.plugins.storage.protocol.MAX_FILE_READ_BYTES
import com.kitakkun.jetwhale.plugins.storage.protocol.MeasureDirectory
import com.kitakkun.jetwhale.plugins.storage.protocol.ReadFile
import com.kitakkun.jetwhale.plugins.storage.protocol.ReadKeyValueStore
import com.kitakkun.jetwhale.plugins.storage.protocol.RemoveKeyValue
import com.kitakkun.jetwhale.plugins.storage.protocol.STORAGE_PLUGIN_ID
import com.kitakkun.jetwhale.plugins.storage.protocol.StorageLocations
import com.kitakkun.jetwhale.plugins.storage.protocol.StorageOperationResult
import com.kitakkun.jetwhale.plugins.storage.protocol.WriteFileChunk
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessageHandlers
import com.kitakkun.jetwhale.protocol.messaging.reply
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64

/**
 * How many entries measuring a directory counts before it stops. Enough for any cache directory an
 * app would reasonably keep, while a runaway tree still answers in bounded time.
 */
private const val MEASURED_ENTRY_LIMIT = 100_000

/**
 * Agent plugin that lets the host browse the app's files and read its key-value stores.
 *
 * Most apps want [platformDefaults]: the app's own directories and the platform's preferences
 * store, found without any configuration.
 *
 * ```kotlin
 * startJetWhale { plugins { register(JetWhaleStorageAgentPlugin.platformDefaults()) } }
 * ```
 *
 * To add a directory or a store of the app's own, build on the defaults:
 *
 * ```kotlin
 * JetWhaleStorageAgentPlugin(
 *     fileRoots = { FileRoot.platformDefaults() + FileRoot(name = "Exports", path = exportDir) },
 *     keyValueStores = { KeyValueStore.platformDefaults() + MySettingsStore },
 * )
 * ```
 *
 * Both lists are asked for again on every host request, so a store the app creates after startup
 * shows up without registering the plugin again.
 */
class JetWhaleStorageAgentPlugin(
    private val fileRoots: () -> List<FileRoot>,
    private val keyValueStores: () -> List<KeyValueStore>,
) : JetWhaleAgentPlugin() {
    override val pluginId: String get() = STORAGE_PLUGIN_ID
    override val pluginVersion: String get() = "1.0.0"

    override fun JetWhaleMessageHandlers.configure() {
        onRequest { _: GetStorageLocations -> reply(locations()) }
        onRequest { request: ListDirectory -> reply(listDirectory(request)) }
        onRequest { request: ReadFile -> reply(readFile(request)) }
        onRequest { request: DeleteFileEntry -> reply(deleteFileEntry(request)) }
        onRequest { request: WriteFileChunk -> reply(writeFileChunk(request)) }
        onRequest { request: MeasureDirectory -> reply(measureDirectory(request)) }
        onRequest { request: ReadKeyValueStore -> reply(readKeyValueStore(request)) }
        onRequest { request: RemoveKeyValue -> reply(removeKeyValue(request)) }
    }

    private fun locations(): StorageLocations = StorageLocations(
        fileRoots = fileRoots().map { FileRootInfo(name = it.name, absolutePath = it.path) },
        keyValueStores = keyValueStores().map { KeyValueStoreInfo(name = it.name) },
    )

    // Each operation below turns any failure into the reply's error, so the host sees why instead
    // of a request that fails without a reason.

    @Suppress("KOTRAIL_CATCH_TOO_BROAD")
    private fun listDirectory(request: ListDirectory): DirectoryListing = try {
        val entries = listDirectoryEntries(resolve(request.rootName, request.path))
        DirectoryListing(entries = entries.sortedWith(compareBy({ !it.isDirectory }, FileEntry::name)), error = null)
    } catch (e: Exception) {
        DirectoryListing(entries = emptyList(), error = e.describe())
    }

    @Suppress("KOTRAIL_CATCH_TOO_BROAD")
    private fun readFile(request: ReadFile): FileContent = try {
        val path = resolve(request.rootName, request.path)
        val bytes = readFileBytes(path, offset = request.offset, maxBytes = request.maxBytes.coerceIn(0, MAX_FILE_READ_BYTES))
        FileContent(contentBase64 = Base64.encode(bytes), totalSizeBytes = fileSize(path), error = null)
    } catch (e: Exception) {
        FileContent(contentBase64 = "", totalSizeBytes = 0, error = e.describe())
    }

    @Suppress("KOTRAIL_CATCH_TOO_BROAD")
    private fun deleteFileEntry(request: DeleteFileEntry): StorageOperationResult = try {
        require(request.path.isNotEmpty()) { "a file root cannot be deleted, only what is inside it" }
        deleteRecursively(resolve(request.rootName, request.path))
        StorageOperationResult(error = null)
    } catch (e: Exception) {
        StorageOperationResult(error = e.describe())
    }

    @Suppress("KOTRAIL_CATCH_TOO_BROAD")
    private fun writeFileChunk(request: WriteFileChunk): StorageOperationResult = try {
        val paths = fileRoot(request.rootName).uploadPaths(request.path, request.uploadId)
        receiveUploadChunk(
            stagingPath = paths.staging,
            targetPath = paths.target,
            offset = request.offset,
            bytes = Base64.decode(request.contentBase64),
            isLast = request.isLast,
        )
        StorageOperationResult(error = null)
    } catch (e: Exception) {
        StorageOperationResult(error = e.describe())
    }

    @Suppress("KOTRAIL_CATCH_TOO_BROAD")
    private fun measureDirectory(request: MeasureDirectory): DirectoryMeasurement = try {
        measureDirectoryTree(resolve(request.rootName, request.path), entryLimit = MEASURED_ENTRY_LIMIT)
    } catch (e: Exception) {
        DirectoryMeasurement(totalSizeBytes = 0, fileCount = 0, directoryCount = 0, truncated = false, error = e.describe())
    }

    @Suppress("KOTRAIL_CATCH_TOO_BROAD")
    private suspend fun readKeyValueStore(request: ReadKeyValueStore): KeyValueStoreContent = try {
        KeyValueStoreContent(entries = keyValueStore(request.storeName).entries().sortedBy(KeyValueEntry::key), error = null)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        KeyValueStoreContent(entries = emptyList(), error = e.describe())
    }

    @Suppress("KOTRAIL_CATCH_TOO_BROAD")
    private suspend fun removeKeyValue(request: RemoveKeyValue): StorageOperationResult = try {
        keyValueStore(request.storeName).remove(request.key)
        StorageOperationResult(error = null)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        StorageOperationResult(error = e.describe())
    }

    private fun resolve(rootName: String, segments: List<String>): String = fileRoot(rootName).resolve(segments)

    private fun fileRoot(name: String): FileRoot = fileRoots().firstOrNull { it.name == name }
        ?: throw IllegalArgumentException("no file root is named '$name'")

    private fun keyValueStore(name: String): KeyValueStore = keyValueStores().firstOrNull { it.name == name }
        ?: throw IllegalArgumentException("no key-value store is named '$name'")

    companion object {
        /** A plugin that shows [FileRoot.platformDefaults] and [KeyValueStore.platformDefaults]. */
        fun platformDefaults(): JetWhaleStorageAgentPlugin = JetWhaleStorageAgentPlugin(
            fileRoots = { FileRoot.platformDefaults() },
            keyValueStores = { KeyValueStore.platformDefaults() },
        )
    }
}

private fun Exception.describe(): String = message ?: this::class.simpleName ?: "unknown error"
