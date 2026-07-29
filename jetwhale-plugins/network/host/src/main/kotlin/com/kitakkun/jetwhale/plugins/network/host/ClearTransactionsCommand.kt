package com.kitakkun.jetwhale.plugins.network.host

import com.kitakkun.jetwhale.host.sdk.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpResult

@OptIn(ExperimentalJetWhaleApi::class)
internal class ClearTransactionsCommand(
    private val clearTransactions: () -> Int,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.clearTransactions"
    override val description = "Clears the captured HTTP transaction list."

    private val cleared = serializableOutput<ClearedTransactionsResult>()

    override suspend fun execute(arguments: JetWhaleMcpArguments): JetWhaleMcpResult = cleared.result(ClearedTransactionsResult(clearedCount = clearTransactions()))
}
