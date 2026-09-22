package com.kitakkun.jetwhale.host.idea

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.awt.ComposePanel
import com.kitakkun.jetwhale.host.ApplicationLifecycleOwner
import com.kitakkun.jetwhale.host.JetWhaleApp
import com.kitakkun.jetwhale.host.component.InitializingDialog
import com.kitakkun.jetwhale.host.component.ShuttingDownDialog
import com.kitakkun.jetwhale.host.di.JetWhaleAppGraph
import com.kitakkun.jetwhale.host.model.AdditionalPluginDirectories
import com.kitakkun.jetwhale.host.model.McpPermissionOverride
import com.kitakkun.jetwhale.host.model.ServerPortOverrides
import com.kitakkun.jetwhale.host.theme.LocalEmbeddedInIde
import com.kitakkun.jetwhale.host.ui.JwTheme
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import javax.swing.JComponent
import kotlin.time.Duration.Companion.seconds

/**
 * The whole JetWhale host, started inside an IDE process. The IDE plugin instantiates this class
 * reflectively through an isolated classloader and talks to it only through JDK types
 * ([JComponent], [AutoCloseable]), so nothing from Kotlin, coroutines or Compose crosses between
 * the IDE's copies of those libraries and the host's own.
 *
 * One instance per IDE process, the way the desktop app has one per JVM: tool windows in every
 * project render the same graph, so the servers bind once and a device connects to the IDE, not to
 * a project.
 */
@Suppress("unused")
class IdeHost : AutoCloseable {
    private val appGraph: JetWhaleAppGraph = createGraphFactory<JetWhaleAppGraph.Factory>()
        .create(
            serverPortOverrides = ServerPortOverrides(serverPort = null, wssPort = null, mcpServerPort = null),
            mcpPermissionOverride = McpPermissionOverride.None,
            additionalPluginDirectories = AdditionalPluginDirectories(emptyList()),
        )

    init {
        appGraph.logCaptureService.startCapture()
        appGraph.applicationLifecycleOwner.initialize()
    }

    fun createToolWindowContent(): JComponent = ComposePanel().apply {
        setContent {
            val applicationState by appGraph.applicationLifecycleOwner.applicationStateFlow.collectAsState()
            val verifyingTrustRegistry by appGraph.pluginTrustService.verifyingTrustRegistryFlow.collectAsState()

            JwTheme(darkTheme = isSystemInDarkTheme()) {
                when (applicationState) {
                    ApplicationLifecycleOwner.ApplicationState.INITIALIZING ->
                        InitializingDialog(verifyingTrustRegistry = verifyingTrustRegistry)

                    ApplicationLifecycleOwner.ApplicationState.STOPPING -> ShuttingDownDialog()

                    ApplicationLifecycleOwner.ApplicationState.NONE,
                    ApplicationLifecycleOwner.ApplicationState.INITIALIZED,
                    ApplicationLifecycleOwner.ApplicationState.STOPPED,
                    -> Unit
                }
            }

            CompositionLocalProvider(LocalEmbeddedInIde provides true) {
                context(appGraph) {
                    JetWhaleApp()
                }
            }
        }
    }

    /**
     * Blocks until the servers and plugins have released everything, because the caller closes
     * the classloader that all of it runs in as soon as this returns. Bounded so a hung shutdown
     * cannot stall the IDE's own exit indefinitely.
     */
    override fun close() {
        val lifecycleOwner = appGraph.applicationLifecycleOwner
        lifecycleOwner.shutdown()
        runBlocking {
            withTimeoutOrNull(SHUTDOWN_TIMEOUT) {
                lifecycleOwner.applicationStateFlow.first { it == ApplicationLifecycleOwner.ApplicationState.STOPPED }
            }
        }
    }

    private companion object {
        val SHUTDOWN_TIMEOUT = 10.seconds
    }
}
