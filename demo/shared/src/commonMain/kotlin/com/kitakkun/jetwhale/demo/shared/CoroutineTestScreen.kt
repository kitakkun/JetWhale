package com.kitakkun.jetwhale.demo.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * The misbehaving coroutines the Coroutine Inspector is meant to find, one card each: what real-app
 * bug it reproduces, where it shows up in the inspector, and what it has running now.
 */
@Composable
internal fun CoroutineTestScreen() {
    val demo = DIModule.coroutineDemo
    val running by demo.running.collectAsState()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Scope “${CoroutineDemo.SCOPE_NAME}” is registered with the inspector: everything below runs in it. Start a scenario, then open the Coroutine Inspector where the card says.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = demo::stopAll, enabled = running.values.any(Set<*>::isNotEmpty)) { Text("Stop everything") }
            }
        }
        items(CoroutineScenario.entries) { scenario ->
            ScenarioCard(
                scenario = scenario,
                runningCount = running[scenario].orEmpty().size,
                onStart = { demo.start(scenario) },
                onStop = { demo.stop(scenario) },
            )
        }
    }
}

@Composable
private fun ScenarioCard(scenario: CoroutineScenario, runningCount: Int, onStart: () -> Unit, onStop: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(scenario.title, style = MaterialTheme.typography.titleMedium)
            Text(scenario.simulates, style = MaterialTheme.typography.bodyMedium)
            Text("Look in: ${scenario.lookIn}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (runningCount == 0) "Not running" else "$runningCount running",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (runningCount == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                Button(onClick = onStart) { Text("Start") }
                OutlinedButton(onClick = onStop, enabled = runningCount > 0) { Text("Stop") }
            }
        }
    }
}

@Preview
@Composable
private fun CoroutineTestScreenPreview() {
    MaterialTheme {
        CoroutineTestScreen()
    }
}

@Preview
@Composable
private fun ScenarioCardPreview() {
    MaterialTheme {
        ScenarioCard(scenario = CoroutineScenario.NeverReturningRequest, runningCount = 1, onStart = {}, onStop = {})
    }
}
