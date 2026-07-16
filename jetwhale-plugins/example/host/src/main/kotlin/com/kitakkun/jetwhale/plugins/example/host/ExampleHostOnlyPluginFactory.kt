package com.kitakkun.jetwhale.plugins.example.host

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginUi

// Instantiated by the host via the fully-qualified name declared in plugin-manifest.json.
@Suppress("UNUSED")
class ExampleHostOnlyPluginFactory : JetWhaleHostPluginFactory {
    override fun createPlugin(): JetWhaleHostPlugin = ExampleHostOnlyPlugin()
}

/**
 * A **host-only** example plugin: it extends the plain [JetWhaleHostPlugin] (no
 * [com.kitakkun.jetwhale.host.sdk.JetWhaleMessagingHostPlugin]), so it has no agent counterpart and
 * does no messaging — just host-side UI and state. Its manifest entry sets `"requiresAgent": false`,
 * which makes it available for every active session regardless of negotiation.
 */
private class ExampleHostOnlyPlugin :
    JetWhaleHostPlugin(),
    JetWhaleHostPluginUi {

    private var counter by mutableIntStateOf(0)

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        Scaffold(
            topBar = { TopAppBar(title = { Text("Example Host-only Plugin") }) },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(it)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("This plugin has no agent counterpart and exchanges no messages — it is pure host UI.")
                Text("Host-side counter: $counter")
                Button(onClick = { counter++ }) {
                    Text("Increment")
                }
            }
        }
    }
}
