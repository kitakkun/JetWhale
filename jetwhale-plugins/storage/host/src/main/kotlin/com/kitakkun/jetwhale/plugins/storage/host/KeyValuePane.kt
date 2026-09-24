package com.kitakkun.jetwhale.plugins.storage.host

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.ui.JwBanner
import com.kitakkun.jetwhale.host.ui.JwColumnOverflow
import com.kitakkun.jetwhale.host.ui.JwColumnWidth
import com.kitakkun.jetwhale.host.ui.JwEmptyState
import com.kitakkun.jetwhale.host.ui.JwIcon
import com.kitakkun.jetwhale.host.ui.JwIconButton
import com.kitakkun.jetwhale.host.ui.JwIconButtonDefaults
import com.kitakkun.jetwhale.host.ui.JwIcons
import com.kitakkun.jetwhale.host.ui.JwListItem
import com.kitakkun.jetwhale.host.ui.JwSplitPane
import com.kitakkun.jetwhale.host.ui.JwTable
import com.kitakkun.jetwhale.host.ui.JwTableColumn
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
                description = "The app has no store this plugin can read. On the JVM there is no platform store; pass your own KeyValueStore to JetWhaleStorageAgentPlugin.",
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
                Column(Modifier.fillMaxSize()) {
                    content?.error?.let { JwBanner(text = it, tone = JwTone.Error) }
                    if (selectedStore != null && content != null) {
                        KeyValueTable(entries = content.entries, onRemove = { actions.removeKey(selectedStore, it.key) })
                    }
                }
            },
        )
    }
}

/**
 * The entries of a key-value store, one row each. [onRemove] adds a remove button to every row;
 * pass null for entries that cannot be changed from here.
 */
@Composable
internal fun KeyValueTable(
    entries: List<KeyValueEntry>,
    onRemove: ((KeyValueEntry) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val columns = buildList {
        add(JwTableColumn.text(header = "Key", width = JwColumnWidth.Weight(1f), overflow = JwColumnOverflow.Wrap, text = KeyValueEntry::key))
        add(JwTableColumn.text(header = "Type", width = JwColumnWidth.Fixed(TypeColumnWidth), text = KeyValueEntry::type))
        add(JwTableColumn.text(header = "Value", width = JwColumnWidth.Weight(2f), overflow = JwColumnOverflow.Wrap, text = KeyValueEntry::value))
        if (onRemove != null) {
            add(
                JwTableColumn<KeyValueEntry>(header = "", width = JwColumnWidth.Fixed(JwIconButtonDefaults.size), alignment = Alignment.CenterHorizontally) { entry ->
                    JwIconButton(tooltip = "Remove ${entry.key}", onClick = { onRemove(entry) }, size = JwIconButtonDefaults.inlineSize) {
                        JwIcon(imageVector = JwIcons.Close, contentDescription = null)
                    }
                },
            )
        }
    }
    JwTable(
        items = entries,
        columns = columns,
        key = KeyValueEntry::key,
        modifier = modifier.fillMaxSize(),
        emptyContent = { JwEmptyState(title = "Empty", description = "This store holds no entries.") },
    )
}
