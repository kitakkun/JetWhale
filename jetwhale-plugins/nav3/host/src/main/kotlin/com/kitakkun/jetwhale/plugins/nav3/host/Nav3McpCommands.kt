package com.kitakkun.jetwhale.plugins.nav3.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand

/**
 * Each of the plugin's MCP commands, built once, so the UI can ring the pane that shows what a running
 * command reads or changes.
 */
@OptIn(ExperimentalJetWhaleApi::class)
internal class Nav3McpCommands(controller: Nav3BackStackController) {
    val getBackStack = GetBackStackCommand(controller)
    val listNavKeyTypes = ListNavKeyTypesCommand(controller)
    val pushNavKey = PushNavKeyCommand(controller)
    val popBackStack = PopBackStackCommand(controller)
    val removeNavKey = RemoveNavKeyCommand(controller)
    val moveNavKeyToTop = MoveNavKeyToTopCommand(controller)
    val replaceBackStack = ReplaceBackStackCommand(controller)

    val all: List<JetWhaleMcpCommand> = listOf(getBackStack, listNavKeyTypes, pushNavKey, popBackStack, removeNavKey, moveNavKeyToTop, replaceBackStack)
}
