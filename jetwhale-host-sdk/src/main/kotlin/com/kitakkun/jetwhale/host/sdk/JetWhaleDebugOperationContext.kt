package com.kitakkun.jetwhale.host.sdk

import kotlinx.coroutines.CoroutineScope

/**
 * Context for dispatching debug operations from JetWhale Host to the plugin.
 * @param Method The type representing a method call
 * @param MethodResult The type representing the result of a method call
 */
public interface JetWhaleDebugOperationContext<Method, MethodResult> {
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
 * This context uses raw String for method calls and results, without any encoding or decoding.
 * It is used internally by JetWhaleHostPlugin to adapt to the typed JetWhaleDebugOperationContext.
 */
public interface JetWhaleRawDebugOperationContext {
    /**
     * The CoroutineScope for executing method calls
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
}
