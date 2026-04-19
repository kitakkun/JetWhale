package com.kitakkun.jetwhale.protocol.host

import com.kitakkun.jetwhale.annotations.InternalJetWhaleApi
import com.kitakkun.jetwhale.protocol.serialization.JetWhaleJson
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

public interface JetWhaleMethodHostPluginProtocol<Event, Method, MethodResult> {
    public fun encodeMethod(value: Method): String
    public fun decodeMethodResult(value: String): MethodResult
    public fun decodeEvent(value: String): Event
}

@PublishedApi
internal class KotlinxSerializationJetWhaleMethodHostPluginProtocol<Event, Method, MethodResult>(
    private val json: Json,
    private val eventSerializer: KSerializer<Event>,
    private val methodSerializer: KSerializer<Method>,
    private val methodResultSerializer: KSerializer<MethodResult>,
) : JetWhaleMethodHostPluginProtocol<Event, Method, MethodResult> {
    override fun encodeMethod(value: Method): String = json.encodeToString(methodSerializer, value)

    override fun decodeMethodResult(value: String): MethodResult = json.decodeFromString(methodResultSerializer, value)

    override fun decodeEvent(value: String): Event = json.decodeFromString(eventSerializer, value)
}

@OptIn(InternalJetWhaleApi::class)
public inline fun <reified Event : Any, reified Method : Any, reified MethodResult : Any> kotlinxSerializationJetWhaleMethodHostPluginProtocol(
    json: Json = JetWhaleJson,
): JetWhaleMethodHostPluginProtocol<Event, Method, MethodResult> = KotlinxSerializationJetWhaleMethodHostPluginProtocol(
    json = json,
    eventSerializer = serializer(),
    methodSerializer = serializer(),
    methodResultSerializer = serializer(),
)
