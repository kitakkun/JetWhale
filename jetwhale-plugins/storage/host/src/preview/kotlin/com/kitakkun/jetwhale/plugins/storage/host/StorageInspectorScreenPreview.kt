package com.kitakkun.jetwhale.plugins.storage.host

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.JwTone
import com.kitakkun.jetwhale.plugins.storage.protocol.DirectoryMeasurement
import com.kitakkun.jetwhale.plugins.storage.protocol.FileEntry
import com.kitakkun.jetwhale.plugins.storage.protocol.FileRootInfo
import com.kitakkun.jetwhale.plugins.storage.protocol.KeyValueEntry
import com.kitakkun.jetwhale.plugins.storage.protocol.KeyValueStoreContent
import com.kitakkun.jetwhale.plugins.storage.protocol.KeyValueStoreInfo
import com.kitakkun.jetwhale.plugins.storage.protocol.StorageLocations
import java.io.File

private val previewRoots = listOf(
    FileRootInfo(name = "Files", absolutePath = "/data/user/0/com.example.app/files"),
    FileRootInfo(name = "Cache", absolutePath = "/data/user/0/com.example.app/cache"),
)

private val previewNotes = FileEntry(
    name = "notes.txt",
    isDirectory = false,
    sizeBytes = 42,
    lastModifiedEpochMillis = 1_760_000_000_000,
    isSymbolicLink = false,
    linkTarget = null,
    createdEpochMillis = 1_750_000_000_000,
    readable = true,
    writable = true,
)

private val previewRows = listOf(
    FileTreeRow(location = FileLocation("Files", emptyList()), depth = 0, entry = null, expanded = true),
    FileTreeRow(
        location = FileLocation("Files", listOf("datastore")),
        depth = 1,
        entry = previewNotes.copy(name = "datastore", isDirectory = true, sizeBytes = 0, createdEpochMillis = null),
        expanded = false,
    ),
    FileTreeRow(location = FileLocation("Files", listOf("notes.txt")), depth = 1, entry = previewNotes, expanded = false),
    FileTreeRow(location = FileLocation("Cache", emptyList()), depth = 0, entry = null, expanded = false),
)

private val previewEntries = listOf(
    KeyValueEntry(key = "onboarded", value = "true", type = "Boolean"),
    KeyValueEntry(key = "userName", value = "kitakkun", type = "String"),
)

private object NoActions : StorageInspectorActions {
    override fun refresh() = Unit

    override fun select(row: FileTreeRow) = Unit

    override fun toggleDirectory(location: FileLocation) = Unit

    override fun toggleSubtree(location: FileLocation) = Unit

    override fun delete(location: FileLocation) = Unit

    override fun saveFile(location: FileLocation, target: File) = Unit

    override fun requestUpload(target: FileLocation, source: File) = Unit

    override fun confirmUpload() = Unit

    override fun cancelUpload() = Unit

    override fun measureDirectory(location: FileLocation) = Unit

    override fun requestZipDownload(location: FileLocation, target: File) = Unit

    override fun confirmZipDownload() = Unit

    override fun cancelZipDownload() = Unit

    override fun computeSha256(location: FileLocation) = Unit

    override fun selectStore(storeName: String) = Unit

    override fun removeKey(storeName: String, key: String) = Unit
}

@Preview
@Composable
private fun StorageInspectorScreenPreview() {
    JwTheme(darkTheme = false) {
        StorageInspectorScreen(
            tab = StorageTab.Files,
            locations = StorageLocations(fileRoots = previewRoots, keyValueStores = listOf(KeyValueStoreInfo("settings"))),
            treeRows = previewRows,
            selectedRow = previewRows[2],
            loadedFile = LoadedFile(location = previewRows[2].location, bytes = "Remember the milk.\n".encodeToByteArray(), totalSizeBytes = 42),
            directoryMeasurement = null,
            fileSha256 = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
            selectedStore = "settings",
            storeContent = KeyValueStoreContent(entries = previewEntries, error = null),
            status = StorageStatus(message = "Reloaded from the app.", isError = false),
            pendingUpload = null,
            pendingZipDownload = null,
            actions = NoActions,
            onSelectTab = {},
        )
    }
}

@Preview
@Composable
private fun FilesPaneEmptyPreview() {
    JwTheme(darkTheme = false) {
        FilesPane(
            treeRows = emptyList(),
            fileRoots = emptyList(),
            selectedRow = null,
            loadedFile = null,
            directoryMeasurement = null,
            fileSha256 = null,
            actions = NoActions,
        )
    }
}

@Preview
@Composable
private fun KeyValuePanePreview() {
    JwTheme(darkTheme = true) {
        KeyValuePane(
            stores = listOf(KeyValueStoreInfo("settings"), KeyValueStoreInfo("session")),
            selectedStore = "settings",
            content = KeyValueStoreContent(entries = previewEntries, error = null),
            actions = NoActions,
        )
    }
}

@Preview
@Composable
private fun KeyValueTablePreview() {
    JwTheme(darkTheme = false) {
        KeyValueTable(entries = previewEntries, selectedKey = "userName", onSelect = {})
    }
}

@Preview
@Composable
private fun ConfirmDialogPreview() {
    JwTheme(darkTheme = false) {
        ConfirmDialog(
            title = "Delete onboarded?",
            message = "The entry is removed from settings. This cannot be undone.",
            confirmLabel = "Delete",
            confirmTone = JwTone.Error,
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun StorageInspectorScreenLargeZipPreview() {
    JwTheme(darkTheme = true) {
        StorageInspectorScreen(
            tab = StorageTab.Files,
            locations = StorageLocations(fileRoots = previewRoots, keyValueStores = emptyList()),
            treeRows = previewRows,
            selectedRow = previewRows[0],
            loadedFile = null,
            directoryMeasurement = null,
            fileSha256 = null,
            selectedStore = null,
            storeContent = null,
            status = null,
            pendingUpload = null,
            pendingZipDownload = PendingZipDownload(
                location = previewRows[0].location,
                target = File("Files.zip"),
                measurement = DirectoryMeasurement(totalSizeBytes = 420L * 1024 * 1024, fileCount = 18_204, directoryCount = 311, truncated = false, error = null),
            ),
            actions = NoActions,
            onSelectTab = {},
        )
    }
}
