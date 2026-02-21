package com.kitakkun.jetwhale.host

import com.kitakkun.jetwhale.host.data.AppDataDirectoryProvider
import com.kitakkun.jetwhale.host.model.DebugWebSocketServer
import com.kitakkun.jetwhale.host.model.DebuggerSettingsRepository
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
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
    private val appDataDirectoryProvider: AppDataDirectoryProvider,
    private val pluginFactoryRepository: PluginFactoryRepository,
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

            appDataDirectoryProvider.getAllPluginJarFilePaths().forEach {
                pluginFactoryRepository.loadPluginFactory(it)
            }

            mutableApplicationStateFlow.update { ApplicationState.INITIALIZED }
        }
    }

    fun shutdown() {
        mutableApplicationStateFlow.update { ApplicationState.STOPPING }
        coroutineScope.launch {
            server.stop()
            mutableApplicationStateFlow.update { ApplicationState.STOPPED }
        }
    }
}
