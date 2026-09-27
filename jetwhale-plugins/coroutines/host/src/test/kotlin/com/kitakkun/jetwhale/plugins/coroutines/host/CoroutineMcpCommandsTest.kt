package com.kitakkun.jetwhale.plugins.coroutines.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.coroutines.protocol.ClearedLongRuns
import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineDetail
import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineDump
import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineState
import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineTree
import com.kitakkun.jetwhale.plugins.coroutines.protocol.DispatcherStatsReport
import com.kitakkun.jetwhale.plugins.coroutines.protocol.TrackedFlowReport
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalJetWhaleApi::class)
class CoroutineMcpCommandsTest {
    private class FakeClient(private val tree: CoroutineTree) : CoroutineInspectorClient {
        override suspend fun coroutineTree(): CoroutineTree = tree

        override suspend fun dispatcherStats(): DispatcherStatsReport = DispatcherStatsReport(dispatchers = emptyList(), untracked = emptyList(), capturedAtEpochMillis = 0)

        override suspend fun trackedFlows(): TrackedFlowReport = TrackedFlowReport(flows = emptyList(), capturedAtEpochMillis = 0)

        override suspend fun dump(): CoroutineDump = CoroutineDump(text = "", unavailableReason = "no probes")

        override suspend fun clearLongRuns(): ClearedLongRuns = ClearedLongRuns(cleared = 0)

        override suspend fun coroutineDetail(id: String): CoroutineDetail = CoroutineDetail(
            id = id,
            found = true,
            debugState = "SUSPENDED",
            suspensionStack = listOf("com.example.Poller.poll(Poller.kt:12)"),
            creationStack = emptyList(),
            stackUnavailableReason = null,
        )
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
    fun `getCoroutineTree refuses an age that is negative or would overflow in milliseconds`() {
        listOf(-1L, Long.MAX_VALUE).forEach { seconds ->
            assertFailsWith<JetWhaleMcpArgumentException> {
                GetCoroutineTreeCommand(FakeClient(tree)).run(buildJsonObject { put("minObservedSeconds", seconds) })
            }
        }
    }

    @Test
    fun `getCoroutineTree with named only counts the matches next to the total`() {
        val unnamed = tree.copy(roots = listOf(tree.roots.single().copy(children = tree.roots.single().children + node("anon", null, CoroutineState.Active, observed = 0))), coroutineCount = 4)

        val result = GetCoroutineTreeCommand(FakeClient(unnamed)).run(buildJsonObject { put("namedOnly", true) })

        assertEquals(3, result.getValue("matchingCount").jsonPrimitive.int)
        assertEquals(4, result.getValue("coroutineCount").jsonPrimitive.int)
    }

    @Test
    fun `getCoroutineTree explains an app that registered no scopes`() {
        val result = GetCoroutineTreeCommand(FakeClient(tree.copy(roots = emptyList(), coroutineCount = 0))).run(buildJsonObject { })

        assertTrue("note" in result)
    }

    @Test
    fun `getCoroutineDetail combines where the coroutine lives with the stack the app reports`() {
        val result = GetCoroutineDetailCommand(FakeClient(tree)).run(buildJsonObject { put("id", "stuck") })

        assertEquals(listOf("Application"), result.getValue("path").jsonArray.map { it.jsonPrimitive.content })
        assertEquals("SUSPENDED", result.getValue("debugState").jsonPrimitive.content)
        assertEquals(listOf("com.example.Poller.poll(Poller.kt:12)"), result.getValue("suspensionStack").jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun `getCoroutineDetail counts what runs below a coroutine by state`() {
        val result = GetCoroutineDetailCommand(FakeClient(tree)).run(buildJsonObject { put("id", "root") })

        assertEquals(2, result.getValue("descendantsByState").jsonObject.getValue("Active").jsonPrimitive.int)
    }

    @Test
    fun `getCoroutineDetail reports an id that is no longer in the tree as gone`() {
        val result = GetCoroutineDetailCommand(FakeClient(tree)).run(buildJsonObject { put("id", "c404") })

        assertEquals(false, result.getValue("found").jsonPrimitive.boolean)
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
