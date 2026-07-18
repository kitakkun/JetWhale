package com.kitakkun.jetwhale.plugins.network.host

import com.kitakkun.jetwhale.host.sdk.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommandPlugin
import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpRequest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalJetWhaleApi::class)
class NetworkMcpCommandsTest {
    private fun tx(txId: String, timestampMs: Long, url: String = "https://api.example.com/$txId", method: String = "GET") = HttpTransaction(
        request = CapturedHttpRequest(txId = txId, method = method, url = url, timestampMs = timestampMs),
    )

    private val transactions = listOf(tx("a", 100), tx("b", 200), tx("c", 300), tx("d", 400))

    private fun listCommand(data: List<HttpTransaction> = transactions) = ListTransactionsCommand(transactions = { data }, redactForMcp = { it })

    private fun execute(command: JetWhaleMcpCommand, vararg args: Pair<String, String>): String = runBlocking { command.execute(JetWhaleMcpArguments(args.toMap())) }

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
    fun `unknown afterTxId is rendered as an error payload by the command plugin`() {
        val plugin = object : JetWhaleMcpCommandPlugin {
            override val mcpCommands: List<JetWhaleMcpCommand> = listOf(listCommand())
        }
        val result = runBlocking { plugin.handleMcpTool("$TOOL_PREFIX.listTransactions", mapOf("afterTxId" to "ghost")) }
        val error = Json.parseToJsonElement(result!!).jsonObject.getValue("error").jsonPrimitive.content
        assertTrue("ghost" in error, "Error should name the unknown cursor: $error")
    }

    @Test
    fun `invalid argument types are rendered as error payloads`() {
        val plugin = object : JetWhaleMcpCommandPlugin {
            override val mcpCommands: List<JetWhaleMcpCommand> = listOf(
                listCommand(),
                SetMockingEnabledCommand { null },
                AddMockRuleCommand(mockRules = { emptyList() }, syncMockRules = { null }),
            )
        }
        runBlocking {
            val badLimit = plugin.handleMcpTool("$TOOL_PREFIX.listTransactions", mapOf("limit" to "many"))
            assertTrue("invalid limit" in badLimit!!, badLimit)

            val badEnabled = plugin.handleMcpTool("$TOOL_PREFIX.setMockingEnabled", mapOf("enabled" to "yes"))
            assertTrue("invalid enabled" in badEnabled!!, badEnabled)

            val missingPattern = plugin.handleMcpTool("$TOOL_PREFIX.addMockRule", emptyMap())
            assertTrue("missing required argument: urlPattern" in missingPattern!!, missingPattern)

            val badMatchType = plugin.handleMcpTool("$TOOL_PREFIX.addMockRule", mapOf("urlPattern" to "/x", "matchType" to "GLOB"))
            assertTrue("invalid matchType" in badMatchType!!, badMatchType)

            val unknownTool = plugin.handleMcpTool("$TOOL_PREFIX.nope", emptyMap())
            assertNull(unknownTool, "Unknown tools must return null so other handlers can claim them")
        }
    }
}
