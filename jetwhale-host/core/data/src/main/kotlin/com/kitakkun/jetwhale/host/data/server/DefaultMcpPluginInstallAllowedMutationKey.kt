package com.kitakkun.jetwhale.host.data.server

import com.kitakkun.jetwhale.host.model.DebuggerSettingsRepository
import com.kitakkun.jetwhale.host.model.McpPluginInstallAllowedMutationKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import soil.query.MutationId
import soil.query.MutationKey
import soil.query.buildMutationKey

@ContributesBinding(AppScope::class, binding<McpPluginInstallAllowedMutationKey>())
@Inject
class DefaultMcpPluginInstallAllowedMutationKey(
    private val settingsRepository: DebuggerSettingsRepository,
) : McpPluginInstallAllowedMutationKey,
    MutationKey<Unit, Boolean> by buildMutationKey(
        id = MutationId("mcp_plugin_install_allowed"),
        mutate = { isAllowed: Boolean ->
            settingsRepository.updateMcpPluginInstallAllowed(enabled = isAllowed)
        },
    )
