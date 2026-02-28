package com.kitakkun.jetwhale.plugins.example.host

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import com.kitakkun.jetwhale.host.sdk.JetWhaleDebugOperationContext
import com.kitakkun.jetwhale.plugins.example.protocol.ExampleMethod
import com.kitakkun.jetwhale.plugins.example.protocol.ExampleMethodResult
import kotlinx.coroutines.launch

@Composable
fun ExamplePluginContent(
    eventLogs: SnapshotStateList<String>,
    context: JetWhaleDebugOperationContext<ExampleMethod, ExampleMethodResult>,
) {
    ExamplePluginView(
        eventLogs = eventLogs,
        onClickSendPing = {
            context.coroutineScope.launch {
                val method = ExampleMethod.Ping
                eventLogs.add("Method: $method")
                val result: ExampleMethodResult.Pong? = context.dispatch(method)
                eventLogs.add("Method Result: $result")
            }
        },
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
            }
            items(eventLogs) { log ->
                Text(log)
            }
        }
    }
}
