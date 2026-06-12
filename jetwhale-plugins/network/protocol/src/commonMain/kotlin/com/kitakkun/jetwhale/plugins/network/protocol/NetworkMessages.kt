package com.kitakkun.jetwhale.plugins.network.protocol

import com.kitakkun.jetwhale.protocol.messaging.JetWhaleEvent
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleRequest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// -- Events: agent (debuggee) -> host ----------------------------------------

@SerialName("network/request_sent")
@Serializable
data class RequestSent(val request: CapturedHttpRequest) : JetWhaleEvent

@SerialName("network/response_received")
@Serializable
data class ResponseReceived(val response: CapturedHttpResponse) : JetWhaleEvent

@SerialName("network/request_failed")
@Serializable
data class RequestFailed(val failure: HttpRequestFailure) : JetWhaleEvent

// -- Requests: host -> agent (debuggee) --------------------------------------

@SerialName("network/set_mock_rules")
@Serializable
data class SetMockRules(val rules: List<MockRule>) : JetWhaleRequest<Ack>

@SerialName("network/set_mocking_enabled")
@Serializable
data class SetMockingEnabled(val enabled: Boolean) : JetWhaleRequest<Ack>

/** Lets the host re-sync the current mock configuration (e.g. on connect). */
@SerialName("network/get_mock_config")
@Serializable
data object GetMockConfig : JetWhaleRequest<MockConfig>

// -- Replies -----------------------------------------------------------------

@SerialName("network/ack")
@Serializable
data object Ack

@SerialName("network/mock_config")
@Serializable
data class MockConfig(
    val enabled: Boolean,
    val rules: List<MockRule>,
)
