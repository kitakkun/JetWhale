package com.kitakkun.jetwhale.plugins.example.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@SerialName("example_method")
@Serializable
sealed interface ExampleMethod {
    @SerialName("ping")
    @Serializable
    data object Ping : ExampleMethod
}
