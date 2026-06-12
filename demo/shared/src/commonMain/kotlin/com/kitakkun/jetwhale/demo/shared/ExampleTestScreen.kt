package com.kitakkun.jetwhale.demo.shared

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun ExampleTestScreen() {
    val plugin = DIModule.exampleAgentPlugin
    val eventLogs by plugin.eventLogsFlow.collectAsState()
    var counter by remember { mutableIntStateOf(0) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
    ) {
        item {
            Button(
                onClick = { plugin.reportButtonClicked(++counter) },
            ) {
                Text("Send ButtonClicked(${counter + 1})")
            }
        }
        items(eventLogs) {
            Text(it)
        }
    }
}
