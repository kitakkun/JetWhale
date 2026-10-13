package com.kitakkun.jetwhale.plugins.background.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.background.protocol.CancelTarget

@OptIn(ExperimentalJetWhaleApi::class)
internal class CancelWorkCommand(
    private val client: BackgroundWorkClient,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.cancelWork"
    override val description =
        "Cancels background work in the app: one piece by id, everything with a tag, or the unique work of a name (WorkManager). Pass exactly one of id, tag and uniqueName. Work already running is stopped. It cannot be undone."

    private val source by string(SOURCE_ARGUMENT_DESCRIPTION)
    private val id by stringOrNull("The id of the work, as listBackgroundWork reports it.")
    private val tag by stringOrNull("Cancel every piece of work in the source with this tag (WorkManager).")
    private val uniqueName by stringOrNull("Cancel the unique work enqueued under this name (WorkManager). Unique names are not listed, so they come from the app's code.")

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val targets = listOfNotNull(
            arguments[id]?.let(CancelTarget::ById),
            arguments[tag]?.let(CancelTarget::ByTag),
            arguments[uniqueName]?.let(CancelTarget::ByUniqueName),
        )
        val target = targets.singleOrNull() ?: throw JetWhaleMcpArgumentException("pass exactly one of id, tag and uniqueName")
        return client.cancel(arguments[source], target).toMcpJson()
    }
}
