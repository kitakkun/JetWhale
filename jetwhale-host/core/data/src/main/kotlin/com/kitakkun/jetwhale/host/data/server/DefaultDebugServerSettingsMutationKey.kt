package com.kitakkun.jetwhale.host.data.server

import com.kitakkun.jetwhale.host.model.DebugServerSettings
import com.kitakkun.jetwhale.host.model.DebugServerSettingsMutationKey
import com.kitakkun.jetwhale.host.model.DebugWebSocketServer
import com.kitakkun.jetwhale.host.model.DebuggerSettingsRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import soil.query.MutationId
import soil.query.MutationKey
import soil.query.buildMutationKey

@ContributesBinding(AppScope::class, binding<DebugServerSettingsMutationKey>())
@Inject
class DefaultDebugServerSettingsMutationKey(
    private val settingsRepository: DebuggerSettingsRepository,
    private val debugWebSocketServer: DebugWebSocketServer,
) : DebugServerSettingsMutationKey,
    MutationKey<Unit, DebugServerSettings> by buildMutationKey(
        id = MutationId("debug_server_settings"),
        mutate = { settings: DebugServerSettings ->
            settingsRepository.updateDebugServerSettings(
                serverPort = settings.serverPort,
                wssPort = settings.wssPort,
                wssEnabled = settings.wssEnabled,
            )
            debugWebSocketServer.stop()
            debugWebSocketServer.start(
                host = "localhost",
                port = settings.serverPort,
                // A stored wss port with the connector switched off means "remember this for when it
                // is switched back on", not "bind it".
                wssPort = settings.wssPort.takeIf { settings.wssEnabled },
            )
        },
    )
