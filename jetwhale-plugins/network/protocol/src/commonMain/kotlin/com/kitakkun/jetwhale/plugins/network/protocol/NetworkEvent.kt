package com.kitakkun.jetwhale.plugins.network.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Messages sent from the agent (debuggee) to the host. */
@SerialName("network_event")
@Serializable
sealed interface NetworkEvent {
    @SerialName("request_sent")
    @Serializable
    data class RequestSent(val request: CapturedHttpRequest) : NetworkEvent

    @SerialName("response_received")
    @Serializable
    data class ResponseReceived(val response: CapturedHttpResponse) : NetworkEvent

    @SerialName("request_failed")
    @Serializable
    data class RequestFailed(val failure: HttpRequestFailure) : NetworkEvent
}
