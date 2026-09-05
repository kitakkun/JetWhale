package com.kitakkun.jetwhale.plugins.example.host

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginUi
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwKeyValueRow
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwToolbar

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

    @Composable
    override fun Content() {
        Column(Modifier.fillMaxSize()) {
            JwToolbar(title = "Example Host-only Plugin")
            Column(
                modifier = Modifier.padding(JwSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(JwSpacing.md),
            ) {
                Text("This plugin has no agent counterpart and exchanges no messages — it is pure host UI.")
                JwKeyValueRow(key = "Host-side counter", value = counter.toString(), monospace = true)
                JwButton(text = "Increment", onClick = { counter++ }, style = JwButtonStyle.Primary)
            }
        }
    }
}
