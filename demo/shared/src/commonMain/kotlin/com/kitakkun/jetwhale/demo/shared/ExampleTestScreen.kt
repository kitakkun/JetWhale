package com.kitakkun.jetwhale.demo.shared

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.plugins.actions.agent.compose.DebugActions
import kotlinx.serialization.Serializable

@Composable
internal fun ExampleTestScreen() {
    val plugin = DIModule.exampleAgentPlugin
    val eventLogs by plugin.eventLogsFlow.collectAsState()
    var counter by remember { mutableIntStateOf(0) }

    // Offered while this tab is shown; the host lists it as a screen action and drops it on leaving.
    DIModule.debugActionsAgentPlugin.DebugActions {
        action<SetCounter>("Set counter") {
            description = "Sets the Example tab's click counter and returns the previous value."
            run { args -> counter.also { counter = args.value } }
        }
    }

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

@Preview
@Composable
private fun ExampleTestScreenPreview() {
    MaterialTheme {
        ExampleTestScreen()
    }
}

@Serializable
private data class SetCounter(val value: Int)
