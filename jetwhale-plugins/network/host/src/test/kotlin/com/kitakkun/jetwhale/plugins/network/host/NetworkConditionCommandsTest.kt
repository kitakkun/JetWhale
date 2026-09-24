package com.kitakkun.jetwhale.plugins.network.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.network.protocol.AppliedNetworkCondition
import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpRequest
import com.kitakkun.jetwhale.plugins.network.protocol.HttpRequestFailure
import com.kitakkun.jetwhale.plugins.network.protocol.InjectedFailure
import com.kitakkun.jetwhale.plugins.network.protocol.NetworkCondition
import com.kitakkun.jetwhale.plugins.network.protocol.NetworkConditionRule
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalJetWhaleApi::class)
class NetworkConditionCommandsTest {
    private var applied: List<NetworkConditionRule> = emptyList()
    private val sync: suspend (List<NetworkConditionRule>) -> JetWhaleMessagingException? = { rules ->
        applied = rules
        null
    }

    @Test
    fun `a preset becomes one rule for every request`() {
        execute(SetNetworkConditionsCommand(sync), "preset" to JsonPrimitive("Offline"))

        val rule = applied.single()
        assertEquals(null, rule.matcher)
        assertTrue(rule.condition.offline)
    }

    @Test
    fun `rules replace the whole set and win over a preset`() {
        val rules = listOf(NetworkConditionRule(id = "r", name = "Slow", enabled = true, matcher = null, condition = NetworkCondition(latencyMs = 900)))

        execute(SetNetworkConditionsCommand(sync), "rules" to Json.encodeToJsonElement(rules), "preset" to JsonPrimitive("Offline"))

        assertEquals(rules, applied)
    }

    @Test
    fun `setNetworkConditions needs a preset or rules`() {
        assertFailsWith<JetWhaleMcpArgumentException> { execute(SetNetworkConditionsCommand(sync)) }
    }

    @Test
    fun `clearNetworkConditions sends an empty set`() {
        applied = listOf(NetworkConditionPreset.Edge.toRule())

        execute(ClearNetworkConditionsCommand(sync))

        assertEquals(emptyList(), applied)
    }

    @Test
    fun `getNetworkConditions lists the rules in match order`() {
        val rules = listOf(NetworkConditionPreset.Fast3G.toRule(), NetworkConditionPreset.Flaky.toRule())

        val result = Json.parseToJsonElement(execute(GetNetworkConditionsCommand(conditionRules = { rules }))).jsonObject

        assertEquals(listOf("Fast 3G", "Flaky"), result.getValue("rules").jsonArray.map { it.jsonObject.getValue("name").jsonPrimitive.content })
    }

    @Test
    fun `transaction tools report the condition that shaped a request`() {
        val condition = AppliedNetworkCondition(
            ruleId = "r",
            ruleName = "Flaky",
            addedLatencyMs = 250,
            downloadBytesPerSecond = null,
            uploadBytesPerSecond = null,
            injectedFailure = InjectedFailure.CONNECTION_RESET,
            offline = false,
        )
        val tx = HttpTransaction(
            request = CapturedHttpRequest(txId = "t", method = "GET", url = "https://example.com/items", timestampMs = 0),
            failure = HttpRequestFailure(txId = "t", message = "Connection reset", durationMs = 250, condition = condition),
        )

        val summary = tx.toSummaryJson().getValue("networkCondition").jsonObject

        assertEquals("CONNECTION_RESET", summary.getValue("injectedFailure").jsonPrimitive.content)
        assertEquals(250, summary.getValue("addedLatencyMs").jsonPrimitive.content.toInt())
    }

    private fun execute(command: JetWhaleMcpCommand, vararg args: Pair<String, JsonElement>): String = runBlocking { command.execute(JetWhaleMcpArguments(JsonObject(args.toMap()))) }
}
