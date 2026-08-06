package com.kitakkun.jetwhale.plugins.nav3.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.nav3.protocol.NavBackStackOperation

@OptIn(ExperimentalJetWhaleApi::class)
internal class RemoveNavKeyCommand(
    private val controller: Nav3BackStackController,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.removeNavKeyAt"
    override val description =
        "Removes a single entry from the middle of the back stack, keeping the entries above it. Use it to rewrite where 'back' will land without leaving the current screen."

    private val index by int("Index of the entry to remove (0 is the root).")
    private val stackId by stringOrNull("Which back stack to edit. Defaults to the app's only one.")

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val target = resolveStackId(arguments[stackId], controller.stacks().map { it.stackId })
        return controller.mutate(
            stackId = target,
            operations = listOf(NavBackStackOperation.RemoveAt(index = arguments[index])),
        ).toMcpJson()
    }
}
