package com.kitakkun.jetwhale.demo.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds

/**
 * Work a Composable starts, as opposed to the scenarios above, which run in a registered scope:
 * a panel that polls while it is shown and saves a draft from a button. Compose cancels both when
 * the panel leaves the screen, which the inspector's "Compose" root makes visible.
 */
@Composable
internal fun ScreenWorkCard() {
    var panelShown by remember { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Work tied to a screen", style = MaterialTheme.typography.titleMedium)
            Text(
                "A panel polls while it is visible, and its button saves a draft. Hiding the panel — or switching to another tab — takes both off the screen, and Compose cancels them.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Look in: Coroutines → root “Compose” → “panel-poll” and “save-draft”; hide the panel and they are gone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (panelShown) "Panel shown" else "Panel hidden", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.weight(1f))
                Button(onClick = { panelShown = !panelShown }) { Text(if (panelShown) "Hide panel" else "Show panel") }
            }
            if (panelShown) PollingPanel()
        }
    }
}

@Composable
private fun PollingPanel() {
    var polls by remember { mutableIntStateOf(0) }
    var savingDrafts by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        withContext(CoroutineName("panel-poll")) {
            while (true) {
                delay(1.seconds)
                polls++
            }
        }
    }
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Polled $polls times", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        Text(if (savingDrafts == 0) "No draft saving" else "$savingDrafts saving", style = MaterialTheme.typography.labelLarge)
        OutlinedButton(
            onClick = {
                scope.launch(CoroutineName("save-draft")) {
                    savingDrafts++
                    try {
                        delay(SAVE_DURATION)
                    } finally {
                        savingDrafts--
                    }
                }
            },
        ) { Text("Save draft (10 s)") }
    }
}

private val SAVE_DURATION = 10.seconds

@Preview
@Composable
private fun ScreenWorkCardPreview() {
    MaterialTheme {
        ScreenWorkCard()
    }
}
