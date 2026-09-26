package com.kitakkun.jetwhale.plugins.storage.host

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.kitakkun.jetwhale.host.sdk.rememberPersistent
import com.kitakkun.jetwhale.host.ui.JwBanner
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwTab
import com.kitakkun.jetwhale.host.ui.JwTabRow
import com.kitakkun.jetwhale.host.ui.JwTone
import com.kitakkun.jetwhale.host.ui.JwToolbar
import com.kitakkun.jetwhale.plugins.storage.protocol.DirectoryMeasurement
import com.kitakkun.jetwhale.plugins.storage.protocol.KeyValueStoreContent
import com.kitakkun.jetwhale.plugins.storage.protocol.StorageLocations

internal enum class StorageTab(val label: String) {
    Files("Files"),
    KeyValue("Key-Value"),
}

/** Binds the host-owned state — the browser and the persisted tab — to [StorageInspectorScreen]. */
@Composable
internal fun StorageInspectorScreenRoot(browser: StorageBrowser, modifier: Modifier = Modifier) {
    var tab by rememberPersistent("tab", default = StorageTab.Files)
    StorageInspectorScreen(
        tab = tab,
        locations = browser.locations,
        treeRows = browser.treeRows,
        selectedRow = browser.selectedRow,
        loadedFile = browser.loadedFile,
        directoryMeasurement = browser.directoryMeasurement,
        fileSha256 = browser.fileSha256,
        selectedStore = browser.selectedStore,
        storeContent = browser.storeContent,
        status = browser.status,
        pendingUpload = browser.pendingUpload,
        actions = browser,
        onSelectTab = { tab = it },
        modifier = modifier,
    )
}

@Composable
internal fun StorageInspectorScreen(
    tab: StorageTab,
    locations: StorageLocations?,
    treeRows: List<FileTreeRow>,
    selectedRow: FileTreeRow?,
    loadedFile: LoadedFile?,
    directoryMeasurement: DirectoryMeasurement?,
    fileSha256: String?,
    selectedStore: String?,
    storeContent: KeyValueStoreContent?,
    status: StorageStatus?,
    pendingUpload: PendingUpload?,
    actions: StorageInspectorActions,
    onSelectTab: (StorageTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        JwToolbar(
            title = "Storage",
            actions = { JwButton(text = "Reload from app", onClick = actions::refresh, style = JwButtonStyle.Text) },
        )
        JwTabRow {
            StorageTab.entries.forEach { entry ->
                JwTab(
                    selected = entry == tab,
                    text = entry.label,
                    onClick = { onSelectTab(entry) },
                    count = when (entry) {
                        StorageTab.Files -> locations?.fileRoots?.size
                        StorageTab.KeyValue -> locations?.keyValueStores?.size
                    },
                )
            }
        }
        status?.let { JwBanner(text = it.message, tone = if (it.isError) JwTone.Error else JwTone.Neutral) }
        when (tab) {
            StorageTab.Files -> FilesPane(
                treeRows = treeRows,
                fileRoots = locations?.fileRoots.orEmpty(),
                selectedRow = selectedRow,
                loadedFile = loadedFile,
                directoryMeasurement = directoryMeasurement,
                fileSha256 = fileSha256,
                actions = actions,
            )

            StorageTab.KeyValue -> KeyValuePane(
                stores = locations?.keyValueStores.orEmpty(),
                selectedStore = selectedStore,
                content = storeContent,
                actions = actions,
            )
        }
    }
    pendingUpload?.let { upload ->
        ConfirmDialog(
            title = "Replace ${upload.target.name}?",
            message = "The file in the app is replaced with ${upload.source.name}. This cannot be undone.",
            confirmLabel = "Replace",
            onConfirm = actions::confirmUpload,
            onDismiss = actions::cancelUpload,
        )
    }
}
