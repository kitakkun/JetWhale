package com.kitakkun.jetwhale.plugins.mainthread.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.mainthread.protocol.ViolationGroup
import com.kitakkun.jetwhale.plugins.mainthread.protocol.ViolationKind
import kotlinx.serialization.builtins.ListSerializer

@OptIn(ExperimentalJetWhaleApi::class)
internal class GetViolationsCommand(
    private val client: MainThreadClient,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.getViolations"
    override val description =
        "Returns StrictMode violations the app committed on its main thread (Android 9+): disk reads and writes, network access, custom slow calls, grouped by kind and by the app call site that caused them, most frequent first, each with the stack of its latest occurrence. Collected only when the app sets no StrictMode thread policy of its own; capabilities.strictMode says whether they are being collected."

    private val kind by enumOrNull("Only violations of this kind.", ViolationKind.entries)

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val report = client.report()
        val requested = arguments[kind]
        val violations = report.violations.filter { requested == null || it.kind == requested }
        return reportJson(report) { put("violations", McpJson.encodeToJsonElement(ListSerializer(ViolationGroup.serializer()), violations)) }
    }
}
