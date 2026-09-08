package com.kitakkun.jetwhale.plugins.example.host

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import com.kitakkun.jetwhale.host.sdk.rememberPersistent
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwEmptyState
import com.kitakkun.jetwhale.host.ui.JwFormField
import com.kitakkun.jetwhale.host.ui.JwHorizontalDivider
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTextField
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.JwTone
import com.kitakkun.jetwhale.host.ui.JwToolbar

@Composable
fun ExamplePluginContent(
    eventLogs: SnapshotStateList<String>,
    onClickSendPing: () -> Unit,
) {
    ExamplePluginView(
        eventLogs = eventLogs,
        onClickSendPing = onClickSendPing,
        onClickTriggerUIError = { error("Example Error") },
    )
}

/**
 * The reference plugin UI: built from `jetwhale-host-ui` alone, so it doubles as the smallest
 * example of a plugin that looks like part of the host.
 */
@Composable
fun ExamplePluginView(
    eventLogs: List<String>,
    onClickSendPing: () -> Unit,
    onClickTriggerUIError: () -> Unit,
) {
    // Demonstrates rememberPersistent: this text is saved to the plugin's own pluginId-scoped
    // storage and survives plugin reloads and host restarts.
    var persistedInput by rememberPersistent("draft-input", default = "")

    Column(Modifier.fillMaxSize()) {
        JwToolbar(
            title = "Example JetWhale Plugin",
            actions = {
                JwButton(text = "Send ping to debuggee", onClick = onClickSendPing, style = JwButtonStyle.Primary)
                JwButton(text = "Throw UI error", onClick = onClickTriggerUIError, tone = JwTone.Error)
            },
        )
        Row(modifier = Modifier.fillMaxWidth().padding(JwSpacing.large)) {
            JwFormField(label = "Persisted input", supportingText = "Saved with rememberPersistent; survives reloads and restarts.") {
                JwTextField(
                    value = persistedInput,
                    onValueChange = { persistedInput = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        JwHorizontalDivider()
        if (eventLogs.isEmpty()) {
            JwEmptyState(
                title = "No messages yet",
                description = "Send a ping, or click the button in the debuggee, and the exchange is logged here.",
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(JwSpacing.large),
                verticalArrangement = Arrangement.spacedBy(JwSpacing.extraSmall),
            ) {
                items(eventLogs) { log ->
                    JwText(text = log, style = JwTheme.textStyles.code)
                }
            }
        }
    }
}
