package com.kitakkun.jetwhale.plugins.example.devpreview

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.window.singleWindowApplication
import com.kitakkun.jetwhale.host.sdk.JetWhaleRawDebugOperationContext
import com.kitakkun.jetwhale.plugins.example.host.ExampleHostPluginFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * Compose Hot Reload PoC harness that exercises the *real* plugin rendering path.
 *
 * Unlike a bare composable preview, this instantiates the actual plugin via its
 * [com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory] and renders it through the SDK's
 * `ContentRaw` entry point — the same path the host uses. The plugin's own state (its event log
 * `SnapshotStateList`) lives inside the plugin instance held by [main], so we can observe whether it
 * survives a hot reload of the plugin's UI code.
 *
 * Run on the JetBrains Runtime (see README):
 *   ./gradlew :jetwhale-plugins:example:dev-preview:hotRun --auto
 *
 * Then: click "Send ping" a few times (entries are added to the plugin's event log), edit
 * `ExamplePluginView` in `:jetwhale-plugins:example:host` and save. The UI updates with the new code
 * while the plugin instance — and its event log — is preserved.
 *
 * Caveat (the point of the PoC): this works because the plugin is a *compile-time classpath
 * dependency*, so Compose Hot Reload recompiles and redefines it like app code. It does NOT cover
 * the production model, where plugins are loaded from external jars via isolated classloaders — see
 * README for why CHR cannot reach those and what the hybrid end state is.
 */
fun main() {
    // Created once and kept alive across reloads; the plugin's state lives in this instance, exactly
    // as it does inside the host.
    val plugin = ExampleHostPluginFactory().createPlugin()

    val context = object : JetWhaleRawDebugOperationContext {
        override val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)

        // No debuggee is attached in this harness, so method dispatch is a no-op.
        override suspend fun dispatch(method: String): String? = null
    }

    singleWindowApplication(title = "Example Plugin — Hot Reload Preview") {
        MaterialTheme {
            plugin.ContentRaw(context)
        }
    }
}
