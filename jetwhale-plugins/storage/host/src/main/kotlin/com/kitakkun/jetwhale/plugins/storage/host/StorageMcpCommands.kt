package com.kitakkun.jetwhale.plugins.storage.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.storage.protocol.STORAGE_PLUGIN_ID
import kotlinx.serialization.json.Json

internal const val TOOL_PREFIX = STORAGE_PLUGIN_ID

/**
 * Each of the Storage Inspector's MCP commands, built once, so the UI can ring the list or pane that shows
 * what a running command reads or changes.
 */
@OptIn(ExperimentalJetWhaleApi::class)
internal class StorageMcpCommands(client: StorageClient) {
    val listLocations = ListStorageLocationsCommand(client)
    val listDirectory = ListDirectoryCommand(client)
    val readFile = ReadFileCommand(client)
    val deleteFileEntry = DeleteFileEntryCommand(client)
    val readKeyValueStore = ReadKeyValueStoreCommand(client)
    val removeKeyValue = RemoveKeyValueCommand(client)
    val measureDirectory = MeasureDirectoryCommand(client)
    val hashFile = HashFileCommand(client)

    val all: List<JetWhaleMcpCommand> = listOf(listLocations, listDirectory, readFile, deleteFileEntry, readKeyValueStore, removeKeyValue, measureDirectory, hashFile)
}

internal val McpJson: Json = Json { encodeDefaults = true }

internal const val PATH_ARGUMENT_DESCRIPTION = "Path below the root, with '/' between segments, e.g. 'files/datastore/settings.preferences_pb'."

/** A tool's `root` and `path` arguments as the location they name. */
internal fun fileLocationOf(rootName: String, path: String?): FileLocation = FileLocation(rootName = rootName, path = path.orEmpty().split('/').filter(String::isNotEmpty))
