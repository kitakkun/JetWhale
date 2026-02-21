package com.kitakkun.jetwhale.agent.sdk

import com.kitakkun.jetwhale.annotations.InternalJetWhaleApi

/**
 * An abstract class representing a raw agent plugin for JetWhale.
 * This plugin handles raw method messages and event messages as strings.
 * It manages a message sender and a message queue for communication with the debugger.
 *
 * Implementations should provide logic for handling typed method messages.
 */
@InternalJetWhaleApi
public abstract class JetWhaleRawAgentPlugin {
    /**
     * unique id to distinguish plugins.
     * For example, "com.kitakkun.jetwhale.debugger.agent.plugin.sample"
     */
    public abstract val pluginId: String

    /**
     * Version of this plugin.
     * For example, "1.0.0"
     */
    public abstract val pluginVersion: String

    /**
     * The message sender used to send messages to the debugger.
     * [JetWhaleMessagingService] will attach/detach this sender.
     */
    private var sender: JetWhaleMessageSender? = null

    /**
     * The message queue for storing event messages when no sender is attached.
     */
    private val queue: JetWhaleAgentMessageQueue = JetWhaleAgentMessageQueue(bufferSize = queueBufferSize())

    /**
     * The size of the message queue buffer.
     * When the buffer is full, the oldest messages will be discarded.
     */
    protected open fun queueBufferSize(): Int = 100

    /**
     * Handles a raw method message received from the debugger.
     */
    @InternalJetWhaleApi
    public abstract suspend fun onRawMethod(message: String): String?

    /**
     * Enqueues a raw event message to be sent to the debugger.
     * If a sender is attached, the message is sent immediately.
     */
    @InternalJetWhaleApi
    public fun enqueueRawEvent(message: String) {
        if (sender != null) {
            sender?.send(message)
            return
        }
        queue.enqueue(message)
    }

    /**
     * Activates the plugin by attaching a message sender.
     * Flushes any queued messages upon attachment.
     */
    @InternalJetWhaleApi
    public fun activate(sender: JetWhaleMessageSender) {
        this.sender = sender
        flushQueue()
    }

    /**
     * Detaches the current message sender.
     */
    @InternalJetWhaleApi
    public fun deactivate() {
        this.sender = null
    }

    /**
     * Flushes the message queue by sending all queued messages using the attached sender.
     */
    private fun flushQueue() {
        while (true) {
            val message = queue.dequeue() ?: break
            sender?.send(message)
        }
    }
}

/**
 * A simple message queue for storing messages with a fixed buffer size.
 * When the buffer is full, the oldest messages are discarded.
 */
private class JetWhaleAgentMessageQueue(
    private val bufferSize: Int,
) {
    private val queue: ArrayDeque<String> = ArrayDeque()

    /**
     * Enqueues a message to the queue.
     * If the queue is full, the oldest message is removed.
     */
    fun enqueue(message: String) {
        if (queue.size >= bufferSize) queue.removeFirst()
        queue.addLast(message)
    }

    /**
     * Dequeues a message from the queue.
     * Returns null if the queue is empty.
     */
    fun dequeue(): String? {
        return if (queue.isNotEmpty()) {
            queue.removeFirst()
        } else {
            null
        }
    }
}

/**
 * A functional interface representing a message sender to send messages to the debugger.
 */
public fun interface JetWhaleMessageSender {
    public fun send(message: String)
}
