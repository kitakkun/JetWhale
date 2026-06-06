package com.kitakkun.jetwhale.plugins.example.devpreview

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.window.singleWindowApplication
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleRawDebugOperationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.util.ServiceLoader

/**
 * Compose Hot Reload "dev-host" spike.
 *
 * Instead of loading a plugin from an external jar via a child classloader (the production model,
 * which Compose Hot Reload cannot reach), this discovers the plugin from the **application
 * classpath** via [ServiceLoader] — the same `@AutoService(JetWhaleHostPluginFactory::class)`
 * mechanism the host uses — and renders it through the SDK's `ContentRaw` entry point.
 *
 * Because the plugin is a compile-time classpath dependency here, Compose Hot Reload can redefine it
 * in place and recompose while preserving the plugin instance's state.
 *
 * This is the local proof of the "dev-host" approach: a plain dev JetWhale that finds plugins on its
 * classpath and runs under CHR. The external-developer version would resolve a published dev-host
 * launcher + the developer's plugin module via a Gradle `hotdev` task (see README).
 *
 * Run on the JetBrains Runtime:
 *   ./gradlew :jetwhale-plugins:example:dev-preview:hotRun --auto
 */
fun main() {
    // Discover the plugin the same way the host does: ServiceLoader over the classpath. Whatever
    // plugin module is on this app's classpath is picked up; here it is :jetwhale-plugins:example:host.
    val factory = ServiceLoader.load(JetWhaleHostPluginFactory::class.java).firstOrNull()
        ?: error("No JetWhaleHostPluginFactory found on the classpath")

    // Created once and kept alive across reloads; the plugin's state lives in this instance.
    val plugin = factory.createPlugin()

    val context = object : JetWhaleRawDebugOperationContext {
        override val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)

        // No debuggee is attached in this harness, so method dispatch is a no-op.
        override suspend fun dispatch(method: String): String? = null
    }

    singleWindowApplication(title = "JetWhale dev-host — Hot Reload Preview") {
        MaterialTheme {
            plugin.ContentRaw(context)
        }
    }
}
