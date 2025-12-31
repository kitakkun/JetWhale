package com.kitakkun.jetwhale.debugger.host.sdk

import kotlinx.serialization.KSerializer

public interface JetWhaleContentUIBuilderContext {
    public suspend fun <Method, MethodResult> dispatch(
        methodSerializer: KSerializer<Method>,
        methodResultSerializer: KSerializer<MethodResult>,
        value: Method
    ): MethodResult?
}
