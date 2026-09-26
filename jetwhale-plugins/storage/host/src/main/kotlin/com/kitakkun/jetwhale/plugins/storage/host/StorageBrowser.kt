package com.kitakkun.jetwhale.plugins.storage.host

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kitakkun.jetwhale.plugins.storage.protocol.DirectoryMeasurement
import com.kitakkun.jetwhale.plugins.storage.protocol.FileEntry
import com.kitakkun.jetwhale.plugins.storage.protocol.FileRootInfo
import com.kitakkun.jetwhale.plugins.storage.protocol.KeyValueStoreContent
import com.kitakkun.jetwhale.plugins.storage.protocol.KeyValueStoreInfo
import com.kitakkun.jetwhale.plugins.storage.protocol.StorageLocations
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import kotlin.io.encoding.Base64

/** How much of a file the UI reads to preview it; the rest is left to a tool that pages through it. */
private const val PREVIEW_BYTES = 256 * 1024

/**
 * How many entries expanding a whole subtree lists before it stops. An app's data directory can
 * hold thousands of cache entries, each directory among them one more request to the app.
 */
private const val SUBTREE_ENTRY_LIMIT = 500

internal data class StorageStatus(val message: String, val isError: Boolean)

/** The storage the inspector's UI shows, and what the user does to it. */
internal interface StorageInspectorActions {
    fun refresh()

    /** Shows [row] in the detail pane; a file is read for its preview, a directory is opened. */
    fun select(row: FileTreeRow)

    fun toggleDirectory(location: FileLocation)

    /** Collapses [location] with everything below it, or expands it and every directory below it. */
    fun toggleSubtree(location: FileLocation)

    fun delete(location: FileLocation)

    /** Reads the whole file at [location], not just the previewed part, and writes it to [target]. */
    fun saveFile(location: FileLocation, target: File)

    /**
     * Writes [source] to [target] in the app. Replacing a file that is already there waits for
     * [confirmUpload]; see `StorageBrowser.pendingUpload`.
     */
    fun requestUpload(target: FileLocation, source: File)

    fun confirmUpload()

    fun cancelUpload()

    /** Adds up the size of the directory at [location] and everything below it. */
    fun measureDirectory(location: FileLocation)

    /** Computes the SHA-256 of the whole file at [location]. */
    fun computeSha256(location: FileLocation)

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

    /** The size of the selected directory, once asked for; any other selection clears it. */
    var directoryMeasurement: DirectoryMeasurement? by mutableStateOf(null)
        private set

    /** The SHA-256 of the selected file as hex, once asked for; any other selection clears it. */
    var fileSha256: String? by mutableStateOf(null)
        private set

    var selectedStore: String? by mutableStateOf(null)
        private set

    var storeContent: KeyValueStoreContent? by mutableStateOf(null)
        private set

    var status: StorageStatus? by mutableStateOf(null)
        private set

    /** An upload that would replace an existing file, waiting for the user to confirm it. */
    var pendingUpload: PendingUpload? by mutableStateOf(null)
        private set

