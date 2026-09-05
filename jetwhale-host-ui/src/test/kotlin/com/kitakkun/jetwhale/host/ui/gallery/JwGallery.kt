package com.kitakkun.jetwhale.host.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.ui.JwBanner
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwCheckbox
import com.kitakkun.jetwhale.host.ui.JwCodeBlock
import com.kitakkun.jetwhale.host.ui.JwColumnWidth
import com.kitakkun.jetwhale.host.ui.JwCountBadge
import com.kitakkun.jetwhale.host.ui.JwDropdownButton
import com.kitakkun.jetwhale.host.ui.JwEmptyState
import com.kitakkun.jetwhale.host.ui.JwFormField
import com.kitakkun.jetwhale.host.ui.JwIcon
import com.kitakkun.jetwhale.host.ui.JwIconButton
import com.kitakkun.jetwhale.host.ui.JwIcons
import com.kitakkun.jetwhale.host.ui.JwKeyValueRow
import com.kitakkun.jetwhale.host.ui.JwListItem
import com.kitakkun.jetwhale.host.ui.JwPanel
import com.kitakkun.jetwhale.host.ui.JwSearchField
import com.kitakkun.jetwhale.host.ui.JwSectionHeader
import com.kitakkun.jetwhale.host.ui.JwSegmentedButtons
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwStatusDot
import com.kitakkun.jetwhale.host.ui.JwStatusLine
import com.kitakkun.jetwhale.host.ui.JwSwitch
import com.kitakkun.jetwhale.host.ui.JwTab
import com.kitakkun.jetwhale.host.ui.JwTabRow
import com.kitakkun.jetwhale.host.ui.JwTable
import com.kitakkun.jetwhale.host.ui.JwTableColumn
import com.kitakkun.jetwhale.host.ui.JwTag
import com.kitakkun.jetwhale.host.ui.JwTagStyle
import com.kitakkun.jetwhale.host.ui.JwTextField
import com.kitakkun.jetwhale.host.ui.JwTone
import com.kitakkun.jetwhale.host.ui.JwToolbar
import com.kitakkun.jetwhale.host.ui.JwTreeRow

/**
 * Every component in every state worth looking at, laid out on one page. The screenshot tests
 * capture it in both themes; it is also the quickest way to eyeball a change to the library.
 *
 * States are fixed (no interaction), so the picture is deterministic.
 */
