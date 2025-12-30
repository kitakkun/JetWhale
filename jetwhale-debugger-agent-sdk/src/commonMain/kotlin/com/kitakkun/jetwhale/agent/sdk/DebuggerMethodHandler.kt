package com.kitakkun.jetwhale.agent.sdk

import com.kitakkun.jetwhale.debugger.protocol.InternalJetWhaleApi
import com.kitakkun.jetwhale.debugger.protocol.serialization.JetWhaleJson
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
 * Handles method calls from the debugger.
 *
 * Do not implement this interface directly; use [DebuggerMethodHandler] builder function instead.
 */
@InternalJetWhaleApi
public interface DebuggerMethodHandler {
    /**
     * @param methodPayload The serialized method payload received from the debugger.
     * Note that it must be deserialized before use.
     */
    @InternalJetWhaleApi
    public suspend fun handle(methodPayload: String): String
}

/**
 * Internal base implementation of [DebuggerMethodHandler].
 *
 * @param Method The type of the method to be handled.
 * @param MethodResult The type of the result returned by the method handler.
 */
@OptIn(InternalJetWhaleApi::class)
public class DebuggerMethodHandlerImpl<Method, MethodResult>(
    private val json: Json,
    private val methodDeserializer: DeserializationStrategy<Method>,
    private val methodResultSerializer: SerializationStrategy<MethodResult>,
    private val handler: suspend (Method) -> MethodResult,
) : DebuggerMethodHandler {
    override suspend fun handle(methodPayload: String): String {
        val deserializedMethod = json.decodeFromString(methodDeserializer, methodPayload)
        val methodResult = handler(deserializedMethod)
        return json.encodeToString(methodResultSerializer, methodResult)
    }
}

/**
 * Builds a [DebuggerMethodHandler] for the specified [Method] and [MethodResponse] types.
 *
 * @param Method The type of the method to be handled.
 * @param MethodResponse The type of the result returned by the method handler.
 * @param onReceiveEvent The suspend function to handle the received method and return a response.
 * @return A [DebuggerMethodHandler] that processes the specified method type and returns the specified response type.
 */
@OptIn(InternalJetWhaleApi::class)
public inline fun <reified Method, reified MethodResponse> DebuggerMethodHandler(
    crossinline onReceiveEvent: suspend (Method) -> MethodResponse,
): DebuggerMethodHandler {
    return DebuggerMethodHandlerImpl<Method, MethodResponse>(
        json = JetWhaleJson,
        methodDeserializer = serializer(),
        methodResultSerializer = serializer(),
        handler = { onReceiveEvent(it) },
    )
}
