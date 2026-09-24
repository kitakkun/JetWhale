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
import com.kitakkun.jetwhale.plugins.mainthread.protocol.Hotspot
import com.kitakkun.jetwhale.plugins.mainthread.protocol.MonitorCapabilities
import com.kitakkun.jetwhale.plugins.mainthread.protocol.MonitorSettings

/** The ranking is the point; the stack beside it needs less room. */
private const val HOTSPOT_LIST_FRACTION = 0.55f

private val NumberColumnWidth = 88.dp

@Composable
internal fun HotspotsPane(hotspots: List<Hotspot>, capabilities: MonitorCapabilities, settings: MonitorSettings, modifier: Modifier = Modifier) {
    var selected by remember { mutableStateOf<String?>(null) }
    Box(modifier.fillMaxSize()) {
        if (hotspots.isEmpty()) {
            JwEmptyState(
                title = if (capabilities.stackSampling) "No hotspots yet" else "Stacks are not sampled on ${capabilities.platform}",
                description = if (capabilities.stackSampling) {
                    "A hotspot appears when a main-thread task runs past ${settings.longTaskThresholdMillis} ms: its stack is sampled every ${settings.sampleIntervalMillis} ms while it runs."
                } else {
                    capabilities.note
                },
            )
            return@Box
        }
        val selectedHotspot = hotspots.firstOrNull { it.signature == selected } ?: hotspots.first()
        JwSplitPane(
            state = rememberJwSplitPaneState(HOTSPOT_LIST_FRACTION),
            first = {
                JwTable(
                    items = hotspots,
                    columns = listOf(
                        JwTableColumn.text(header = "Blocked", width = JwColumnWidth.Fixed(NumberColumnWidth), alignment = Alignment.End) { formatMillis(it.blockedMillis) },
                        JwTableColumn.text(header = "Tasks", width = JwColumnWidth.Fixed(NumberColumnWidth), alignment = Alignment.End) { it.taskCount.toString() },
                        JwTableColumn.text(header = "Where", width = JwColumnWidth.Weight(1f), overflow = JwColumnOverflow.Ellipsis, text = ::callSiteLabel),
                    ),
                    key = Hotspot::signature,
                    isSelected = { it.signature == selectedHotspot.signature },
                    onClick = { selected = it.signature },
                )
            },
            second = { HotspotDetail(selectedHotspot) },
        )
    }
}

@Composable
private fun HotspotDetail(hotspot: Hotspot) {
    Column(Modifier.fillMaxSize().padding(JwSpacing.large).verticalScroll(rememberScrollState())) {
        JwText(text = callSiteLabel(hotspot), style = JwTheme.textStyles.title)
        JwKeyValueRow(key = "Blocked", value = "${formatMillis(hotspot.blockedMillis)} over ${hotspot.sampleCount} samples")
        JwKeyValueRow(key = "Long tasks", value = hotspot.taskCount.toString())
        JwKeyValueRow(key = "Signature", value = hotspot.signature, monospace = true)
        JwText(text = "Latest stack, innermost first", style = JwTheme.textStyles.labelSmall, color = JwTheme.colors.textSecondary)
        JwCodeBlock(text = hotspot.frames.joinToString("\n"), copyLabel = "Copy stack")
    }
}

/** The first frame of the signature: the app code that was running. */
private fun callSiteLabel(hotspot: Hotspot): String = hotspot.signature.substringBefore(" ← ")
