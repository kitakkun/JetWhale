package com.kitakkun.jetwhale.protocol.agent

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

interface JetWhaleAgentPluginProtocol<Event, Method, MethodResult> {
    fun decodeMethod(value: String): Method
    fun encodeMethodResult(value: MethodResult): String
    fun encodeEvent(value: Event): String
}

class KotlinxSerializationJetWhaleAgentPluginProtocol<Event, Method, MethodResult>(
    private val json: Json,
    private val eventSerializer: KSerializer<Event>,
    private val methodSerializer: KSerializer<Method>,
    private val methodResultSerializer: KSerializer<MethodResult>,
) : JetWhaleAgentPluginProtocol<Event, Method, MethodResult> {
    override fun decodeMethod(value: String): Method {
        return json.decodeFromString(methodSerializer, value)
    }

    override fun encodeMethodResult(value: MethodResult): String {
        return json.encodeToString(methodResultSerializer, value)
    }

    override fun encodeEvent(value: Event): String {
        return json.encodeToString(eventSerializer, value)
    }
}

inline fun <reified Event : Any, reified Method : Any, reified MethodResult : Any> kotlinxSerializationJetWhaleAgentPluginProtocol(json: Json): JetWhaleAgentPluginProtocol<Event, Method, MethodResult> {
    return KotlinxSerializationJetWhaleAgentPluginProtocol(
        json = json,
        eventSerializer = serializer(),
        methodSerializer = serializer(),
        methodResultSerializer = serializer(),
    )
}
