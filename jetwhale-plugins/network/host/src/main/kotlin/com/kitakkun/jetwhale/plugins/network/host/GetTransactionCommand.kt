package com.kitakkun.jetwhale.plugins.network.host

import com.kitakkun.jetwhale.host.sdk.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand

@OptIn(ExperimentalJetWhaleApi::class)
internal class GetTransactionCommand(
    private val transactions: () -> List<HttpTransaction>,
    private val redactForMcp: (HttpTransaction) -> HttpTransaction,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.getTransaction"
    override val description = "Returns the full detail of one captured HTTP transaction (request/response headers and bodies, or the failure)."

    private val txIdParam = requiredString("txId", "The transaction id from listTransactions.")

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val txId = arguments[txIdParam]
        val transaction = transactions().firstOrNull { it.txId == txId }
            ?: throw JetWhaleMcpArgumentException("no transaction with txId: $txId")
        return redactForMcp(transaction).toDetailJson().toString()
    }
}
