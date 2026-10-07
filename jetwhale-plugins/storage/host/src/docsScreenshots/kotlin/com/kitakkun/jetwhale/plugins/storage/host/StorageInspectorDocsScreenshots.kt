package com.kitakkun.jetwhale.plugins.storage.host

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.plugins.storage.protocol.FileEntry
import com.kitakkun.jetwhale.plugins.storage.protocol.FileRootInfo
import com.kitakkun.jetwhale.plugins.storage.protocol.KeyValueEntry
import com.kitakkun.jetwhale.plugins.storage.protocol.KeyValueStoreContent
import com.kitakkun.jetwhale.plugins.storage.protocol.KeyValueStoreInfo
import com.kitakkun.jetwhale.plugins.storage.protocol.StorageLocations
import com.kitakkun.jetwhale.tools.docsscreenshots.DocsShot
import com.kitakkun.jetwhale.tools.docsscreenshots.DocsShotRecorder
import com.kitakkun.jetwhale.tools.docsscreenshots.InMemoryPluginStorage
import com.kitakkun.jetwhale.tools.docsscreenshots.PluginSceneSurface
import com.kitakkun.jetwhale.tools.docsscreenshots.mouseClickThenMovePointerAway
import com.kitakkun.jetwhale.tools.docsscreenshots.onSurface
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.Test

/**
 * [StorageInspectorScreen] rather than its Root: the Root only binds a live [StorageBrowser], which
 * needs the app on the other end, to the screen. The tree is flattened by [flattenFileTree], as the
 * browser does.
 */
@OptIn(ExperimentalTestApi::class)
class StorageInspectorDocsScreenshots {
    private val recorder = DocsShotRecorder.forImagesDirectoryProperty()

    @Test
    fun `the files with a Preferences DataStore file previewed`() = recorder.record(
        DocsShot(page = PAGE, name = "files", surfaceSize = DpSize(860.dp, 480.dp), density = 1.6f, displayWidth = 688),
    ) { darkTheme ->
        setStorageInspector(darkTheme, tab = StorageTab.Files)
        onSurface()
    }

    @Test
    fun `a key-value store with an entry selected`() = recorder.record(
        DocsShot(page = PAGE, name = "key-value", surfaceSize = DpSize(860.dp, 300.dp), density = 1.6f, displayWidth = 688),
    ) { darkTheme ->
        setStorageInspector(darkTheme, tab = StorageTab.KeyValue)
        onNodeWithText("theme").mouseClickThenMovePointerAway()
        onSurface()
    }
}

@OptIn(ExperimentalTestApi::class)
private fun SkikoComposeUiTest.setStorageInspector(darkTheme: Boolean, tab: StorageTab) {
    val treeRows = flattenFileTree(roots = FILE_ROOTS, children = DIRECTORY_CONTENT, expanded = setOf(FILES, DATASTORE))
    val selectedRow = treeRows.single { it.location == SETTINGS_FILE }
    setContent {
        PluginSceneSurface(darkTheme = darkTheme, storage = InMemoryPluginStorage(emptyMap())) {
            StorageInspectorScreen(
                tab = tab,
                locations = StorageLocations(fileRoots = FILE_ROOTS, keyValueStores = STORES),
                treeRows = treeRows,
                selectedRow = selectedRow,
                loadedFile = LoadedFile(location = SETTINGS_FILE, bytes = SETTINGS_FILE_BYTES, totalSizeBytes = SETTINGS_FILE_BYTES.size.toLong()),
                directoryMeasurement = null,
                fileSha256 = null,
                selectedStore = STORES.first().name,
                storeContent = KeyValueStoreContent(entries = PREFERENCES_ENTRIES, error = null),
                status = null,
                actions = NoStorageInspectorActions,
                onSelectTab = {},
            )
        }
    }
}

private object NoStorageInspectorActions : StorageInspectorActions {
    override fun refresh() = Unit

    override fun select(row: FileTreeRow) = Unit

    override fun toggleDirectory(location: FileLocation) = Unit

    override fun toggleSubtree(location: FileLocation) = Unit

    override fun delete(location: FileLocation) = Unit

    override fun saveFile(location: FileLocation, target: File) = Unit

    override fun measureDirectory(location: FileLocation) = Unit

    override fun computeSha256(location: FileLocation) = Unit

    override fun selectStore(storeName: String) = Unit

    override fun removeKey(storeName: String, key: String) = Unit
}

private const val PAGE = "storage-inspector"

