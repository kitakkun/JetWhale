package com.kitakkun.jetwhale.plugins.coroutines.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.coroutines.protocol.ClearedLongRuns
import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineDump
import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineState
import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineTree
import com.kitakkun.jetwhale.plugins.coroutines.protocol.DispatcherStatsReport
import com.kitakkun.jetwhale.plugins.coroutines.protocol.TrackedFlowReport
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalJetWhaleApi::class)
class CoroutineMcpCommandsTest {
    private class FakeClient(private val tree: CoroutineTree) : CoroutineInspectorClient {
        override suspend fun coroutineTree(): CoroutineTree = tree

        override suspend fun dispatcherStats(): DispatcherStatsReport = DispatcherStatsReport(dispatchers = emptyList(), capturedAtEpochMillis = 0)

        override suspend fun trackedFlows(): TrackedFlowReport = TrackedFlowReport(flows = emptyList(), capturedAtEpochMillis = 0)

        override suspend fun dump(): CoroutineDump = CoroutineDump(text = "", unavailableReason = "no probes")

        override suspend fun clearLongRuns(): ClearedLongRuns = ClearedLongRuns(cleared = 0)
    }

    private val tree = CoroutineTree(
        roots = listOf(
            node("root", "Application", CoroutineState.Active, observed = 100_000, node("stuck", "stuck", CoroutineState.Active, observed = 90_000), node("fresh", "fresh", CoroutineState.Active, observed = 100)),
        ),
        coroutineCount = 3,
        truncated = false,
        capturedAtEpochMillis = 0,
    )

    @Test
    fun `getCoroutineTree filters by how long a coroutine has been seen`() {
        val result = GetCoroutineTreeCommand(FakeClient(tree)).run(buildJsonObject { put("minObservedSeconds", 60) })

        val children = result.getValue("roots").jsonArray.single().jsonObject.getValue("children").jsonArray
        assertEquals(listOf("stuck"), children.map { it.jsonObject.getValue("name").jsonPrimitive.content })
    }

    @Test
    fun `getCoroutineTree explains an app that registered no scopes`() {
        val result = GetCoroutineTreeCommand(FakeClient(tree.copy(roots = emptyList(), coroutineCount = 0))).run(buildJsonObject { })

        assertTrue("note" in result)
    }

    @Test
    fun `dumpCoroutines passes on why the app cannot dump`() {
        val result = DumpCoroutinesCommand(FakeClient(tree)).run(buildJsonObject { })

        assertEquals("no probes", result.getValue("unavailableReason").jsonPrimitive.content)
    }
}

@OptIn(ExperimentalJetWhaleApi::class)
private fun JetWhaleMcpCommand.run(arguments: JsonObject): JsonObject = runBlocking {
    Json.parseToJsonElement(execute(JetWhaleMcpArguments(arguments))).jsonObject
}
