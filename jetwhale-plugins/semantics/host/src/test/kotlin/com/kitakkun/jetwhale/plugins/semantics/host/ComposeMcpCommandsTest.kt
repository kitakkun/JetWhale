package com.kitakkun.jetwhale.plugins.semantics.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpContent
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeActionResult
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
    private val unansweredCapture: suspend (NodeTreeCaptureOptions) -> NodeTreeSnapshot = { throw JetWhaleRequestException("timed out") }

    @Test
    fun `a tool the app does not answer is a failed call`() {
        val commandsWithArguments = listOf(
            GetNodeTreeCommand(capture = unansweredCapture) to buildJsonObject { },
            FindNodesCommand(capture = unansweredCapture) to buildJsonObject { },
            NodeAtCommand(capture = unansweredCapture) to buildJsonObject {
                put("x", 1)
                put("y", 1)
            },
            PerformNodeActionCommand(lastSnapshot = { null }, capture = unansweredCapture, perform = { error("never reached") }) to buildJsonObject {
                put("nodeId", 1)
                put("action", "Click")
            },
        )

        val failureMessages = commandsWithArguments.map { (command, arguments) -> command.failureMessageOf(arguments) }

        assertEquals(List(commandsWithArguments.size) { "the app did not answer: timed out" }, failureMessages)
    }

    @Test
    fun `an action the app does not perform is a failed call with its reason`() {
        val command = PerformNodeActionCommand(
            lastSnapshot = { null },
            capture = unansweredCapture,
            perform = { NodeActionResult(performed = false, message = "node 7 is disabled") },
        )

        val failureMessage = command.failureMessageOf(
            buildJsonObject {
                put("nodeId", 7)
                put("action", "Click")
                put("rootId", "window-1")
            },
        )

        assertEquals("Click was not performed on node 7 in window-1: node 7 is disabled", failureMessage)
    }
}

@OptIn(ExperimentalJetWhaleApi::class)
private fun JetWhaleMcpCommand.failureMessageOf(arguments: JsonObject): String? = runBlocking {
    val result = run(JetWhaleMcpArguments(arguments))
    (result.content.single() as JetWhaleMcpContent.Text).text.takeIf { result.isError }
}
