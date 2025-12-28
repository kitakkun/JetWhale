package com.kitakkun.jetwhale.debugger.agent.runtime

import com.kitakkun.jetwhale.debugger.protocol.InternalJetWhaleApi
import com.kitakkun.jetwhale.debugger.protocol.serialization.JetWhaleJson
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Dispatches events to the debugger.
 * Various implementations can have different policies for handling events.
 *
 * @see DropIfDisconnectedDispatcher for dropping events when there is no connected sender.
 * @see BufferedEventDispatcher for buffering events until a sender is attached.
 */
public interface EventDispatcher<Event> {
    public fun dispatch(event: Event)
}

/**
 * Internal base implementation of [SenderAttachable].
 */
internal open class DefaultSenderAttachable : SenderAttachable {
    var sender: JetWhaleEventSender? = null
        private set

    final override fun detachSender() {
        sender = null
    }

    override fun attachSender(sender: JetWhaleEventSender) {
        this.sender = sender
    }
}

/**
 * Allows attaching and detaching a [JetWhaleEventSender].
 */
public interface SenderAttachable {
    public fun detachSender()
    public fun attachSender(sender: JetWhaleEventSender)
}

/**
 * Sends serialized events to the debugger.
 */
public fun interface JetWhaleEventSender {
    public fun send(serializedEvent: String)
}

/**
 * An [EventDispatcher] that drops events if there is no connected sender.
 */
internal class DropIfDisconnectedDispatcher<Event> internal constructor(
    private val eventSerializer: KSerializer<Event>,
    private val json: Json,
) : EventDispatcher<Event>, DefaultSenderAttachable() {
    override fun dispatch(event: Event) {
        sender?.let {
            val serializedEvent = json.encodeToString(eventSerializer, event)
            it.send(serializedEvent)
        }
    }
}

/**
 * An [EventDispatcher] that buffers events until a sender is attached.
 */
internal class BufferedEventDispatcher<Event> internal constructor(
    val bufferSize: Int,
    private val eventSerializer: KSerializer<Event>,
    private val json: Json,
) : EventDispatcher<Event>, DefaultSenderAttachable() {
    private val queue = ArrayDeque<Event>()

    override fun dispatch(event: Event) {
        sender?.let {
            val serializedEvent = json.encodeToString(eventSerializer, event)
            it.send(serializedEvent)
        } ?: run {
            queue.add(event)
        }
    }

    override fun attachSender(sender: JetWhaleEventSender) {
        super.attachSender(sender)

        // Flush the queue
        val iterator = queue.iterator()
        while (iterator.hasNext()) {
            val event = iterator.next()
            val serializedEvent = json.encodeToString(eventSerializer, event)
            sender.send(serializedEvent)
            queue.remove(event)
        }
    }
}

/**
 * Creates a [DropIfDisconnectedDispatcher] with the specified [eventSerializer].
 *
 * @param eventSerializer The serializer for the event type.
 */
@Suppress("FunctionName")
@OptIn(InternalJetWhaleApi::class)
public fun <Event> DropIfDisconnectedDispatcher(
    eventSerializer: KSerializer<Event>,
): EventDispatcher<Event> {
    return DropIfDisconnectedDispatcher(
        eventSerializer = eventSerializer,
        json = JetWhaleJson
    )
}

/**
 * Creates a [BufferedEventDispatcher] with the specified [eventSerializer] and [bufferSize].
 *
 * @param eventSerializer The serializer for the event type.
 * @param bufferSize The maximum number of events to buffer.
 */
@Suppress("FunctionName")
@OptIn(InternalJetWhaleApi::class)
public fun <Event> BufferedEventDispatcher(
    eventSerializer: KSerializer<Event>,
    bufferSize: Int,
): EventDispatcher<Event> {
    return BufferedEventDispatcher(
        eventSerializer = eventSerializer,
        bufferSize = bufferSize,
        json = JetWhaleJson,
    )
}
