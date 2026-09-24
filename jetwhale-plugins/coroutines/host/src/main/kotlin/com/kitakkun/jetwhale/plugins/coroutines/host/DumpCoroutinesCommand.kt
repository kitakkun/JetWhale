package com.kitakkun.jetwhale.plugins.coroutines.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineDump

@OptIn(ExperimentalJetWhaleApi::class)
internal class DumpCoroutinesCommand(
    private val client: CoroutineInspectorClient,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.dumpCoroutines"
    override val description =
        "Returns every coroutine in the app with the stack it is suspended at, which shows where a stuck coroutine waits. Only an app on the JVM that installed kotlinx-coroutines-debug's DebugProbes can produce one; otherwise unavailableReason says why."

    override suspend fun execute(arguments: JetWhaleMcpArguments): String = McpJson.encodeToString(CoroutineDump.serializer(), client.dump())
}
