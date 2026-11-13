package com.kitakkun.jetwhale.plugins.background.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.background.protocol.CancelTarget
import com.kitakkun.jetwhale.plugins.background.protocol.WorkState
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
import kotlin.test.assertFailsWith

@OptIn(ExperimentalJetWhaleApi::class)
class BackgroundWorkMcpCommandsTest {
    private val client = FakeBackgroundWorkClient(
        listOf(
            workItem("WorkManager", "sync", WorkState.Enqueued, tags = listOf("sync"), canRunNow = true),
            workItem("WorkManager", "upload", WorkState.Failed, tags = listOf("upload"), canRunNow = true),
            workItem("JobScheduler", "cleanup", WorkState.Scheduled, tags = emptyList(), canRunNow = false),
        ),
    )

    @Test
    fun `listBackgroundWork filters by state`() {
        val result = ListBackgroundWorkCommand(client).run(buildJsonObject { put("state", "Failed") })

        assertEquals(listOf("upload"), result.getValue("items").jsonArray.map { it.jsonObject.getValue("id").jsonPrimitive.content })
    }

    @Test
    fun `listBackgroundWork reports every source even when the filter leaves no items`() {
        val result = ListBackgroundWorkCommand(client).run(buildJsonObject { put("query", "nothing matches this") })

        assertEquals(2, result.getValue("sources").jsonArray.size)
        assertEquals(0, result.getValue("items").jsonArray.size)
    }

    @Test
    fun `cancelWork by tag sends a tag target`() {
        CancelWorkCommand(client).run(
            buildJsonObject {
                put("source", "WorkManager")
                put("tag", "sync")
            },
        )

        assertEquals(listOf<Pair<String, CancelTarget>>("WorkManager" to CancelTarget.ByTag("sync")), client.cancelled)
    }

    @Test
    fun `cancelWork refuses more than one target`() {
        assertFailsWith<JetWhaleMcpArgumentException> {
            CancelWorkCommand(client).run(
                buildJsonObject {
                    put("source", "WorkManager")
                    put("id", "sync")
                    put("tag", "sync")
                },
            )
        }
    }

    @Test
    fun `cancelWork refuses no target`() {
        assertFailsWith<JetWhaleMcpArgumentException> { CancelWorkCommand(client).run(buildJsonObject { put("source", "WorkManager") }) }
    }

    @Test
    fun `runWorkNow passes the refusal through as an error`() {
        val result = RunWorkNowCommand(client).run(
            buildJsonObject {
                put("source", "JobScheduler")
                put("id", "cleanup")
            },
        )

        assertEquals("cannot run cleanup now", result.getValue("error").jsonPrimitive.content)
    }
}

@OptIn(ExperimentalJetWhaleApi::class)
private fun JetWhaleMcpCommand.run(arguments: JsonObject): JsonObject = runBlocking {
    Json.parseToJsonElement(execute(JetWhaleMcpArguments(arguments))).jsonObject
}