private const val DATA_DIR = "/data/user/0/com.example.sampleapp"

/** 2026-10-01 09:00 UTC. */
private const val MODIFIED_AT = 1_790_845_200_000

private val FILE_ROOTS = listOf(
    FileRootInfo(name = "Data", absolutePath = DATA_DIR),
    FileRootInfo(name = "Files", absolutePath = "$DATA_DIR/files"),
    FileRootInfo(name = "Cache", absolutePath = "$DATA_DIR/cache"),
    FileRootInfo(name = "External cache", absolutePath = "/storage/emulated/0/Android/data/com.example.sampleapp/cache"),
)

private val FILES = FileLocation(rootName = "Files", path = emptyList())

private val DATASTORE = FileLocation(rootName = "Files", path = listOf("datastore"))

private val SETTINGS_FILE = FileLocation(rootName = "Files", path = listOf("datastore", "settings.preferences_pb"))

private val STORES = listOf(KeyValueStoreInfo(name = "settings"), KeyValueStoreInfo(name = "session"))

private val PREFERENCES_ENTRIES = listOf(
    KeyValueEntry(key = "launch_count", value = "12", type = "Int"),
    KeyValueEntry(key = "onboarding_done", value = "true", type = "Boolean"),
    KeyValueEntry(key = "theme", value = "dark", type = "String"),
)

private val SETTINGS_FILE_BYTES = preferencesDataStoreFile(
    "launch_count" to 12,
    "onboarding_done" to true,
    "theme" to "dark",
)

private val DIRECTORY_CONTENT = mapOf(
    FILES to listOf(
        directory(name = "datastore"),
        directory(name = "images"),
        file(name = "notes.txt", sizeBytes = 1_204),
    ),
    DATASTORE to listOf(
        file(name = "settings.preferences_pb", sizeBytes = SETTINGS_FILE_BYTES.size.toLong()),
    ),
)

private fun directory(name: String) = file(name = name, sizeBytes = 0).copy(isDirectory = true)

private fun file(name: String, sizeBytes: Long) = FileEntry(
    name = name,
    isDirectory = false,
    sizeBytes = sizeBytes,
    lastModifiedEpochMillis = MODIFIED_AT,
    isSymbolicLink = false,
    linkTarget = null,
    createdEpochMillis = MODIFIED_AT,
    readable = true,
    writable = true,
)

/**
 * The bytes Jetpack DataStore writes for these preferences: a `PreferenceMap` whose field 1 holds one
 * map entry per key, each with its key (1) and a `Value` (2) of boolean (1), integer (3) or string (5).
 */
private fun preferencesDataStoreFile(vararg preferences: Pair<String, Any>): ByteArray {
    val file = ByteArrayOutputStream()
    preferences.forEach { (key, value) ->
        val encodedValue = ByteArrayOutputStream()
        when (value) {
            is Boolean -> encodedValue.writeVarintField(field = 1, value = if (value) 1 else 0)
            is Int -> encodedValue.writeVarintField(field = 3, value = value)
            is String -> encodedValue.writeLengthDelimitedField(field = 5, bytes = value.encodeToByteArray())
            else -> error("no Preferences type for $value")
        }
        val entry = ByteArrayOutputStream()
        entry.writeLengthDelimitedField(field = 1, bytes = key.encodeToByteArray())
        entry.writeLengthDelimitedField(field = 2, bytes = encodedValue.toByteArray())
        file.writeLengthDelimitedField(field = 1, bytes = entry.toByteArray())
    }
    return file.toByteArray()
}

private fun ByteArrayOutputStream.writeVarintField(field: Int, value: Int) {
    writeVarint(field shl 3)
    writeVarint(value)
}

private fun ByteArrayOutputStream.writeLengthDelimitedField(field: Int, bytes: ByteArray) {
    writeVarint((field shl 3) or LENGTH_DELIMITED)
    writeVarint(bytes.size)
    write(bytes)
}

private fun ByteArrayOutputStream.writeVarint(value: Int) {
    var remaining = value
    while (remaining >= VARINT_CONTINUATION) {
        write((remaining and VARINT_PAYLOAD_MASK) or VARINT_CONTINUATION)
        remaining = remaining ushr VARINT_PAYLOAD_BITS
    }
    write(remaining)
}

private const val LENGTH_DELIMITED = 2

private const val VARINT_CONTINUATION = 0x80

private const val VARINT_PAYLOAD_MASK = 0x7F

private const val VARINT_PAYLOAD_BITS = 7
