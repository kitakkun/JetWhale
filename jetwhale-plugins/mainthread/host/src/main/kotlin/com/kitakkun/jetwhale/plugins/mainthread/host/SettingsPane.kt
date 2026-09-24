package com.kitakkun.jetwhale.plugins.mainthread.host

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwFormField
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwTextField
import com.kitakkun.jetwhale.plugins.mainthread.protocol.MonitorSettings

private val FormWidth = 420.dp

@Composable
internal fun SettingsPane(settings: MonitorSettings, onApply: (MonitorSettings) -> Unit, modifier: Modifier = Modifier) {
    var longTask by remember(settings) { mutableStateOf(settings.longTaskThresholdMillis.toString()) }
    var interval by remember(settings) { mutableStateOf(settings.sampleIntervalMillis.toString()) }
    var unresponsive by remember(settings) { mutableStateOf(settings.unresponsiveThresholdMillis.toString()) }
    val edited = validSettingsOf(longTask = longTask, interval = interval, unresponsive = unresponsive)
    Column(
        modifier = modifier.fillMaxSize().padding(JwSpacing.large).widthIn(max = FormWidth),
        verticalArrangement = Arrangement.spacedBy(JwSpacing.medium),
    ) {
        JwFormField(label = "Long task (ms)", supportingText = "Tasks at least this long are recorded, and sampled while they run.") {
            JwTextField(value = longTask, onValueChange = { longTask = it }, isError = longTask.toLongOrNull()?.takeIf { it > 0 } == null)
        }
        JwFormField(label = "Sample interval (ms)", supportingText = "How often a long task's stack is read. Shorter is finer and costs more.") {
            JwTextField(value = interval, onValueChange = { interval = it }, isError = interval.toLongOrNull()?.takeIf { it > 0 } == null)
        }
        JwFormField(label = "Unresponsive (ms)", supportingText = "Tasks this long are marked the way an ANR would report them.") {
            JwTextField(value = unresponsive, onValueChange = { unresponsive = it }, isError = unresponsive.toLongOrNull()?.takeIf { it > 0 } == null)
        }
        JwButton(
            text = "Apply",
            style = JwButtonStyle.Primary,
            enabled = edited != null && edited != settings,
            onClick = { edited?.let(onApply) },
        )
    }
}

/** The form's values as settings, or null while any of them is not a positive whole number. */
internal fun validSettingsOf(longTask: String, interval: String, unresponsive: String): MonitorSettings? {
    val longTaskMillis = longTask.toLongOrNull()?.takeIf { it > 0 } ?: return null
    val intervalMillis = interval.toLongOrNull()?.takeIf { it > 0 } ?: return null
    val unresponsiveMillis = unresponsive.toLongOrNull()?.takeIf { it > 0 } ?: return null
    return MonitorSettings(longTaskThresholdMillis = longTaskMillis, sampleIntervalMillis = intervalMillis, unresponsiveThresholdMillis = unresponsiveMillis)
}
