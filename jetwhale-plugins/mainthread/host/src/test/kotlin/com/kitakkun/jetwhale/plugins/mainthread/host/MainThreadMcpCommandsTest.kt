package com.kitakkun.jetwhale.plugins.mainthread.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.mainthread.protocol.FrameStats
import com.kitakkun.jetwhale.plugins.mainthread.protocol.Hotspot
import com.kitakkun.jetwhale.plugins.mainthread.protocol.LongTask
import com.kitakkun.jetwhale.plugins.mainthread.protocol.MainThreadReport
import com.kitakkun.jetwhale.plugins.mainthread.protocol.MonitorCapabilities
import com.kitakkun.jetwhale.plugins.mainthread.protocol.MonitorSettings
import com.kitakkun.jetwhale.plugins.mainthread.protocol.ViolationGroup
import com.kitakkun.jetwhale.plugins.mainthread.protocol.ViolationKind
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
class MainThreadMcpCommandsTest {
    private val client = FakeMainThreadClient(
        report(
            hotspots = (1..15).map { hotspot("com.example.Site$it.run", samples = 20 - it) },
            violations = listOf(
                violation(ViolationKind.DiskWrite, "com.example.Prefs.save"),
                violation(ViolationKind.Network, "com.example.Api.fetch"),
            ),
            longTasks = listOf(longTask(duration = 120), longTask(duration = 6_000)),
        ),
    )

    @Test
    fun `getHotspots returns the most blocking first up to the limit`() {
        val result = GetHotspotsCommand(client).run(buildJsonObject { put("limit", 3) })

        val signatures = result.getValue("hotspots").jsonArray.map { it.jsonObject.getValue("signature").jsonPrimitive.content }
        assertEquals(listOf("com.example.Site1.run", "com.example.Site2.run", "com.example.Site3.run"), signatures)
    }

    @Test
    fun `every result says what the platform can report and the thresholds in force`() {
        val result = GetFrameStatsCommand(client).run()

        assertEquals("true", result.getValue("capabilities").jsonObject.getValue("strictMode").jsonPrimitive.content)
        assertEquals("100", result.getValue("settings").jsonObject.getValue("longTaskThresholdMillis").jsonPrimitive.content)
        assertTrue("recordingSinceEpochMillis" in result)
    }

    @Test
    fun `getViolations filters by kind`() {
        val result = GetViolationsCommand(client).run(buildJsonObject { put("kind", "Network") })

        assertEquals(listOf("com.example.Api.fetch"), result.getValue("violations").jsonArray.map { it.jsonObject.getValue("callSite").jsonPrimitive.content })
    }

    @Test
    fun `getLongTasks filters by minimum duration`() {
        val result = GetLongTasksCommand(client).run(buildJsonObject { put("minDurationMillis", 1_000) })

        assertEquals(listOf("6000"), result.getValue("longTasks").jsonArray.map { it.jsonObject.getValue("durationMillis").jsonPrimitive.content })
    }

    @Test
    fun `resetStats clears the app's records`() {
        ResetStatsCommand(client).run()

        assertEquals(1, client.resets)
    }
}

@OptIn(ExperimentalJetWhaleApi::class)
private fun JetWhaleMcpCommand.run(arguments: JsonObject = buildJsonObject { }): JsonObject = runBlocking {
    Json.parseToJsonElement(execute(JetWhaleMcpArguments(arguments))).jsonObject
}
