package com.kitakkun.jetwhale.plugins.example.devpreview

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.window.singleWindowApplication
import com.kitakkun.jetwhale.plugins.example.host.ExamplePluginView

/**
 * Compose Hot Reload PoC harness for a JetWhale plugin's UI.
 *
 * This renders the example plugin's [ExamplePluginView] directly on the application classpath (not
 * via a loaded jar / separate classloader), so JetBrains Compose Hot Reload can redefine the
 * plugin's classes in place and recompose while preserving Compose state.
 *
 * Run on the JetBrains Runtime (see this module's README):
 *   ./gradlew :jetwhale-plugins:example:dev-preview:hotRun --auto
 * Add a few log entries, then edit [ExamplePluginView] (e.g. change a button label) and save: the UI
 * updates while the `eventLogs` state below is preserved.
 *
 * Note: `DevelopmentEntryPoint` (from `org.jetbrains.compose.hot-reload:runtime-api`) can wrap the
 * content to scope reloads, but is not required — `hotRun` reloads the whole window composition.
 */
fun main() = singleWindowApplication(title = "Example Plugin — Hot Reload Preview") {
    MaterialTheme {
        // State intentionally hoisted here (outside the reloaded composable) so we can observe that
        // it survives a hot reload of ExamplePluginView.
        val eventLogs = remember { mutableStateListOf<String>() }
        ExamplePluginView(
            eventLogs = eventLogs,
            onClickSendPing = { eventLogs.add("ping #${eventLogs.size + 1}") },
            onClickTriggerUIError = { eventLogs.add("error button clicked") },
        )
    }
}
