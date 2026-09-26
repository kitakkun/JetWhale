package com.kitakkun.jetwhale.demo.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.time.TimeSource

/** Something the demo does on the main thread on purpose, for the Main Thread Monitor to catch. */
class MainThreadBlocker(val label: String, val block: () -> String)

/** Blockers only a platform has: a SharedPreferences commit on Android, a sleep on the JVM. */
expect val platformMainThreadBlockers: List<MainThreadBlocker>

private val commonBlockers = listOf(
    MainThreadBlocker("Busy loop for 300 ms") {
        busyLoop(millis = 300)
        "Spun the main thread for 300 ms."
    },
    MainThreadBlocker("Busy loop for 5.5 s (unresponsive)") {
        busyLoop(millis = 5_500)
        "Spun the main thread for 5.5 s."
    },
)

@Composable
fun MainThreadTestScreen() {
    var result by remember { mutableStateOf("Each button blocks the main thread on purpose.") }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        (commonBlockers + platformMainThreadBlockers).forEach { blocker ->
            Button(onClick = { result = blocker.block() }) { Text(blocker.label) }
        }
        Text(result)
    }
}

@Preview
@Composable
private fun MainThreadTestScreenPreview() {
    MainThreadTestScreen()
}

/** Work the sampler can see in a named frame, rather than a wait. */
private fun busyLoop(millis: Long) {
    val end = TimeSource.Monotonic.markNow()
    var sink = 0L
    while (end.elapsedNow().inWholeMilliseconds < millis) sink += sink xor 31
    check(sink != 1L)
}
