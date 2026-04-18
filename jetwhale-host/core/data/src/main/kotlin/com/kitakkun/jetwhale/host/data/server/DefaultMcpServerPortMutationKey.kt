package com.kitakkun.jetwhale.host.data.server

import com.kitakkun.jetwhale.host.mcp.McpServerService
import com.kitakkun.jetwhale.host.model.DebuggerSettingsRepository
import com.kitakkun.jetwhale.host.model.McpServerPortMutationKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import soil.query.MutationId
import soil.query.MutationKey
import soil.query.buildMutationKey

@ContributesBinding(AppScope::class, binding<McpServerPortMutationKey>())
@Inject
class DefaultMcpServerPortMutationKey(
    private val settingsRepository: DebuggerSettingsRepository,
    private val mcpServerService: McpServerService,
) : McpServerPortMutationKey, MutationKey<Unit, Int> by buildMutationKey(
    id = MutationId("mcp_server_port"),
    mutate = { port: Int ->
        settingsRepository.updateMcpServerPort(port)
        mcpServerService.stop()
        mcpServerService.start(host = "localhost", port = port)
    },
)
