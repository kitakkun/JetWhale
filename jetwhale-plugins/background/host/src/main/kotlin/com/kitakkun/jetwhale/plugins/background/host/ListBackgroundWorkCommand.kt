package com.kitakkun.jetwhale.plugins.background.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.background.protocol.BackgroundWorkSnapshot
import com.kitakkun.jetwhale.plugins.background.protocol.WorkState

@OptIn(ExperimentalJetWhaleApi::class)
internal class ListBackgroundWorkCommand(
    private val client: BackgroundWorkClient,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.listBackgroundWork"
    override val description =
        "Lists the app's scheduled background work: WorkManager work (with the adapter), JobScheduler jobs, the app's alarm clock and running services on Android, BGTaskScheduler requests on iOS. Each item has its state, worker or job class, tags, constraints, next run time, periodicity, progress, output, stop reason, and whether cancelWork and runWorkNow work for it; runNowHint gives an adb or lldb command where the app itself cannot force a run. Sources lists each scheduler and why one is unavailable."

    private val state by enumOrNull("Only work in this state.", WorkState.entries)
    private val query by stringOrNull("Only work whose name, id, tag or source contains this text, ignoring case.")

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val snapshot = client.snapshot()
        val filter = WorkFilter(states = setOfNotNull(arguments[state]), query = arguments[query].orEmpty())
        return McpJson.encodeToString(
            BackgroundWorkSnapshot.serializer(),
            snapshot.copy(items = snapshot.items.filter(filter::matches)),
        )
    }
}
