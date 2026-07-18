package com.kitakkun.jetwhale.host.data.server

import com.kitakkun.jetwhale.host.model.DebugWebSocketServer
import com.kitakkun.jetwhale.host.model.DebuggerSettingsRepository
import com.kitakkun.jetwhale.host.model.RestartDebugServerMutationKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import soil.query.MutationId
import soil.query.MutationKey
import soil.query.buildMutationKey

@ContributesBinding(AppScope::class, binding<RestartDebugServerMutationKey>())
@Inject
class DefaultRestartDebugServerMutationKey(
    private val settingsRepository: DebuggerSettingsRepository,
    private val debugWebSocketServer: DebugWebSocketServer,
) : RestartDebugServerMutationKey,
    MutationKey<Unit, Unit> by buildMutationKey(
        id = MutationId("restart_debug_server"),
        mutate = {
            debugWebSocketServer.stop()
            val wssPort = if (settingsRepository.wssEnabledFlow.value) {
                settingsRepository.wssPortFlow.value
            } else {
                null
            }
            debugWebSocketServer.start(
                host = "localhost",
                port = settingsRepository.serverPortFlow.value,
                wssPort = wssPort,
            )
        },
    )
