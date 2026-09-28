package com.kitakkun.jetwhale.plugins.actions.host

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.kitakkun.jetwhale.host.ui.JwListItem
import com.kitakkun.jetwhale.host.ui.JwSearchField
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwTag
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.JwTone
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionDescriptor

/**
 * The searchable list of actions — the palette. Enter in the search field picks the first match,
 * so ⌘K, a few letters and Enter reach any action from the keyboard.
 */
@Composable
internal fun ActionListPane(
    actions: List<ActionDescriptor>,
    query: String,
    searchFocus: FocusRequester,
    pinnedIds: Set<String>,
    selectedId: String?,
    onQueryChange: (String) -> Unit,
    onSelect: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        JwSearchField(
            value = query,
            clearLabel = "Clear search",
            onValueChange = onQueryChange,
            placeholder = "Search actions (⌘K)",
            modifier = Modifier
                .fillMaxWidth()
                .padding(JwSpacing.medium)
                .focusRequester(searchFocus)
                .onPreviewKeyEvent { event ->
                    val picksFirst = event.type == KeyEventType.KeyDown && event.key == Key.Enter && actions.isNotEmpty()
                    if (picksFirst) onSelect(actions.first().id)
                    picksFirst
                },
        )
        LazyColumn(Modifier.fillMaxSize()) {
            items(actions, key = ActionDescriptor::id) { action ->
                JwListItem(selected = action.id == selectedId, onClick = { onSelect(action.id) }) {
                    Column(Modifier.weight(1f)) {
                        JwText(text = action.title, maxLines = 1)
                        action.group?.let { JwText(text = it, style = JwTheme.textStyles.labelSmall, color = JwTheme.colors.textSecondary, maxLines = 1) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(JwSpacing.small), verticalAlignment = Alignment.CenterVertically) {
                        if (action.destructive) JwTag(text = "Destructive", tone = JwTone.Error)
                        if (action.scoped) JwTag(text = "Screen", tone = JwTone.Info)
                        JwTag(text = if (action.id in pinnedIds) "Pinned" else "Pin", onClick = { onTogglePin(action.id) })
                    }
                }
            }
        }
    }
}
