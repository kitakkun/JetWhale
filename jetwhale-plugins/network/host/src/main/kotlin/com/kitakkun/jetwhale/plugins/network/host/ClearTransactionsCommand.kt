package com.kitakkun.jetwhale.plugins.network.host

import com.kitakkun.jetwhale.host.sdk.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@OptIn(ExperimentalJetWhaleApi::class)
internal class ClearTransactionsCommand(
    private val clearTransactions: () -> Int,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.clearTransactions"
    override val description = "Clears the captured HTTP transaction list."

    override suspend fun execute(arguments: JetWhaleMcpArguments): String = buildJsonObject { put("clearedCount", clearTransactions()) }.toString()
}
