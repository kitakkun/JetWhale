package com.kitakkun.jetwhale.plugins.example.protocol

import com.kitakkun.jetwhale.protocol.messaging.JetWhaleEvent
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleRequest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Fire-and-forget event sent from the agent to the host when the demo button is clicked. */
@SerialName("example/button_clicked")
@Serializable
data class ButtonClicked(val count: Int) : JetWhaleEvent

/** Request sent from the host to the agent; the agent replies with [Pong]. */
@SerialName("example/ping")
@Serializable
data object Ping : JetWhaleRequest<Pong>

/** Reply to [Ping]. */
@SerialName("example/pong")
@Serializable
data object Pong
