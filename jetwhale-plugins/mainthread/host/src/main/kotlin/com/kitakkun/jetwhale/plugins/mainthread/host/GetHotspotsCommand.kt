package com.kitakkun.jetwhale.plugins.mainthread.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.mainthread.protocol.Hotspot
import kotlinx.serialization.builtins.ListSerializer

/** Enough to cover what matters without flooding the caller with one-sample stacks. */
private const val DEFAULT_HOTSPOT_LIMIT = 10

@OptIn(ExperimentalJetWhaleApi::class)
internal class GetHotspotsCommand(
    private val client: MainThreadClient,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.getHotspots"
    override val description =
        "Returns where the app's main thread spent its time while it was blocked: stack samples taken during long tasks, grouped by the innermost app frames they end in (the signature) and ranked by the estimated time blocked there. Each hotspot's frames list is innermost first; the first frames that belong to the app are usually the code to move off the main thread."

    private val limit by intOrNull("How many hotspots to return, most blocking first. Defaults to $DEFAULT_HOTSPOT_LIMIT.")

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val report = client.report()
        val hotspots = report.hotspots.take(arguments[limit] ?: DEFAULT_HOTSPOT_LIMIT)
        return reportJson(report) { put("hotspots", McpJson.encodeToJsonElement(ListSerializer(Hotspot.serializer()), hotspots)) }
    }
}
