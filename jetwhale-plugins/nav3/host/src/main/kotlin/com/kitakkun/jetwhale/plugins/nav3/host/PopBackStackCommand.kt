package com.kitakkun.jetwhale.plugins.nav3.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.nav3.protocol.NavBackStackOperation

@OptIn(ExperimentalJetWhaleApi::class)
internal class PopBackStackCommand(
    private val controller: Nav3BackStackController,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.popBackStack"
    override val description =
        "Navigates back: pops entries off the app's back stack, either a number of them or down to a given index. Popping the last remaining entry is refused, since Navigation 3 cannot render an empty stack."

    private val count by intOrNull("How many entries to pop from the top. Defaults to 1. Ignored when toIndex is given.")
    private val toIndex by intOrNull("Pop until the entry at this index is on top (0 is the root).")
    private val inclusive by booleanOrNull("With toIndex: also pop the entry at that index. Defaults to false.")
    private val stackId by stringOrNull("Which back stack to pop. Defaults to the app's only one.")

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val target = resolveStackId(arguments[stackId], controller.stacks().map { it.stackId })
        val requestedIndex = arguments[toIndex]
        val operation = when (requestedIndex) {
            null -> NavBackStackOperation.Pop(count = arguments[count] ?: 1)
            else -> NavBackStackOperation.PopTo(index = requestedIndex, inclusive = arguments[inclusive] ?: false)
        }
        return controller.mutate(stackId = target, operations = listOf(operation)).toMcpJson()
    }
}
