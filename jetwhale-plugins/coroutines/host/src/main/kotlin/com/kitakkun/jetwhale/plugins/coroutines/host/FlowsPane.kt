package com.kitakkun.jetwhale.plugins.coroutines.host

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.ui.JwColumnOverflow
import com.kitakkun.jetwhale.host.ui.JwColumnWidth
import com.kitakkun.jetwhale.host.ui.JwEmptyState
import com.kitakkun.jetwhale.host.ui.JwSplitPane
import com.kitakkun.jetwhale.host.ui.JwTable
import com.kitakkun.jetwhale.host.ui.JwTableColumn
import com.kitakkun.jetwhale.host.ui.rememberJwSplitPaneState
import com.kitakkun.jetwhale.plugins.coroutines.protocol.FlowValue
import com.kitakkun.jetwhale.plugins.coroutines.protocol.TrackedFlowInfo
import com.kitakkun.jetwhale.plugins.coroutines.protocol.TrackedFlowReport
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ValueTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())

/** The flow table gets most of the width; the values of the selected flow read fine in the rest. */
private const val FLOW_TABLE_FRACTION = 0.6f

private val CountColumnWidth = 84.dp

@Composable
internal fun FlowsPane(report: TrackedFlowReport?, modifier: Modifier = Modifier) {
    var selectedName by remember { mutableStateOf<String?>(null) }
    Box(modifier.fillMaxSize()) {
        when {
            report == null -> JwEmptyState(title = "Reading flows…")

            report.flows.isEmpty() -> JwEmptyState(
                title = "No flows tracked",
                description = "Track a flow where it is collected to see its collectors and values: inspector.track(repository.prices, name = \"prices\").",
            )

            else -> JwSplitPane(
                state = rememberJwSplitPaneState(FLOW_TABLE_FRACTION),
                first = {
                    JwTable(
                        items = report.flows,
                        columns = listOf(
                            JwTableColumn.text(header = "Flow", width = JwColumnWidth.Weight(1f), text = TrackedFlowInfo::name),
                            count("Collectors") { it.activeCollectors.toString() },
                            count("Emitted") { it.emissions.toString() },
                            count("Per sec") { String.format(Locale.ROOT, "%.1f", it.emissionsPerSecond) },
                            count("Done") { it.completions.toString() },
                            count("Cancelled") { it.cancellations.toString() },
                            count("Failed") { it.failures.toString() },
                        ),
                        key = TrackedFlowInfo::name,
                        isSelected = { it.name == selectedName },
                        onClick = { selectedName = it.name },
                    )
                },
                second = {
                    val selected = report.flows.firstOrNull { it.name == selectedName }
                    if (selected == null) {
                        JwEmptyState(title = "No flow selected", description = "Pick a flow to see the values it emitted last.")
                    } else {
                        JwTable(
                            items = selected.recentValues,
                            columns = listOf(
                                JwTableColumn.text(header = "At", width = JwColumnWidth.Fixed(110.dp)) { ValueTimeFormatter.format(Instant.ofEpochMilli(it.atEpochMillis)) },
                                JwTableColumn.text(header = "Value", width = JwColumnWidth.Weight(1f), overflow = JwColumnOverflow.Wrap, text = FlowValue::text),
                            ),
                            emptyContent = { JwEmptyState(title = "Nothing emitted yet") },
                        )
                    }
                },
            )
        }
    }
}

private fun <T> count(header: String, text: (T) -> String): JwTableColumn<T> = JwTableColumn.text(header = header, width = JwColumnWidth.Fixed(CountColumnWidth), alignment = Alignment.End, text = text)