    /** Loads the locations, then everything the user had open, so a reconnect restores the view. */
    suspend fun load() {
        val loaded = client.locations()
        // Sizes and contents may have changed since they were computed.
        directoryMeasurement = null
        fileSha256 = null
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
        val nextStore = selectedStore?.takeIf(storeNames::contains) ?: storeNames.firstOrNull()
        if (nextStore != selectedStore) {
            selectedStore = nextStore
            storeContent = null
        }
        nextStore?.let { loadStore(it) }
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

    override fun toggleSubtree(location: FileLocation) {
        if (location in expanded) {
            expanded = expanded.filterNot(location::contains).toSet()
            return
        }
        launchReporting { expandSubtree(location) }
    }

    /** Expands directories breadth first, so a stop at the limit leaves the upper levels complete. */
    private suspend fun expandSubtree(location: FileLocation) {
        val pending = ArrayDeque(listOf(location))
        var listed = 0
        while (pending.isNotEmpty()) {
            if (listed >= SUBTREE_ENTRY_LIMIT) {
                status = StorageStatus(message = "Stopped expanding ${location.name} after $listed entries; open the rest a level at a time.", isError = false)
                return
            }
            val directory = pending.removeFirst()
            expanded = expanded + directory
            loadDirectory(directory)
            val entries = children[directory].orEmpty()
            listed += entries.size
            entries.filter(FileEntry::isDirectory).mapTo(pending) { directory.child(it.name) }
        }
    }

    override fun select(row: FileTreeRow) {
        selectedLocation = row.location
        loadedFile = null
        directoryMeasurement = null
        fileSha256 = null
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

    override fun saveFile(location: FileLocation, target: File) = launchReporting {
        try {
            val error = copyFileTo(location, target)
            status = when (error) {
                null -> StorageStatus(message = "Saved ${location.name} to ${target.absolutePath}.", isError = false)
                else -> StorageStatus(message = error, isError = true)
            }
        } catch (e: IOException) {
            status = StorageStatus(message = "Could not write ${target.absolutePath}: ${e.message}", isError = true)
        }
    }

    /** Copies the file page by page; returns the agent's error, if any, after removing the partial copy. */
    private suspend fun copyFileTo(location: FileLocation, target: File): String? {
        val error = target.outputStream().use { output -> client.readWholeFile(location, output::write) }
        if (error != null) target.delete()
        return error
    }

    override fun requestUpload(target: FileLocation, source: File) = launchReporting {
        val parent = FileLocation(target.rootName, target.path.dropLast(1))
        val listing = client.listDirectory(parent)
        // Without a listing there is no telling whether the upload would replace a file unasked.
        listing.error?.let { error ->
            status = StorageStatus(message = error, isError = true)
            return@launchReporting
        }
        val exists = listing.entries.any { it.name == target.name }
        if (exists) pendingUpload = PendingUpload(target, source) else upload(target, source)
    }

    override fun confirmUpload() {
        val upload = pendingUpload ?: return
        pendingUpload = null
        launchReporting { upload(upload.target, upload.source) }
    }

    override fun cancelUpload() {
        pendingUpload = null
    }

    private suspend fun upload(target: FileLocation, source: File) {
        val error = try {
            RandomAccessFile(source, "r").use { input ->
                val total = input.length()
                client.writeWholeFile(
                    location = target,
                    totalSizeBytes = total,
                    readChunk = { offset, size ->
                        input.seek(offset)
                        ByteArray(size).also(input::readFully)
                    },
                    onProgress = { sent ->
                        if (sent < total) status = StorageStatus(message = "Uploading ${source.name}… $sent of $total bytes", isError = false)
                    },
                )
            }
        } catch (e: IOException) {
            "Could not read ${source.absolutePath}: ${e.message}"
        }
        if (error != null) {
            status = StorageStatus(message = error, isError = true)
            return
        }
        loadDirectory(FileLocation(target.rootName, target.path.dropLast(1)))
        if (selectedLocation == target) loadFile(target)
        status = StorageStatus(
            message = "Uploaded ${source.name} as ${target.name}. Apps that keep the file open (DataStore, SQLite) may not see the change until they restart.",
            isError = false,
        )
    }

    override fun measureDirectory(location: FileLocation) = launchReporting {
        val measurement = client.measureDirectory(location)
        // The user may have picked another entry while the walk was running; its outcome, failure
        // included, no longer belongs on screen.
        if (selectedLocation != location) return@launchReporting
        when (val error = measurement.error) {
            null -> directoryMeasurement = measurement
            else -> status = StorageStatus(message = error, isError = true)
        }
    }

    override fun computeSha256(location: FileLocation) = launchReporting {
        val digest = client.sha256Of(location)
        if (selectedLocation != location) return@launchReporting
        when (val error = digest.error) {
            null -> fileSha256 = digest.sha256Hex
            else -> status = StorageStatus(message = error, isError = true)
        }
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
            null -> StorageStatus(message = "Deleted $key from $storeName.", isError = false)
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

/** An upload of [source] that would replace the existing file at [target]. */
internal data class PendingUpload(val target: FileLocation, val source: File)
