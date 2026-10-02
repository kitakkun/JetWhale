package com.kitakkun.jetwhale.plugins.semantics.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpContent
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeCaptureOptions
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeSnapshot
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleRequestException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalJetWhaleApi::class)
class ComposeMcpCommandsTest {
    private val unanswered: suspend (NodeTreeCaptureOptions) -> NodeTreeSnapshot = { throw JetWhaleRequestException("timed out") }

    @Test
    fun `a tool the app does not answer is a failed call`() {
        val commands = listOf(
            GetNodeTreeCommand(capture = unanswered) to buildJsonObject { },
            FindNodesCommand(capture = unanswered) to buildJsonObject { },
            NodeAtCommand(capture = unanswered) to buildJsonObject {
                put("x", 1)
                put("y", 1)
            },
            PerformNodeActionCommand(lastSnapshot = { null }, capture = unanswered, perform = { error("never reached") }) to buildJsonObject {
                put("nodeId", 1)
                put("action", "Click")
            },
        )

        val failures = commands.map { (command, arguments) -> command.failureOf(arguments) }

        assertEquals(List(commands.size) { "the app did not answer: timed out" }, failures)
    }
}

@OptIn(ExperimentalJetWhaleApi::class)
private fun JetWhaleMcpCommand.failureOf(arguments: JsonObject): String? = runBlocking {
    val result = run(JetWhaleMcpArguments(arguments))
    (result.content.single() as JetWhaleMcpContent.Text).text.takeIf { result.isError }
}
