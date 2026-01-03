package com.kitakkun.jetwhale.host.sdk

import kotlinx.serialization.KSerializer

public interface JetWhaleEventReceiverContext {
    public fun <T> getDeserializedPayload(serializer: KSerializer<T>): T
}
