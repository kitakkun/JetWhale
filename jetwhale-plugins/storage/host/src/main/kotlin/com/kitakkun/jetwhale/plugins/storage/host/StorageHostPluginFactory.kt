package com.kitakkun.jetwhale.plugins.storage.host

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginUi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCapablePlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.host.sdk.JetWhaleMessagingHostPlugin
import com.kitakkun.jetwhale.plugins.storage.protocol.DeleteFileEntry
import com.kitakkun.jetwhale.plugins.storage.protocol.DirectoryListing
import com.kitakkun.jetwhale.plugins.storage.protocol.DirectoryMeasurement
import com.kitakkun.jetwhale.plugins.storage.protocol.FileContent
import com.kitakkun.jetwhale.plugins.storage.protocol.GetStorageLocations
import com.kitakkun.jetwhale.plugins.storage.protocol.KeyValueStoreContent
import com.kitakkun.jetwhale.plugins.storage.protocol.ListDirectory
import com.kitakkun.jetwhale.plugins.storage.protocol.MeasureDirectory
import com.kitakkun.jetwhale.plugins.storage.protocol.ReadFile
import com.kitakkun.jetwhale.plugins.storage.protocol.ReadKeyValueStore
import com.kitakkun.jetwhale.plugins.storage.protocol.RemoveKeyValue
import com.kitakkun.jetwhale.plugins.storage.protocol.StorageLocations
import com.kitakkun.jetwhale.plugins.storage.protocol.StorageOperationResult
import com.kitakkun.jetwhale.plugins.storage.protocol.WriteFileChunk
import com.kitakkun.jetwhale.protocol.messaging.request
import kotlin.io.encoding.Base64

// Instantiated by the host via the fully-qualified name declared in plugin-manifest.json.
@Suppress("UNUSED")
class StorageHostPluginFactory : JetWhaleHostPluginFactory {
    override fun createPlugin(): JetWhaleHostPlugin = StorageHostPlugin()
}

@OptIn(ExperimentalJetWhaleApi::class)
private class StorageHostPlugin :
    JetWhaleMessagingHostPlugin(),
    JetWhaleHostPluginUi,
    JetWhaleMcpCapablePlugin,
    StorageClient {

    private val browser by lazy { StorageBrowser(client = this, scope = pluginScope) }

    // The agent pushes nothing, so the whole view is fetched once per connection.
    override suspend fun onPrepare() {
        browser.load()
    }

    override suspend fun locations(): StorageLocations = messenger.request(GetStorageLocations)

    override suspend fun listDirectory(location: FileLocation): DirectoryListing = messenger.request(ListDirectory(rootName = location.rootName, path = location.path))

    override suspend fun readFile(location: FileLocation, offset: Long, maxBytes: Int): FileContent = messenger.request(ReadFile(rootName = location.rootName, path = location.path, offset = offset, maxBytes = maxBytes))

    override suspend fun delete(location: FileLocation): StorageOperationResult = messenger.request(DeleteFileEntry(rootName = location.rootName, path = location.path))

    override suspend fun writeFileChunk(location: FileLocation, uploadId: String, offset: Long, bytes: ByteArray, isLast: Boolean): StorageOperationResult = messenger.request(
        WriteFileChunk(
            rootName = location.rootName,
            path = location.path,
            uploadId = uploadId,
            offset = offset,
            contentBase64 = Base64.encode(bytes),
            isLast = isLast,
        ),
    )

    override suspend fun measureDirectory(location: FileLocation): DirectoryMeasurement = messenger.request(MeasureDirectory(rootName = location.rootName, path = location.path))

    override suspend fun readKeyValueStore(storeName: String): KeyValueStoreContent = messenger.request(ReadKeyValueStore(storeName))

    override suspend fun removeKeyValue(storeName: String, key: String): StorageOperationResult = messenger.request(RemoveKeyValue(storeName = storeName, key = key))

    @Composable
    override fun Content() {
        StorageInspectorScreenRoot(browser)
    }

    override val mcpCommands: List<JetWhaleMcpCommand> = listOf(
        ListStorageLocationsCommand(this),
        ListDirectoryCommand(this),
        ReadFileCommand(this),
        DeleteFileEntryCommand(this),
        WriteFileCommand(this),
        ReadKeyValueStoreCommand(this),
        RemoveKeyValueCommand(this),
        MeasureDirectoryCommand(this),
        HashFileCommand(this),
    )
}
