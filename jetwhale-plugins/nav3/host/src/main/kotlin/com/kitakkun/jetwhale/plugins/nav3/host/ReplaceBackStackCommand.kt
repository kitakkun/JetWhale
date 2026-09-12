package com.kitakkun.jetwhale.plugins.nav3.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpResult
import com.kitakkun.jetwhale.plugins.nav3.protocol.NavBackStackOperation
import kotlinx.serialization.json.JsonObject

@OptIn(ExperimentalJetWhaleApi::class)
internal class ReplaceBackStackCommand(
    private val controller: Nav3BackStackController,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.replaceBackStack"
    override val description =
        "Puts the app into an exact navigation state by replacing the whole back stack with the given keys, root first. Use it to reproduce a deep-linked state in one step instead of pushing screen by screen."

    private val keys by jsonArray("The new back stack as a JSON array of NavKey objects, root first, e.g. [{\"type\":\"Home\"},{\"type\":\"Detail\",\"id\":\"42\"}]. Must not be empty.")
    private val stackId by stringOrNull("Which back stack to replace. Defaults to the app's only one.")

    override suspend fun execute(arguments: JetWhaleMcpArguments): JetWhaleMcpResult {
        val target = resolveStackId(arguments[stackId], controller.stacks().map { it.stackId })
        val newKeys = arguments[keys]
        if (newKeys.isEmpty()) {
            throw JetWhaleMcpArgumentException("keys must not be empty: Navigation 3 cannot render an empty back stack")
        }
        newKeys.forEachIndexed { index, element ->
            if (element !is JsonObject) throw JetWhaleMcpArgumentException("keys[$index] is not a JSON object")
        }
        return JetWhaleMcpResult.text(
            controller.mutate(
                stackId = target,
                operations = listOf(NavBackStackOperation.ReplaceAll(keys = newKeys.toList())),
            ).toMcpJson(),
        )
    }
}
