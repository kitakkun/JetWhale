package com.kitakkun.jetwhale.plugins.network.host

import com.kitakkun.jetwhale.plugins.network.protocol.AppliedNetworkCondition
import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpRequest
import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpResponse
import com.kitakkun.jetwhale.plugins.network.protocol.HttpRequestFailure

/** Host-side correlation of a request with its eventual response or failure. */
data class HttpTransaction(
    val request: CapturedHttpRequest,
    val response: CapturedHttpResponse? = null,
    val failure: HttpRequestFailure? = null,
) {
    val txId: String get() = request.txId

    /** The network condition that shaped this exchange, once its outcome is known. */
    val condition: AppliedNetworkCondition? get() = response?.condition ?: failure?.condition
}
