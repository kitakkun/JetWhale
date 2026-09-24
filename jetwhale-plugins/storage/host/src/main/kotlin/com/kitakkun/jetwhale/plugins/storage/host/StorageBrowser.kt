package com.kitakkun.jetwhale.plugins.storage.host

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kitakkun.jetwhale.plugins.storage.protocol.FileEntry
import com.kitakkun.jetwhale.plugins.storage.protocol.FileRootInfo
import com.kitakkun.jetwhale.plugins.storage.protocol.KeyValueStoreContent
import com.kitakkun.jetwhale.plugins.storage.protocol.KeyValueStoreInfo
import com.kitakkun.jetwhale.plugins.storage.protocol.StorageLocations
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.io.encoding.Base64

/** How much of a file the UI reads to preview it; the rest is left to a tool that pages through it. */
private const val PREVIEW_BYTES = 256 * 1024

internal data class StorageStatus(val message: String, val isError: Boolean)

/** The storage the inspector's UI shows, and what the user does to it. */
internal interface StorageInspectorActions {
    fun refresh()

    /** Shows [row] in the detail pane; a file is read for its preview, a directory is opened. */
    fun select(row: FileTreeRow)

    fun toggleDirectory(location: FileLocation)

    fun delete(location: FileLocation)

    fun selectStore(storeName: String)

    fun removeKey(storeName: String, key: String)
}

/**
 * What the inspector's UI has loaded from the app: the file tree as far as the user has opened it,
 * the file being previewed, and the key-value store being read. Every change goes through [client]
 * on [scope], and a failure lands in [status] rather than being thrown.
 */
@Stable
internal class StorageBrowser(
    private val client: StorageClient,
    private val scope: CoroutineScope,
) : StorageInspectorActions {
    var locations: StorageLocations? by mutableStateOf(null)
        private set

    private val children = mutableStateMapOf<FileLocation, List<FileEntry>>()
    private var expanded: Set<FileLocation> by mutableStateOf(emptySet())

    val treeRows: List<FileTreeRow>
        get() = flattenFileTree(locations?.fileRoots.orEmpty(), children, expanded)

    private var selectedLocation: FileLocation? by mutableStateOf(null)

    /** The row in the detail pane, looked up again after each reload so its size and date stay current. */
    val selectedRow: FileTreeRow?
        get() = selectedLocation?.let { location -> treeRows.firstOrNull { it.location == location } }

    var loadedFile: LoadedFile? by mutableStateOf(null)
        private set

    var selectedStore: String? by mutableStateOf(null)
        private set

    var storeContent: KeyValueStoreContent? by mutableStateOf(null)
        private set

    var status: StorageStatus? by mutableStateOf(null)
        private set

    /** Loads the locations, then everything the user had open, so a reconnect restores the view. */
    suspend fun load() {
        val loaded = client.locations()
        locations = loaded
        val rootNames = loaded.fileRoots.map(FileRootInfo::name).toSet()
        expanded = expanded.filterTo(mutableSetOf()) { it.rootName in rootNames }
        children.keys.retainAll(expanded)
        expanded.forEach { loadDirectory(it) }
        val selected = selectedRow
        if (selected == null) {
            selectedLocation = null
            loadedFile = null
        } else if (!selected.isDirectory) {
            loadFile(selected.location)
        }
        val storeNames = loaded.keyValueStores.map(KeyValueStoreInfo::name)
        selectedStore = selectedStore?.takeIf(storeNames::contains) ?: storeNames.firstOrNull()
        selectedStore?.let { loadStore(it) }
    }

    override fun refresh() = launchReporting {
        load()
        status = StorageStatus(message = "Reloaded from the app.", isError = false)
    }

    override fun toggleDirectory(location: FileLocation) {
        if (location in expanded) {
            expanded = expanded - location
            return
        }
        expanded = expanded + location
        launchReporting { loadDirectory(location) }
    }

    override fun select(row: FileTreeRow) {
        selectedLocation = row.location
        loadedFile = null
        when {
            !row.isDirectory -> launchReporting { loadFile(row.location) }
            !row.expanded -> toggleDirectory(row.location)
        }
    }

    override fun delete(location: FileLocation) = launchReporting {
        val error = client.delete(location).error
        if (error != null) {
            status = StorageStatus(message = error, isError = true)
            return@launchReporting
        }
        if (selectedLocation?.let(location::contains) == true) {
            selectedLocation = null
            loadedFile = null
        }
        expanded = expanded.filterNot(location::contains).toSet()
        loadDirectory(FileLocation(location.rootName, location.path.dropLast(1)))
        status = StorageStatus(message = "Deleted ${location.name}.", isError = false)
    }

    override fun selectStore(storeName: String) {
        selectedStore = storeName
        storeContent = null
        launchReporting { loadStore(storeName) }
    }

    override fun removeKey(storeName: String, key: String) = launchReporting {
        val error = client.removeKeyValue(storeName, key).error
        loadStore(storeName)
        status = when (error) {
            null -> StorageStatus(message = "Removed $key from $storeName.", isError = false)
            else -> StorageStatus(message = error, isError = true)
        }
    }

    private suspend fun loadStore(storeName: String) {
        val content = client.readKeyValueStore(storeName)
        // The user may have picked another store while this one was in flight.
        if (selectedStore == storeName) storeContent = content
    }

    private suspend fun loadDirectory(location: FileLocation) {
        val listing = client.listDirectory(location)
        listing.error?.let { status = StorageStatus(message = it, isError = true) }
        children[location] = listing.entries
    }

    private suspend fun loadFile(location: FileLocation) {
        val content = client.readFile(location, offset = 0, maxBytes = PREVIEW_BYTES)
        // The user may have picked another file while this one was in flight.
        if (selectedLocation != location) return
        val error = content.error
        if (error != null) {
            status = StorageStatus(message = error, isError = true)
            return
        }
        loadedFile = LoadedFile(location = location, bytes = Base64.decode(content.contentBase64), totalSizeBytes = content.totalSizeBytes)
    }

    private fun launchReporting(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (e: JetWhaleMessagingException) {
                status = StorageStatus(message = "Failed to reach the app: ${e.message}", isError = true)
            }
        }
    }
}
