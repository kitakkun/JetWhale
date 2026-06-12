package com.kitakkun.jetwhale.protocol.messaging

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The single wire envelope for plugin messaging, in both directions (host -> agent and
 * agent -> host). Request/response is not a separate channel: a [Request] is just a one-way frame
 * carrying a [Request.correlationId], and a [Reply] is a one-way frame answering it via
 * [Reply.inReplyTo]. The sealed shape makes invalid states (a notification with a correlation id,
 * a reply carrying both payload and error, ...) unrepresentable.
 *
 * [Notification.messageType] / [Request.messageType] identify the concrete payload type (the
 * serial name captured at handler-registration / send time). A [Reply] needs no message type: the
 * requester already knows the expected reply type from the request's [JetWhaleRequest] declaration.
 */
@Serializable
public sealed interface PluginFrame {
    public val pluginId: String

    /** Fire-and-forget message. Carries no correlation information by construction. */
    @SerialName("frame/notification")
    @Serializable
    public data class Notification(
        override val pluginId: String,
        val messageType: String,
        val payload: String,
    ) : PluginFrame

    /** A message that expects exactly one [Reply] correlated via [correlationId]. */
    @SerialName("frame/request")
    @Serializable
    public data class Request(
        override val pluginId: String,
        val correlationId: String,
        val messageType: String,
        val payload: String,
    ) : PluginFrame

    /** The answer to a [Request], correlated via [inReplyTo]. Exactly success or failure. */
    @Serializable
    public sealed interface Reply : PluginFrame {
        public val inReplyTo: String

        @SerialName("frame/reply/success")
        @Serializable
        public data class Success(
            override val pluginId: String,
            override val inReplyTo: String,
            val payload: String,
        ) : Reply

        @SerialName("frame/reply/failure")
        @Serializable
        public data class Failure(
            override val pluginId: String,
            override val inReplyTo: String,
            val errorMessage: String,
        ) : Reply
    }
}
