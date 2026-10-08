package com.kitakkun.jetwhale.host

import com.kitakkun.jetwhale.host.mcp.McpServerService
import com.kitakkun.jetwhale.host.model.DebugWebSocketServer
import com.kitakkun.jetwhale.host.model.DebuggerSettingsRepository
import com.kitakkun.jetwhale.host.model.PluginDirectoryWatchService
import com.kitakkun.jetwhale.host.model.PluginHotReloadService
import com.kitakkun.jetwhale.host.model.PluginInstallJobService
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
    private val debugWebSocketServer: DebugWebSocketServer,
    private val mcpServerService: McpServerService,
    private val pluginTrustService: PluginTrustService,
    private val pluginHotReloadService: PluginHotReloadService,
    private val pluginDirectoryWatchService: PluginDirectoryWatchService,
    private val settingsRepository: DebuggerSettingsRepository,
    private val pluginInstallJobService: PluginInstallJobService,
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
            val wssPort = if (settingsRepository.readWssEnabled()) {
                settingsRepository.readWssPort()
            } else {
                null
            }
            debugWebSocketServer.start(
                host = "localhost",
                port = settingsRepository.readServerPort(),
                wssPort = wssPort,
            )
            mcpServerService.start(
                host = "localhost",
                port = settingsRepository.readMcpServerPort(),
            )

            pluginTrustService.loadTrustedPlugins()
            pluginDirectoryWatchService.start()

            pluginHotReloadService.start()

            mutableApplicationStateFlow.update { ApplicationState.INITIALIZED }
        }
    }

    fun shutdown() {
        mutableApplicationStateFlow.update { ApplicationState.STOPPING }
        coroutineScope.launch {
            pluginInstallJobService.cancelAll()
            pluginHotReloadService.stop()
            pluginDirectoryWatchService.stop()
            mcpServerService.stop()
            debugWebSocketServer.stop()
            mutableApplicationStateFlow.update { ApplicationState.STOPPED }
        }
    }
}
