package com.kitakkun.jetwhale.host.sdk

import kotlinx.coroutines.CoroutineScope

/**
 * @suppress
 */
@Deprecated("Use JetWhaleConnection instead.")
public interface JetWhaleDebugOperationContext<Method, MethodResult> {
    public val coroutineScope: CoroutineScope
    public suspend fun <MR : MethodResult> dispatch(method: Method): MR?
}

/**
 * @suppress
 */
@Deprecated("Use JetWhaleRawConnection instead.")
public interface JetWhaleRawDebugOperationContext {
    public val coroutineScope: CoroutineScope
    public suspend fun dispatch(method: String): String?
}
