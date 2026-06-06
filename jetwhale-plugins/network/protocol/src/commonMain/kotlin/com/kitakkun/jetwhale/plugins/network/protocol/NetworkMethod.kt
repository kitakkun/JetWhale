package com.kitakkun.jetwhale.plugins.network.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Requests sent from the host to the agent (debuggee). */
@SerialName("network_method")
@Serializable
sealed interface NetworkMethod {
    @SerialName("set_mock_rules")
    @Serializable
    data class SetMockRules(val rules: List<MockRule>) : NetworkMethod

    @SerialName("set_mocking_enabled")
    @Serializable
    data class SetMockingEnabled(val enabled: Boolean) : NetworkMethod

    /** Lets the host re-sync the current mock configuration (e.g. on connect). */
    @SerialName("get_mock_config")
    @Serializable
    data object GetMockConfig : NetworkMethod
}
