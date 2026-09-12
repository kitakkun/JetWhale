package com.kitakkun.jetwhale.plugins.network.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpResult

@OptIn(ExperimentalJetWhaleApi::class)
internal class GetTransactionCommand(
    private val transactions: () -> List<HttpTransaction>,
    private val redactForMcp: (HttpTransaction) -> HttpTransaction,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.getTransaction"
    override val description = "Returns the full detail of one captured HTTP transaction (request/response headers and bodies, or the failure)."

    private val txId by string("The transaction id from listTransactions.")

    override suspend fun execute(arguments: JetWhaleMcpArguments): JetWhaleMcpResult {
        val txId = arguments[this.txId]
        val transaction = transactions().firstOrNull { it.txId == txId }
            ?: throw JetWhaleMcpArgumentException("no transaction with txId: $txId")
        return JetWhaleMcpResult.text(redactForMcp(transaction).toDetailJson().toString())
    }
}
