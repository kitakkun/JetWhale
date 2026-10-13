package com.kitakkun.jetwhale.plugins.network.host

import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpRequest
import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpResponse
import com.kitakkun.jetwhale.plugins.network.protocol.HttpRequestFailure
import com.kitakkun.jetwhale.plugins.network.protocol.RedactionRule
import com.kitakkun.jetwhale.plugins.network.protocol.redact

/** Host-side correlation of a request with its eventual response or failure. */
data class HttpTransaction(
    val request: CapturedHttpRequest,
    val response: CapturedHttpResponse? = null,
    val failure: HttpRequestFailure? = null,
) {
    val txId: String get() = request.txId
}

/** [transaction] with these rules applied to its request, its response and its failure. */
internal fun List<RedactionRule>.redact(transaction: HttpTransaction): HttpTransaction {
    if (isEmpty()) return transaction
    return transaction.copy(
        request = redact(transaction.request),
        response = transaction.response?.let { redact(it) },
        failure = transaction.failure?.let { redact(it) },
    )
}
