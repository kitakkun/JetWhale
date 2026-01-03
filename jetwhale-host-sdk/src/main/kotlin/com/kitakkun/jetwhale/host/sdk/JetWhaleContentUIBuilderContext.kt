package com.kitakkun.jetwhale.host.sdk

import kotlinx.serialization.KSerializer

public interface JetWhaleContentUIBuilderContext {
    public suspend fun <Method, MethodResult> dispatch(
        methodSerializer: KSerializer<Method>,
        methodResultSerializer: KSerializer<MethodResult>,
        value: Method
    ): MethodResult?
}
