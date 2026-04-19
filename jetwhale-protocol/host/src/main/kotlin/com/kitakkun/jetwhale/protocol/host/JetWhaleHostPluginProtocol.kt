package com.kitakkun.jetwhale.protocol.host

import com.kitakkun.jetwhale.annotations.InternalJetWhaleApi
import com.kitakkun.jetwhale.protocol.serialization.JetWhaleJson
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

public interface JetWhaleHostPluginProtocol<DebuggeeEvent, DebuggerEvent> {
    public fun decodeDebuggeeEvent(value: String): DebuggeeEvent
    public fun encodeDebuggerEvent(value: DebuggerEvent): String
}

@PublishedApi
internal class KotlinxSerializationJetWhaleHostPluginProtocol<DebuggeeEvent, DebuggerEvent>(
    private val json: Json,
    private val debuggeeEventSerializer: KSerializer<DebuggeeEvent>,
    private val debuggerEventSerializer: KSerializer<DebuggerEvent>,
) : JetWhaleHostPluginProtocol<DebuggeeEvent, DebuggerEvent> {
    override fun decodeDebuggeeEvent(value: String): DebuggeeEvent = json.decodeFromString(debuggeeEventSerializer, value)

    override fun encodeDebuggerEvent(value: DebuggerEvent): String = json.encodeToString(debuggerEventSerializer, value)
}

@OptIn(InternalJetWhaleApi::class)
public inline fun <reified DebuggeeEvent : Any, reified DebuggerEvent : Any> kotlinxSerializationJetWhaleHostPluginProtocol(
    json: Json = JetWhaleJson,
): JetWhaleHostPluginProtocol<DebuggeeEvent, DebuggerEvent> = KotlinxSerializationJetWhaleHostPluginProtocol(
    json = json,
    debuggeeEventSerializer = serializer(),
    debuggerEventSerializer = serializer(),
)
