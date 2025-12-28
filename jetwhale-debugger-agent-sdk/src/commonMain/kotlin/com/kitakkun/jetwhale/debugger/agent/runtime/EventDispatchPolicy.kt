package com.kitakkun.jetwhale.debugger.agent.runtime

/**
 * Policy for dispatching events to the debugger.
 */
public sealed interface EventDispatchPolicy {
    /**
     * Drop events if there is no connected sender.
     */
    public data object DropIfDisconnected : EventDispatchPolicy

    /**
     * Buffer events until a sender is attached.
     *
     * @param bufferSize The maximum number of events to buffer.
     */
    public data class QueueBuffered(val bufferSize: Int) : EventDispatchPolicy
}
