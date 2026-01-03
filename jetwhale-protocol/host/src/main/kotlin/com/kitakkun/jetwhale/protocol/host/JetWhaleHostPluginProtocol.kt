package com.kitakkun.jetwhale.protocol.host

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

interface JetWhaleHostPluginProtocol<Event, Method, MethodResult> {
    fun encodeMethod(value: Method): String
    fun decodeMethodResult(value: String): MethodResult
    fun decodeEvent(value: String): Event
}

class KotlinxSerializationJetWhaleHostPluginProtocol<Event, Method, MethodResult>(
    private val json: Json,
    private val eventSerializer: KSerializer<Event>,
    private val methodSerializer: KSerializer<Method>,
    private val methodResultSerializer: KSerializer<MethodResult>,
) : JetWhaleHostPluginProtocol<Event, Method, MethodResult> {
    override fun encodeMethod(value: Method): String {
        return json.encodeToString(methodSerializer, value)
    }

    override fun decodeMethodResult(value: String): MethodResult {
        return json.decodeFromString(methodResultSerializer, value)
    }

    override fun decodeEvent(value: String): Event {
        return json.decodeFromString(eventSerializer, value)
    }
}

inline fun <reified Event : Any, reified Method : Any, reified MethodResult : Any> kotlinxSerializationJetWhaleHostPluginProtocol(json: Json): JetWhaleHostPluginProtocol<Event, Method, MethodResult> {
    return KotlinxSerializationJetWhaleHostPluginProtocol(
        json = json,
        eventSerializer = serializer(),
        methodSerializer = serializer(),
        methodResultSerializer = serializer(),
    )
}