@Composable
fun JwGallery() {
    Column(
        modifier = Modifier
            .width(GALLERY_WIDTH.dp)
            .background(MaterialTheme.colorScheme.surface),
    ) {
        JwToolbar(
            title = "Gallery",
            actions = {
                JwIconButton(onClick = {}, tooltip = "Search") { JwIcon(JwIcons.Search, contentDescription = null) }
                JwIconButton(onClick = {}, tooltip = "Selected", selected = true) { JwIcon(JwIcons.Check, contentDescription = null) }
                JwIconButton(onClick = {}, tooltip = "Disabled", enabled = false) { JwIcon(JwIcons.Close, contentDescription = null) }
            },
        )
        JwTabRow {
            JwTab(selected = true, onClick = {}, text = "Traffic", count = 12)
            JwTab(selected = false, onClick = {}, text = "Mocks", count = 0)
            JwTab(selected = false, onClick = {}, text = "Settings")
        }
        JwBanner(text = "A newer version is available", onDismiss = {}, dismissLabel = "Dismiss", actions = {
            JwButton(text = "View", onClick = {}, style = JwButtonStyle.Text)
        })
        JwBanner(text = "Following the AI agent — jetwhale.click", tone = JwTone.Warning, actions = {
            JwButton(text = "Stop following", onClick = {}, style = JwButtonStyle.Text)
        })
        JwStatusLine(text = "3 roots · 120 shown of 120 · 12 ms on device", trailingContent = { JwCountBadge(count = 120) })
        JwStatusLine(text = "Capture failed: no probe installed", tone = JwTone.Error)

        Section("Buttons") {
            Row(horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium), verticalAlignment = Alignment.CenterVertically) {
                JwButton(text = "Primary", onClick = {}, style = JwButtonStyle.Primary)
                JwButton(text = "Secondary", onClick = {})
                JwButton(text = "Text", onClick = {}, style = JwButtonStyle.Text)
                JwButton(text = "Delete", onClick = {}, style = JwButtonStyle.Text, tone = JwTone.Error)
                JwButton(text = "Disabled", onClick = {}, enabled = false)
                JwButton(text = "Disabled", onClick = {}, style = JwButtonStyle.Primary, enabled = false)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium), verticalAlignment = Alignment.CenterVertically) {
                JwSegmentedButtons(options = listOf("Tree", "Raw"), selected = "Tree", onSelect = {}, label = { it })
                JwSwitch(checked = true, onCheckedChange = {}, contentDescription = "On")
                JwSwitch(checked = false, onCheckedChange = {}, contentDescription = "Off")
                JwSwitch(checked = true, onCheckedChange = {}, contentDescription = "Disabled", enabled = false)
                JwCheckbox(checked = true, onCheckedChange = {}, label = "Checked")
                JwCheckbox(checked = false, onCheckedChange = {}, label = "Unchecked")
                JwCheckbox(checked = true, onCheckedChange = {}, label = "Disabled", enabled = false)
            }
        }

        Section("Inputs") {
            Row(horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium)) {
                JwFormField(label = "Name", modifier = Modifier.weight(1f)) {
                    JwTextField(value = "", onValueChange = {}, placeholder = "Placeholder")
                }
                JwFormField(label = "Group id", modifier = Modifier.weight(1f), supportingText = "Required", isError = true) {
                    JwTextField(value = "com.example", onValueChange = {}, isError = true)
                }
                JwFormField(label = "Repository", modifier = Modifier.weight(1f)) {
                    JwDropdownButton(text = "Maven Central", expanded = false, onExpandedChange = {}, trailingIcon = { JwStatusDot(JwTone.Success) }) {}
                }
            }
            JwSearchField(value = "api/users", onValueChange = {}, clearLabel = "Clear", placeholder = "Filter")
            JwFormField(label = "Body") {
                JwTextField(value = "{\n  \"type\": \"Detail\"\n}", onValueChange = {}, minLines = 3)
            }
        }

        Section("Tags and badges") {
            Row(horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium), verticalAlignment = Alignment.CenterVertically) {
                JwTone.entries.forEach { tone -> JwTag(text = tone.name, tone = tone) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium), verticalAlignment = Alignment.CenterVertically) {
                JwTone.entries.forEach { tone -> JwTag(text = tone.name, tone = tone, style = JwTagStyle.Tinted) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium), verticalAlignment = Alignment.CenterVertically) {
                JwTone.entries.forEach { tone -> JwTag(text = tone.name, tone = tone, style = JwTagStyle.Filled) }
                JwTag(text = "MCP", onClick = {}, trailingIcon = { JwIcon(JwIcons.ChevronRight, contentDescription = null) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium), verticalAlignment = Alignment.CenterVertically) {
                JwTone.entries.forEach { tone -> JwStatusDot(tone = tone) }
                JwStatusDot(tone = JwTone.Neutral, filled = false)
                JwCountBadge(count = 3)
                JwCountBadge(count = 42, tone = JwTone.Warning)
                JwCountBadge(count = 1, tone = JwTone.Error)
            }
        }

        Section("Rows") {
            JwPanel(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                JwSectionHeader(title = "Enabled Plugins", count = 3, expanded = true, onToggleExpanded = {})
                JwListItem(text = "Network Inspector", selected = true, onClick = {}, leadingContent = { JwIcon(JwIcons.Check, contentDescription = null) }, trailingContent = { JwTag(text = "MCP") })
                JwListItem(text = "Nav3 Navigator", selected = false, onClick = {}, supportingText = "com.kitakkun.jetwhale.nav3")
                JwListItem(text = "Muted item", selected = false, onClick = {}, muted = true)
                JwListItem(text = "Disabled item", selected = false, onClick = {}, enabled = false)
                JwSectionHeader(title = "Collapsed", count = 1, expanded = false, onToggleExpanded = {})
            }
            JwPanel(title = "Tree", contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                JwTreeRow(text = "Root", depth = 0, expandable = true, expanded = true, selected = false, onClick = {}, onToggleExpanded = {})
                JwTreeRow(text = "Column", depth = 1, expandable = true, expanded = true, selected = true, onClick = {}, onToggleExpanded = {}, trailingContent = { JwTag(text = "clickable", tone = JwTone.Accent) })
                JwTreeRow(text = "Text \"Hello\"", depth = 2, expandable = false, expanded = false, selected = false, onClick = {}, onToggleExpanded = {})
                JwTreeRow(text = "Hidden", depth = 2, expandable = false, expanded = false, selected = false, onClick = {}, onToggleExpanded = {}, muted = true)
            }
            JwPanel(title = "Properties", headerActions = { JwButton(text = "Copy", onClick = {}, style = JwButtonStyle.Text) }) {
                JwKeyValueRow(key = "id", value = "42", monospace = true)
                JwKeyValueRow(key = "Content-Type", value = "application/json; charset=utf-8", monospace = true)
                JwKeyValueRow(key = "url", value = "https://example.com/api/very/long/path/that/does/not/fit/in/the/row/at/all", monospace = true, wrap = false)
            }
            JwCodeBlock(text = "{\n  \"type\": \"ProductDetail\",\n  \"id\": \"42\"\n}", copyLabel = "Copy")
        }

        Section("Table") {
            JwTable(
                items = SAMPLE_ROWS,
                columns = SAMPLE_COLUMNS,
                key = { it.id },
                isSelected = { it.id == 2 },
                onClick = {},
                modifier = Modifier.height(TABLE_HEIGHT.dp),
            )
        }

        Section("Empty state") {
            JwEmptyState(
                title = "No plugin selected",
                description = "Pick a session and a plugin in the sidebar.",
                action = { JwButton(text = "Open settings", onClick = {}, style = JwButtonStyle.Primary) },
                modifier = Modifier.height(EMPTY_STATE_HEIGHT.dp),
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(JwSpacing.large),
        verticalArrangement = Arrangement.spacedBy(JwSpacing.medium),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        content()
    }
}

