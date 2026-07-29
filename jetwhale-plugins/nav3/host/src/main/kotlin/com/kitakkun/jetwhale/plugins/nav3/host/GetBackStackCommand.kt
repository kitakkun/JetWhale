package com.kitakkun.jetwhale.plugins.nav3.host

import com.kitakkun.jetwhale.host.sdk.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

@OptIn(ExperimentalJetWhaleApi::class)
internal class GetBackStackCommand(
    private val controller: Nav3BackStackController,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.getBackStack"
    override val description =
        "Returns the app's current Navigation 3 back stack(s): every entry with its index, type name, display string and the JSON key that can be pushed again. Index 0 is the root; the last entry is what the app is showing."

    private val stackId by stringOrNull("Which back stack to read. Returns every registered stack if omitted.")

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val requested = arguments[stackId]
        val stacks = controller.stacks().filter { requested == null || it.stackId == requested }
        return buildJsonObject {
            putJsonArray("stacks") { stacks.forEach { add(it.toMcpJson()) } }
            if (stacks.isEmpty()) {
                put(
                    "note",
                    if (requested == null) {
                        "The app has no Navigation 3 back stack registered; it must call TrackNavBackStack (or registerBackStack) first."
                    } else {
                        "No back stack is registered as '$requested'."
                    },
                )
            }
        }.toString()
    }
}
