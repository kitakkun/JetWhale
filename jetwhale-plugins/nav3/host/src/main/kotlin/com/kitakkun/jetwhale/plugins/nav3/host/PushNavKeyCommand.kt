package com.kitakkun.jetwhale.plugins.nav3.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpResult
import com.kitakkun.jetwhale.plugins.nav3.protocol.NavBackStackOperation

@OptIn(ExperimentalJetWhaleApi::class)
internal class PushNavKeyCommand(
    private val controller: Nav3BackStackController,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.pushNavKey"
    override val description =
        "Navigates the app by pushing a NavKey onto its back stack. The key is a JSON object shaped like one of the templates from listNavKeyTypes (or the `key` of an existing entry from getBackStack); the app decodes it with its own serializers, so an unknown type is refused rather than guessed."

    private val key by jsonObject("The NavKey to push, e.g. {\"type\":\"Detail\",\"id\":\"42\"}.")
    private val index by intOrNull("Insert the key at this index instead of on top of the stack (0 is the root).")
    private val stackId by stringOrNull("Which back stack to push onto. Defaults to the app's only one.")

    override suspend fun execute(arguments: JetWhaleMcpArguments): JetWhaleMcpResult {
        val target = resolveStackId(arguments[stackId], controller.stacks().map { it.stackId })
        val result = controller.mutate(
            stackId = target,
            operations = listOf(NavBackStackOperation.Push(key = arguments[key], index = arguments[index])),
        )
        return JetWhaleMcpResult.text(result.toMcpJson())
    }
}
