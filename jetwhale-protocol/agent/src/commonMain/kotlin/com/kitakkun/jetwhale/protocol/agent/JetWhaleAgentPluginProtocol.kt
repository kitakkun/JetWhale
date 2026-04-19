package com.kitakkun.jetwhale.protocol.agent

import com.kitakkun.jetwhale.annotations.InternalJetWhaleApi
import com.kitakkun.jetwhale.protocol.serialization.JetWhaleJson
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

public interface JetWhaleAgentPluginProtocol<DebuggeeEvent, DebuggerEvent> {
    public fun encodeDebuggeeEvent(value: DebuggeeEvent): String
    public fun decodeDebuggerEvent(value: String): DebuggerEvent
}

@PublishedApi
internal class KotlinxSerializationJetWhaleAgentPluginProtocol<DebuggeeEvent, DebuggerEvent>(
    private val json: Json,
    private val debuggeeEventSerializer: KSerializer<DebuggeeEvent>,
    private val debuggerEventSerializer: KSerializer<DebuggerEvent>,
) : JetWhaleAgentPluginProtocol<DebuggeeEvent, DebuggerEvent> {
    override fun encodeDebuggeeEvent(value: DebuggeeEvent): String = json.encodeToString(debuggeeEventSerializer, value)

    override fun decodeDebuggerEvent(value: String): DebuggerEvent = json.decodeFromString(debuggerEventSerializer, value)
}

@OptIn(InternalJetWhaleApi::class)
public inline fun <reified DebuggeeEvent : Any, reified DebuggerEvent : Any> kotlinxSerializationJetWhaleAgentPluginProtocol(
    json: Json = JetWhaleJson,
): JetWhaleAgentPluginProtocol<DebuggeeEvent, DebuggerEvent> = KotlinxSerializationJetWhaleAgentPluginProtocol(
    json = json,
    debuggeeEventSerializer = serializer(),
    debuggerEventSerializer = serializer(),
)
