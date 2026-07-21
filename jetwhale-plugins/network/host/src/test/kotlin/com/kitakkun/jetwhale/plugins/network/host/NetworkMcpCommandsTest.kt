package com.kitakkun.jetwhale.plugins.network.host

import com.kitakkun.jetwhale.host.sdk.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpRequest
import com.kitakkun.jetwhale.plugins.network.protocol.MockMatchType
import com.kitakkun.jetwhale.plugins.network.protocol.MockMatcher
import com.kitakkun.jetwhale.plugins.network.protocol.MockResponseSpec
import com.kitakkun.jetwhale.plugins.network.protocol.MockRule
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalJetWhaleApi::class)
class NetworkMcpCommandsTest {
    private fun tx(txId: String, timestampMs: Long, url: String = "https://api.example.com/$txId", method: String = "GET") = HttpTransaction(
        request = CapturedHttpRequest(txId = txId, method = method, url = url, timestampMs = timestampMs),
    )

    private val transactions = listOf(tx("a", 100), tx("b", 200), tx("c", 300), tx("d", 400))

    private fun listCommand(data: List<HttpTransaction> = transactions) = ListTransactionsCommand(transactions = { data }, redactForMcp = { it })

    private fun execute(command: JetWhaleMcpCommand, vararg args: Pair<String, String>): String = executeJson(command, *args.map { (key, value) -> key to JsonPrimitive(value) }.toTypedArray())

    private fun executeJson(command: JetWhaleMcpCommand, vararg args: Pair<String, JsonElement>): String = runBlocking { command.execute(JetWhaleMcpArguments(JsonObject(args.toMap()))) }

    private fun txIdsOf(result: String): List<String> = Json.parseToJsonElement(result).jsonObject
        .getValue("transactions").jsonArray
        .map { it.jsonObject.getValue("txId").jsonPrimitive.content }

    private fun nextCursorOf(result: String): String? = Json.parseToJsonElement(result).jsonObject["nextCursor"]?.jsonPrimitive?.content

    @Test
    fun `listTransactions without arguments returns all oldest first`() {
        val result = execute(listCommand())
        assertEquals(listOf("a", "b", "c", "d"), txIdsOf(result))
        assertNull(nextCursorOf(result))
    }

    @Test
    fun `limit without cursor keeps latest-N meaning`() {
        val result = execute(listCommand(), "limit" to "2")
        assertEquals(listOf("c", "d"), txIdsOf(result))
        assertNull(nextCursorOf(result))
    }

    @Test
    fun `afterTxId pages forward and returns nextCursor until exhausted`() {
        val firstPage = execute(listCommand(), "afterTxId" to "a", "limit" to "2")
        assertEquals(listOf("b", "c"), txIdsOf(firstPage))
        assertEquals("c", nextCursorOf(firstPage))

        val secondPage = execute(listCommand(), "afterTxId" to "c", "limit" to "2")
        assertEquals(listOf("d"), txIdsOf(secondPage))
        assertNull(nextCursorOf(secondPage), "The final page must not return a cursor")
    }

    @Test
    fun `timestamp range filters on the request timestamp`() {
        val result = execute(listCommand(), "sinceTimestampMs" to "200", "untilTimestampMs" to "300")
        assertEquals(listOf("b", "c"), txIdsOf(result))
    }

    @Test
    fun `cursor resolves against the unfiltered list so filters can change between pages`() {
        val result = execute(listCommand(), "afterTxId" to "b", "urlContains" to "/d")
        assertEquals(listOf("d"), txIdsOf(result))
    }

    @Test
    fun `unknown afterTxId throws a caller-facing argument exception`() {
        val exception = assertFailsWith<JetWhaleMcpArgumentException> {
            execute(listCommand(), "afterTxId" to "ghost")
        }
        assertTrue("ghost" in exception.message!!, "The message should name the unknown cursor: ${exception.message}")
    }

    @Test
    fun `invalid argument types throw caller-facing argument exceptions`() {
        val badLimit = assertFailsWith<JetWhaleMcpArgumentException> {
            execute(listCommand(), "limit" to "many")
        }
        assertTrue("invalid limit" in badLimit.message!!, badLimit.message!!)

        val badEnabled = assertFailsWith<JetWhaleMcpArgumentException> {
            execute(SetMockingEnabledCommand { null }, "enabled" to "yes")
        }
        assertTrue("invalid enabled" in badEnabled.message!!, badEnabled.message!!)

        val addMockRule = AddMockRuleCommand(mockRules = { emptyList() }, syncMockRules = { null })
        val missingPattern = assertFailsWith<JetWhaleMcpArgumentException> {
            execute(addMockRule)
        }
        assertTrue("missing required argument: urlPattern" in missingPattern.message!!, missingPattern.message!!)

        val badMatchType = assertFailsWith<JetWhaleMcpArgumentException> {
            execute(addMockRule, "urlPattern" to "/x", "matchType" to "GLOB")
        }
        assertTrue("invalid matchType" in badMatchType.message!!, badMatchType.message!!)
    }