private data class SampleRow(val id: Int, val status: Int, val method: String, val url: String, val ms: Long)

private val SAMPLE_ROWS = listOf(
    SampleRow(1, 200, "GET", "https://example.com/api/users?page=1", 42),
    SampleRow(2, 404, "GET", "https://example.com/api/missing", 12),
    SampleRow(3, 201, "POST", "https://example.com/api/orders", 310),
    SampleRow(4, 302, "GET", "https://example.com/redirect", 8),
)

private val SAMPLE_COLUMNS = listOf(
    JwTableColumn<SampleRow>(header = "Status", width = JwColumnWidth.Fixed(48.dp)) {
        val tone = when (it.status) {
            in 200..299 -> JwTone.Success
            in 300..399 -> JwTone.Info
            else -> JwTone.Error
        }
        JwTag(text = it.status.toString(), tone = tone, style = JwTagStyle.Tinted)
    },
    JwTableColumn(header = "Method", width = JwColumnWidth.Fixed(56.dp)) { Text(it.method, style = MaterialTheme.typography.labelMedium) },
    JwTableColumn(header = "URL", width = JwColumnWidth.Weight(1f)) { Text(it.url, style = MaterialTheme.typography.bodySmall, maxLines = 1) },
    JwTableColumn(header = "Time", width = JwColumnWidth.Fixed(56.dp), alignment = Alignment.End) { Text("${it.ms}ms", style = MaterialTheme.typography.labelSmall) },
)

/** Wide enough for the widest row (the button row) without wrapping. */
const val GALLERY_WIDTH = 900

/** Tall enough to show the header and all sample rows. */
private const val TABLE_HEIGHT = 170

private const val EMPTY_STATE_HEIGHT = 200
