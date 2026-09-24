package com.kitakkun.jetwhale.plugins.mainthread.host

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.ui.JwCodeBlock
import com.kitakkun.jetwhale.host.ui.JwColumnOverflow
import com.kitakkun.jetwhale.host.ui.JwColumnWidth
import com.kitakkun.jetwhale.host.ui.JwEmptyState
import com.kitakkun.jetwhale.host.ui.JwKeyValueRow
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwSplitPane
import com.kitakkun.jetwhale.host.ui.JwTable
import com.kitakkun.jetwhale.host.ui.JwTableColumn
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.rememberJwSplitPaneState
import com.kitakkun.jetwhale.plugins.mainthread.protocol.ViolationGroup
import com.kitakkun.jetwhale.plugins.mainthread.protocol.ViolationKind

private const val VIOLATION_LIST_FRACTION = 0.55f

private val KindColumnWidth = 120.dp
private val CountColumnWidth = 64.dp

/** @param unavailableReason Why violations are not collected, or null when they are. */
@Composable
internal fun ViolationsPane(violations: List<ViolationGroup>, unavailableReason: String?, modifier: Modifier = Modifier) {
    var selected by remember { mutableStateOf<Pair<ViolationKind, String>?>(null) }
    Box(modifier.fillMaxSize()) {
        if (violations.isEmpty()) {
            JwEmptyState(
                title = if (unavailableReason == null) "No StrictMode violations" else "StrictMode is not collected",
                description = unavailableReason ?: "Disk and network access on the main thread shows up here, grouped by the code that did it.",
            )
            return@Box
        }
        val selectedGroup = violations.firstOrNull { (it.kind to it.callSite) == selected } ?: violations.first()
        JwSplitPane(
            state = rememberJwSplitPaneState(VIOLATION_LIST_FRACTION),
            first = {
                JwTable(
                    items = violations,
                    columns = listOf(
                        JwTableColumn.text(header = "Kind", width = JwColumnWidth.Fixed(KindColumnWidth)) { kindLabel(it.kind) },
                        JwTableColumn.text(header = "Count", width = JwColumnWidth.Fixed(CountColumnWidth), alignment = Alignment.End) { it.count.toString() },
                        JwTableColumn.text(header = "Call site", width = JwColumnWidth.Weight(1f), overflow = JwColumnOverflow.Ellipsis, text = ViolationGroup::callSite),
                    ),
                    key = { it.kind to it.callSite },
                    isSelected = { it === selectedGroup },
                    onClick = { selected = it.kind to it.callSite },
                )
            },
            second = { ViolationDetail(selectedGroup) },
        )
    }
}

@Composable
private fun ViolationDetail(group: ViolationGroup) {
    Column(Modifier.fillMaxSize().padding(JwSpacing.large).verticalScroll(rememberScrollState())) {
        JwText(text = "${kindLabel(group.kind)} on the main thread", style = JwTheme.textStyles.title)
        JwKeyValueRow(key = "Call site", value = group.callSite, monospace = true, wrap = false)
        JwKeyValueRow(key = "Occurrences", value = group.count.toString())
        JwKeyValueRow(key = "Last", value = formatTimeOfDay(group.lastEpochMillis))
        JwKeyValueRow(key = "Message", value = group.message)
        JwText(text = "Latest stack, innermost first", style = JwTheme.textStyles.labelSmall, color = JwTheme.colors.textSecondary)
        JwCodeBlock(text = group.stack.joinToString("\n"), copyLabel = "Copy stack")
    }
}

private fun kindLabel(kind: ViolationKind): String = when (kind) {
    ViolationKind.DiskRead -> "Disk read"
    ViolationKind.DiskWrite -> "Disk write"
    ViolationKind.Network -> "Network"
    ViolationKind.CustomSlowCall -> "Slow call"
    ViolationKind.ResourceMismatch -> "Resource mismatch"
    ViolationKind.UnbufferedIo -> "Unbuffered I/O"
    ViolationKind.Other -> "Other"
}
