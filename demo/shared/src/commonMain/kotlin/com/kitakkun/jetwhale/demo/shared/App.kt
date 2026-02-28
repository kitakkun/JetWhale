package com.kitakkun.jetwhale.demo.shared

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.plugins.example.protocol.ExampleEvent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val plugin = DIModule.exampleAgentPlugin
    val eventLogs by plugin.eventLogsFlow.collectAsState()
    var counter by remember { mutableIntStateOf(0) }

    MaterialTheme {
        Surface {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text("JetWhale Demo App (Agent)")
                        },
                    )
                },
            ) {
                LazyColumn(
                    modifier = Modifier
                        .padding(it)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    stickyHeader {
                        Button(
                            onClick = { plugin.enqueueEvent(ExampleEvent.ButtonClicked(++counter)) },
                        ) {
                            Text(
                                text = "Send ExampleEvent.ButtonClicked(${counter + 1})",
                            )
                        }
                    }
                    items(eventLogs) {
                        Text(text = it)
                    }
                }
            }
        }
    }
}
