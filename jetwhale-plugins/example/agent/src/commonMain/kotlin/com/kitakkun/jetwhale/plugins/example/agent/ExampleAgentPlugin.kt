package com.kitakkun.jetwhale.plugins.example.agent

import com.kitakkun.jetwhale.agent.sdk.JetWhaleAgentPlugin
import com.kitakkun.jetwhale.plugins.example.protocol.ExampleEvent
import com.kitakkun.jetwhale.plugins.example.protocol.ExampleMethod
import com.kitakkun.jetwhale.plugins.example.protocol.ExampleMethodResult
import com.kitakkun.jetwhale.protocol.agent.JetWhaleAgentPluginProtocol
import com.kitakkun.jetwhale.protocol.agent.kotlinxSerializationJetWhaleAgentPluginProtocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * An example JetWhale agent plugin that handles ExampleMethod and ExampleEvent.
 * It logs received methods and events to a StateFlow of event logs.
 */
class ExampleAgentPlugin : JetWhaleAgentPlugin<ExampleEvent, ExampleMethod, ExampleMethodResult>() {
    override val pluginId: String get() = "com.kitakkun.jetwhale.example"
    override val pluginVersion: String get() = "1.0.0"
    override val protocol: JetWhaleAgentPluginProtocol<ExampleEvent, ExampleMethod, ExampleMethodResult> = kotlinxSerializationJetWhaleAgentPluginProtocol()

    val mutableEventLogsFlow: MutableStateFlow<List<String>> = MutableStateFlow(emptyList())
    val eventLogsFlow: StateFlow<List<String>> = mutableEventLogsFlow

    override suspend fun onReceiveMethod(method: ExampleMethod): ExampleMethodResult {
        when (method) {
            is ExampleMethod.Ping -> {
                val result = ExampleMethodResult.Pong
                mutableEventLogsFlow.update {
                    it.toMutableList().apply {
                        add("Method: $method")
                        add("MethodResult: $result")
                    }
                }
                return result
            }
        }
    }

    override fun onEnqueueEvent(event: ExampleEvent) {
        mutableEventLogsFlow.update {
            it.toMutableList().apply {
                add("Event: $event")
            }
        }
    }
}
