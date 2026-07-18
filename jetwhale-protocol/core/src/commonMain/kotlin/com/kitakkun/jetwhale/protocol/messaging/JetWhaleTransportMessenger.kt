package com.kitakkun.jetwhale.protocol.messaging

/** Outcome of a non-suspending [JetWhaleTransportMessenger.trySendRaw] enqueue. */
public enum class RawSendOutcome {
    /** The event was handed to the outgoing queue. */
    SENT,

    /** The outgoing buffer is full; the event was not enqueued. */
    BUFFER_FULL,

    /** The connection is closed; the event will never be sent. */
    CONNECTION_CLOSED,
}

/**
 * The low-level, always-connected transport surface an agent-side buffering messenger binds to.
 *
 * It extends the shared [JetWhaleConnectedMessenger] with a single non-suspending, tri-state
 * enqueue: unlike [JetWhaleConnectedMessenger.sendRaw] (which collapses every failure to `false`),
 * [trySendRaw] tells a full buffer apart from a closed connection. An agent's connection-independent
 * messenger needs that distinction to implement its offline send policies (drop vs. fail). Host
 * plugins never see this type — they use only [JetWhaleConnectedMessenger].
 */
public interface JetWhaleTransportMessenger : JetWhaleConnectedMessenger {
    /**
     * Non-suspending enqueue of a fire-and-forget event, reporting whether it was accepted, dropped
     * because the outgoing buffer is full, or rejected because the connection is closed.
     */
    public fun trySendRaw(messageType: String, payload: String): RawSendOutcome
}
