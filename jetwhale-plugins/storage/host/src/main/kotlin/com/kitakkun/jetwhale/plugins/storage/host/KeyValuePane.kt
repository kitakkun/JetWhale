package com.kitakkun.jetwhale.plugins.storage.host

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.ui.JwBanner
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwColumnOverflow
import com.kitakkun.jetwhale.host.ui.JwColumnWidth
import com.kitakkun.jetwhale.host.ui.JwEmptyState
import com.kitakkun.jetwhale.host.ui.JwListItem
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwSplitPane
import com.kitakkun.jetwhale.host.ui.JwTable
import com.kitakkun.jetwhale.host.ui.JwTableColumn
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.JwTone
import com.kitakkun.jetwhale.host.ui.rememberJwSplitPaneState
import com.kitakkun.jetwhale.plugins.storage.protocol.KeyValueEntry
import com.kitakkun.jetwhale.plugins.storage.protocol.KeyValueStoreContent
import com.kitakkun.jetwhale.plugins.storage.protocol.KeyValueStoreInfo

/** The store list is a column of names; the entries beside it need the room. */
private const val STORE_LIST_FRACTION = 0.25f

/** Fits the longest type name a platform store reports, "Set<String>". */
private val TypeColumnWidth = 96.dp

@Composable
internal fun KeyValuePane(
    stores: List<KeyValueStoreInfo>,
    selectedStore: String?,
    content: KeyValueStoreContent?,
    actions: StorageInspectorActions,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        if (stores.isEmpty()) {
            JwEmptyState(
                title = "No key-value stores",
                description = "The app has no store this plugin can read. " +
                    "On the JVM there is no platform store; pass your own KeyValueStore to JetWhaleStorageAgentPlugin.",
            )
            return@Box
        }
        JwSplitPane(
            state = rememberJwSplitPaneState(STORE_LIST_FRACTION),
            first = {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(stores, key = KeyValueStoreInfo::name) { store ->
                        JwListItem(
                            text = store.name,
                            selected = store.name == selectedStore,
                            onClick = { actions.selectStore(store.name) },
                        )
                    }
                }
            },
            second = {
                if (selectedStore != null) StoreEntries(storeName = selectedStore, content = content, actions = actions)
            },
        )
    }
}

/**
 * One store's entries. Deleting works as it does for a file: select the entry, then confirm, so a
 * stray click cannot remove anything.
 */
@Composable
private fun StoreEntries(storeName: String, content: KeyValueStoreContent?, actions: StorageInspectorActions) {
    var selectedKey by remember(storeName) { mutableStateOf<String?>(null) }
    var confirmingDelete by remember(storeName) { mutableStateOf(false) }
    val selectedEntry = content?.entries?.firstOrNull { it.key == selectedKey }
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = JwSpacing.large, vertical = JwSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            JwText(text = storeName, style = JwTheme.textStyles.title, modifier = Modifier.weight(1f))
            JwButton(text = "Delete…", onClick = { confirmingDelete = true }, tone = JwTone.Error, enabled = selectedEntry != null)
        }
        content?.error?.let { JwBanner(text = it, tone = JwTone.Error) }
        if (content != null) KeyValueTable(entries = content.entries, selectedKey = selectedKey, onSelect = { selectedKey = it.key })
    }
    if (confirmingDelete && selectedEntry != null) {
        ConfirmDeleteDialog(
            title = "Delete ${selectedEntry.key}?",
            message = "The entry is removed from $storeName. This cannot be undone.",
            onConfirm = {
                confirmingDelete = false
                selectedKey = null
                actions.removeKey(storeName, selectedEntry.key)
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}

/**
 * The entries of a key-value store, one row each. [onSelect] makes the rows selectable; pass null
 * for entries that cannot be changed from here.
 */
@Composable
internal fun KeyValueTable(
    entries: List<KeyValueEntry>,
    selectedKey: String?,
    onSelect: ((KeyValueEntry) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    JwTable(
        items = entries,
        columns = listOf(
            JwTableColumn.text(
                header = "Key",
                width = JwColumnWidth.Weight(1f),
                overflow = JwColumnOverflow.Wrap,
                text = KeyValueEntry::key,
            ),
            JwTableColumn.text(header = "Type", width = JwColumnWidth.Fixed(TypeColumnWidth), text = KeyValueEntry::type),
            JwTableColumn.text(
                header = "Value",
                width = JwColumnWidth.Weight(2f),
                overflow = JwColumnOverflow.Wrap,
                text = KeyValueEntry::value,
            ),
        ),
        key = KeyValueEntry::key,
        isSelected = { it.key == selectedKey },
        onClick = onSelect,
        modifier = modifier.fillMaxSize(),
        emptyContent = { JwEmptyState(title = "Empty", description = "This store holds no entries.") },
    )
}
