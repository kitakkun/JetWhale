package com.kitakkun.jetwhale.plugins.example.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@OptIn(ExperimentalJetWhaleApi::class)
internal class SendPingCommand(
    private val requestPing: suspend () -> Unit,
) : JetWhaleMcpCommand() {
    override val name = "com.kitakkun.jetwhale.example.sendPing"
    override val description = "Sends a Ping request to the debuggee and returns whether a Pong reply was received."

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val pongReceived = try {
            requestPing()
            true
        } catch (_: JetWhaleMessagingException) {
            false
        }
        return buildJsonObject {
            put("pongReceived", pongReceived)
        }.toString()
    }
}
