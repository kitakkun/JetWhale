package com.kitakkun.jetwhale.plugins.example.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@SerialName("example_event")
@Serializable
sealed interface ExampleEvent {
    @SerialName("button_clicked")
    @Serializable
    data class ButtonClicked(val count: Int) : ExampleEvent
}
