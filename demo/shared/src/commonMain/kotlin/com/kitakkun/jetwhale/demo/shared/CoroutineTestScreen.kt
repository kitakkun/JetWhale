package com.kitakkun.jetwhale.demo.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
internal fun CoroutineTestScreen() {
    val demo = DIModule.coroutineDemo
    val tick by demo.ticker.collectAsState(initial = 0)
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Tracked flow \"ticker\": $tick")
        Button(onClick = demo::startStuckCoroutine) { Text("Start a coroutine that waits forever") }
        Button(onClick = demo::leakPoller) { Text("Leak a poller") }
        Button(onClick = demo::blockMainThread) { Text("Block the main thread for 300 ms") }
        OutlinedButton(onClick = demo::cancelAll) { Text("Cancel all demo coroutines") }
    }
}

@Preview
@Composable
private fun CoroutineTestScreenPreview() {
    MaterialTheme {
        CoroutineTestScreen()
    }
}
