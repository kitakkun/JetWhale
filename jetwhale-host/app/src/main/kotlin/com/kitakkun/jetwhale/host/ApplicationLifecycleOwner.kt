package com.kitakkun.jetwhale.host

import com.kitakkun.jetwhale.host.mcp.McpServerService
import com.kitakkun.jetwhale.host.model.DebugWebSocketServer
import com.kitakkun.jetwhale.host.model.DebuggerSettingsRepository
import com.kitakkun.jetwhale.host.model.PluginHotReloadService
import com.kitakkun.jetwhale.host.model.PluginTrustService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Inject
@SingleIn(AppScope::class)
class ApplicationLifecycleOwner(
    private val server: DebugWebSocketServer,
    private val mcpServerService: McpServerService,
    private val pluginTrustService: PluginTrustService,
    private val pluginHotReloadService: PluginHotReloadService,
    private val settingsRepository: DebuggerSettingsRepository,
) {
    enum class ApplicationState {
        NONE,
        INITIALIZING,
        INITIALIZED,
        STOPPING,
        STOPPED,
    }

    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)

    private val mutableApplicationStateFlow: MutableStateFlow<ApplicationState> = MutableStateFlow(ApplicationState.NONE)
    val applicationStateFlow: StateFlow<ApplicationState> = mutableApplicationStateFlow

    fun initialize() {
        mutableApplicationStateFlow.update { ApplicationState.INITIALIZING }
        coroutineScope.launch {
            server.start(
                host = "localhost",
                port = settingsRepository.readServerPort(),
            )
            mcpServerService.start(
                host = "localhost",
                port = settingsRepository.readMcpServerPort(),
            )

            // Load only jars the user has explicitly approved (pinned by content hash). Anything else
            // in the plugins directory — a jar that was never approved, or one whose bytes changed
            // after approval — is surfaced for review instead of being executed. This is what stops a
            // malicious jar dropped into ~/.jetwhale/plugins from auto-running in the host process.
            pluginTrustService.loadTrustedPlugins()

            // Loads dev plugins (if any) and starts watching the dev directory for hot reload.
            // No-op unless the jetwhale.devPluginsDir system property is set.
            pluginHotReloadService.start()

            mutableApplicationStateFlow.update { ApplicationState.INITIALIZED }
        }
    }

    fun shutdown() {
        mutableApplicationStateFlow.update { ApplicationState.STOPPING }
        coroutineScope.launch {
            pluginHotReloadService.stop()
            mcpServerService.stop()
            server.stop()
            mutableApplicationStateFlow.update { ApplicationState.STOPPED }
        }
    }
}
