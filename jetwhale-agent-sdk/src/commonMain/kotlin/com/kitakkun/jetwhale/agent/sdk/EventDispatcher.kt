package com.kitakkun.jetwhale.agent.sdk

import com.kitakkun.jetwhale.protocol.InternalJetWhaleApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

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
public interface JetWhaleEventSender {
    public fun <T> send(serializer: KSerializer<T>, event: T)
}

/**
 * An [EventDispatcher] that drops events if there is no connected sender.
 */
internal class DropIfDisconnectedDispatcherInternal<Event> internal constructor(
    private val eventSerializer: KSerializer<Event>,
) : EventDispatcher<Event>, DefaultSenderAttachable() {
    override fun dispatch(event: Event) {
        sender?.send(eventSerializer, event)
    }
}

/**
 * An [EventDispatcher] that buffers events until a sender is attached.
 */
internal class BufferedEventDispatcherInternal<Event> internal constructor(
    val bufferSize: Int,
    private val eventSerializer: KSerializer<Event>,
) : EventDispatcher<Event>, DefaultSenderAttachable() {
    private val queue = ArrayDeque<Event>()

    override fun dispatch(event: Event) {
        sender?.send(eventSerializer, event) ?: run {
            if (queue.size >= bufferSize) {
                queue.removeFirst()
            }
            queue.addLast(event)
        }
    }

    override fun attachSender(sender: JetWhaleEventSender) {
        super.attachSender(sender)

        // Flush the queue
        val iterator = queue.iterator()
        while (iterator.hasNext()) {
            val event = iterator.next()
            sender.send(eventSerializer, event)
            queue.remove(event)
        }
    }
}

/**
 * Creates a [DropIfDisconnectedDispatcher] for the specified [Event] type.
 */
@Suppress("FunctionName")
@OptIn(InternalJetWhaleApi::class)
public inline fun <reified Event> DropIfDisconnectedDispatcher(): EventDispatcher<Event> {
    return DropIfDisconnectedDispatcher(serializer())
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
    return DropIfDisconnectedDispatcherInternal(eventSerializer = eventSerializer)
}

/**
 * Creates a [BufferedEventDispatcher] for the specified [Event] type and [bufferSize].
 *
 * @param bufferSize The maximum number of events to buffer.
 */
@Suppress("FunctionName")
@OptIn(InternalJetWhaleApi::class)
public inline fun <reified Event> BufferedEventDispatcher(
    bufferSize: Int,
): EventDispatcher<Event> {
    return BufferedEventDispatcher(
        eventSerializer = serializer(),
        bufferSize = bufferSize,
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
    return BufferedEventDispatcherInternal(
        eventSerializer = eventSerializer,
        bufferSize = bufferSize,
    )
}
