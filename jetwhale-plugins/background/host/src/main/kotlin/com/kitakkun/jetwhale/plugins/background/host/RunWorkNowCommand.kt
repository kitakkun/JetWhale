package com.kitakkun.jetwhale.plugins.background.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand

@OptIn(ExperimentalJetWhaleApi::class)
internal class RunWorkNowCommand(
    private val client: BackgroundWorkClient,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.runWorkNow"
    override val description =
        "Runs a piece of background work now, where the app can. For WorkManager this enqueues a one-time copy of the same worker with no constraints or delay and empty input data (WorkManager cannot start enqueued work early); the original keeps waiting. JobScheduler jobs and iOS task requests cannot be forced from inside the app: the error says so, and listBackgroundWork's runNowHint has the adb or lldb command instead. The message says what was actually done."

    private val source by string(SOURCE_ARGUMENT_DESCRIPTION)
    private val id by string("The id of the work, as listBackgroundWork reports it.")

    override suspend fun execute(arguments: JetWhaleMcpArguments): String = client.runNow(arguments[source], arguments[id]).toMcpJson()
}
