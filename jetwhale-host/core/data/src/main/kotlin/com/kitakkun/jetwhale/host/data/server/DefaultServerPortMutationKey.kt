package com.kitakkun.jetwhale.host.data.server

import com.kitakkun.jetwhale.host.model.DebugWebSocketServer
import com.kitakkun.jetwhale.host.model.DebuggerSettingsRepository
import com.kitakkun.jetwhale.host.model.ServerPortMutationKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import soil.query.MutationId
import soil.query.buildMutationKey

@ContributesBinding(AppScope::class)
@Inject
class DefaultServerPortMutationKey(
    private val settingsRepository: DebuggerSettingsRepository,
    private val debugWebSocketServer: DebugWebSocketServer,
) : ServerPortMutationKey by buildMutationKey(
    id = MutationId("server_port"),
    mutate = { port: Int ->
        settingsRepository.updateServerPort(port)
        debugWebSocketServer.stop()
        debugWebSocketServer.start(host = "localhost", port = port)
    },
)
