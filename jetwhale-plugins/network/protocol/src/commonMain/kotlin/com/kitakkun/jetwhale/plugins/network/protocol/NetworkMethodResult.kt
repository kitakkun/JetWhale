package com.kitakkun.jetwhale.plugins.network.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Results returned from the agent to the host in response to a [NetworkMethod]. */
@SerialName("network_method_result")
@Serializable
sealed interface NetworkMethodResult {
    @SerialName("ack")
    @Serializable
    data object Ack : NetworkMethodResult

    @SerialName("mock_config")
    @Serializable
    data class MockConfig(
        val enabled: Boolean,
        val rules: List<MockRule>,
    ) : NetworkMethodResult
}
