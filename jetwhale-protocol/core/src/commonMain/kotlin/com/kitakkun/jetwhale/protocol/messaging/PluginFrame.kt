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
    /** Id of the plugin this frame belongs to; used to route the frame to that plugin's peer. */
    public val pluginId: String

    /**
     * Fire-and-forget message. Carries no correlation information by construction.
     *
     * @property pluginId Id of the plugin this notification is addressed to.
     * @property messageType Serial name of the concrete event type, used to pick its handler/serializer.
     * @property payload The event, serialized with the payload format.
     */
    @SerialName("frame/notification")
    @Serializable
    public data class Notification(
        override val pluginId: String,
        val messageType: String,
        val payload: String,
    ) : PluginFrame

    /**
     * A message that expects exactly one [Reply] correlated via [correlationId].
     *
     * @property pluginId Id of the plugin this request is addressed to.
     * @property correlationId Unique id the matching [Reply] echoes back in [Reply.inReplyTo].
     * @property messageType Serial name of the concrete request type, used to pick its handler/serializer.
     * @property payload The request, serialized with the payload format.
     */
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
        /** [Request.correlationId] of the request this reply answers. */
        public val inReplyTo: String

        /**
         * A request handled successfully; [payload] carries the serialized reply value.
         *
         * @property pluginId Id of the plugin that produced this reply.
         * @property inReplyTo [Request.correlationId] of the request being answered.
         * @property payload The reply value, serialized with the payload format. Its type is the one
         *   declared by the request's [JetWhaleRequest], so no message type is needed.
         */
        @SerialName("frame/reply/success")
        @Serializable
        public data class Success(
            override val pluginId: String,
            override val inReplyTo: String,
            val payload: String,
        ) : Reply

        /**
         * A request that could not be handled (no handler, handler threw, undispatchable, ...).
         *
         * @property pluginId Id of the plugin that produced this reply.
         * @property inReplyTo [Request.correlationId] of the request being answered.
         * @property errorMessage Human-readable reason; surfaced to the requester as a
         *   [JetWhaleRequestException].
         */
        @SerialName("frame/reply/failure")
        @Serializable
        public data class Failure(
            override val pluginId: String,
            override val inReplyTo: String,
            val errorMessage: String,
        ) : Reply
    }
}
