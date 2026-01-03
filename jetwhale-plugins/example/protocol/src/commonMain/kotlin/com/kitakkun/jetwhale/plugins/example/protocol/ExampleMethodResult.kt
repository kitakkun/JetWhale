package com.kitakkun.jetwhale.plugins.example.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@SerialName("example_method_result")
@Serializable
sealed interface ExampleMethodResult {
    @SerialName("pong")
    @Serializable
    data object Pong : ExampleMethodResult
}