    @Test
    fun `addMockRule carries response headers through to the host`() {
        var synced: List<MockRule>? = null
        val command = AddMockRuleCommand(mockRules = { emptyList() }, syncMockRules = {
            synced = it
            null
        })
        val result = executeJson(
            command,
            "urlPattern" to JsonPrimitive("/x"),
            "headers" to buildJsonObject {
                put("Content-Type", "application/json")
                put("X-Trace", "abc")
            },
        )
        val rule = synced!!.single()
        assertEquals(mapOf("Content-Type" to "application/json", "X-Trace" to "abc"), rule.response.headers)
        assertTrue("application/json" in result, result)
    }

    @Test
    fun `addMockRule contentType convenience fills in the Content-Type header`() {
        var synced: List<MockRule>? = null
        val command = AddMockRuleCommand(mockRules = { emptyList() }, syncMockRules = {
            synced = it
            null
        })
        executeJson(
            command,
            "urlPattern" to JsonPrimitive("/x"),
            "contentType" to JsonPrimitive("application/json"),
        )
        assertEquals(mapOf("Content-Type" to "application/json"), synced!!.single().response.headers)
    }

    @Test
    fun `addMockRule explicit headers win over the contentType convenience`() {
        var synced: List<MockRule>? = null
        val command = AddMockRuleCommand(mockRules = { emptyList() }, syncMockRules = {
            synced = it
            null
        })
        executeJson(
            command,
            "urlPattern" to JsonPrimitive("/x"),
            "contentType" to JsonPrimitive("text/plain"),
            "headers" to buildJsonObject { put("Content-Type", "application/json") },
        )
        assertEquals(mapOf("Content-Type" to "application/json"), synced!!.single().response.headers)
    }

    @Test
    fun `setMockRules replaces the entire rule set`() {
        var synced: List<MockRule>? = null
        val command = SetMockRulesCommand(syncMockRules = {
            synced = it
            null
        })
        val edited = listOf(
            MockRule(
                id = "r1",
                name = "edited",
                enabled = false,
                matcher = MockMatcher(urlPattern = "/a", matchType = MockMatchType.EXACT),
                response = MockResponseSpec(statusCode = 201, headers = mapOf("Content-Type" to "application/json")),
            ),
        )
        val result = executeJson(command, "rules" to Json.encodeToJsonElement(edited))
        assertEquals(edited, synced)
        assertTrue("edited" in result, result)
    }

    @Test
    fun `setMockRules with a malformed rule throws a caller-facing argument exception`() {
        val command = SetMockRulesCommand(syncMockRules = { null })
        val exception = assertFailsWith<JetWhaleMcpArgumentException> {
            executeJson(
                command,
                "rules" to Json.parseToJsonElement("""[{"name":"missing id and matcher"}]"""),
            )
        }
        assertTrue("invalid rules" in exception.message!!, exception.message!!)
    }

    @Test
    fun `declaring a parameter after the schema was read fails fast`() {
        val command = object : JetWhaleMcpCommand() {
            override val name = "test.late"
            override val description = "declares a parameter inside execute"

            override suspend fun execute(arguments: JetWhaleMcpArguments): String {
                val late by stringOrNull("declared too late")
                return late.name
            }
        }
        command.toDescriptor()
        val exception = assertFailsWith<IllegalStateException> { execute(command) }
        assertTrue("late" in exception.message!!, exception.message!!)
    }

    @Test
    fun `declaring the same parameter name twice fails fast`() {
        val exception = assertFailsWith<IllegalStateException> {
            object : JetWhaleMcpCommand() {
                override val name = "test.dup"
                override val description = "declares the same name twice"

                private val first by stringOrNull("first declaration", name = "x")
                private val second by stringOrNull("second declaration", name = "x")

                override suspend fun execute(arguments: JetWhaleMcpArguments): String = "unused"
            }
        }
        assertTrue("declared twice" in exception.message!!, exception.message!!)
    }
}
