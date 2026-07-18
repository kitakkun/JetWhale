package com.kitakkun.jetwhale.plugins.example.host

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import com.kitakkun.jetwhale.host.sdk.rememberPersistent

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamplePluginView(
    eventLogs: List<String>,
    onClickSendPing: () -> Unit,
    onClickTriggerUIError: () -> Unit,
) {
    // Demonstrates rememberPersistent: this text is saved to the plugin's own pluginId-scoped
    // storage and survives plugin reloads and host restarts.
    var persistedInput by rememberPersistent("draft-input", default = "")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Example JetWhale Plugin")
                },
            )
        },
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(it),
        ) {
            stickyHeader {
                Button(onClickSendPing) {
                    Text("Send ping to debuggee")
                }
                Button(onClickTriggerUIError) {
                    Text("Throw UI error")
                }
                OutlinedTextField(
                    value = persistedInput,
                    onValueChange = { persistedInput = it },
                    label = { Text("Persisted input") },
                )
            }
            items(eventLogs) { log ->
                Text(log)
            }
        }
    }
}
