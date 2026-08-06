package com.kitakkun.jetwhale.plugins.nav3.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.nav3.protocol.NavBackStackOperation

@OptIn(ExperimentalJetWhaleApi::class)
internal class MoveNavKeyToTopCommand(
    private val controller: Nav3BackStackController,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.moveNavKeyToTop"
    override val description =
        "Brings an entry that is already on the back stack to the top, so the app shows it again without a second copy of it in the stack."

    private val index by int("Index of the entry to bring to the top (0 is the root).")
    private val stackId by stringOrNull("Which back stack to edit. Defaults to the app's only one.")

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val target = resolveStackId(arguments[stackId], controller.stacks().map { it.stackId })
        return controller.mutate(
            stackId = target,
            operations = listOf(NavBackStackOperation.MoveToTop(index = arguments[index])),
        ).toMcpJson()
    }
}
