package com.kitakkun.jetwhale.host.sdk

import kotlinx.coroutines.CoroutineScope

/**
 * Context for sending fire-and-forget debugger events from JetWhale Host to the plugin.
 * @param DebuggerEvent The type representing an event sent from the debugger to the debuggee
 */
public interface JetWhaleDebugOperationContext<DebuggerEvent> {
    /**
     * The CoroutineScope for executing operations
     */
    public val coroutineScope: CoroutineScope

    /**
     * Sends a fire-and-forget event to the debuggee plugin.
     *
     * @param event The event to send
     */
    public suspend fun send(event: DebuggerEvent)
}

/**
 * Context for dispatching request-response method calls from JetWhale Host to the plugin.
 * @param Method The type representing a method call
 * @param MethodResult The type representing the result of a method call
 */
public interface JetWhaleMethodDebugOperationContext<Method, MethodResult> {
    /**
     * The CoroutineScope for executing method calls
     */
    public val coroutineScope: CoroutineScope

    /**
     * Dispatches a method call to the plugin and returns the result.
     * Do not call this method from the UI thread.
     *
     * @param method The method to dispatch
     * @return The result of the method call, or null if there is no result
     */
    public suspend fun <MR : MethodResult> dispatch(method: Method): MR?
}

/**
 * Context for dispatching raw debug operations from JetWhale Host to the plugin.
 * This context uses raw String for operations, without any encoding or decoding.
 * It is used internally by JetWhaleHostPlugin and JetWhaleMethodHostPlugin.
 */
public interface JetWhaleRawDebugOperationContext {
    /**
     * The CoroutineScope for executing operations
     */
    public val coroutineScope: CoroutineScope

    /**
     * Dispatches a raw method call to the plugin and returns the raw result.
     * Do not call this method from the UI thread.
     *
     * @param method The raw method to dispatch
     * @return The raw result of the method call, or null if there is no result
     */
    public suspend fun dispatch(method: String): String?

    /**
     * Sends a raw fire-and-forget event to the plugin.
     *
     * @param encodedEvent The raw encoded event to send
     */
    public suspend fun send(encodedEvent: String)
}
