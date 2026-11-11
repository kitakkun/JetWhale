package com.kitakkun.jetwhale.plugins.nav3.host

import com.kitakkun.jetwhale.plugins.nav3.protocol.MutationResult
import com.kitakkun.jetwhale.plugins.nav3.protocol.NavBackStackOperation
import com.kitakkun.jetwhale.plugins.nav3.protocol.NavBackStackSnapshot
import com.kitakkun.jetwhale.plugins.nav3.protocol.NavKeyTypeDescriptor

/** The plugin's commands for previews and docs screenshots, which draw the panes but never run a command. */
internal val previewMcpCommands = Nav3McpCommands(NoBackStackController)

private object NoBackStackController : Nav3BackStackController {
    override fun stacks(): List<NavBackStackSnapshot> = emptyList()

    override fun keyTypes(): List<NavKeyTypeDescriptor> = emptyList()

    override suspend fun mutate(stackId: String, operations: List<NavBackStackOperation>): MutationResult = error("A preview has no back stack")
}
