package com.kitakkun.jetwhale.host.mcp.tools.host

import com.kitakkun.jetwhale.host.mcp.text
import com.kitakkun.jetwhale.host.model.LogCaptureService
import com.kitakkun.jetwhale.host.model.LogEntry
import com.kitakkun.jetwhale.host.model.LogLevel
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpResult
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import dev.mokkery.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Instant

class HostLogCommandsTest {

    private val logs = MutableStateFlow(
        listOf(
            logEntry("first info", LogLevel.INFO),
            logEntry("second boom", LogLevel.ERROR),
            logEntry("third info", LogLevel.INFO),
        ),
    )
    private val logCaptureService = mock<LogCaptureService> {
        every { this@mock.logs } returns this@HostLogCommandsTest.logs
        every { clearLogs() } returns Unit
    }

    @Test
    fun `getLogs returns the newest entries last`() = runBlocking {
        val result = GetLogsCommand(logCaptureService).execute(arguments()).decodeLogs()

        assertEquals(listOf("first info", "second boom", "third info"), result.logs.map { it.message })
        assertEquals(3, result.returned)
        assertEquals(3, result.total)
    }

    @Test
    fun `getLogs keeps only the most recent entries when a limit is given`() = runBlocking {
        val result = GetLogsCommand(logCaptureService).execute(arguments("limit" to JsonPrimitive(2))).decodeLogs()

        assertEquals(listOf("second boom", "third info"), result.logs.map { it.message })
        assertEquals(2, result.returned)
        assertEquals(3, result.total)
    }

    @Test
    fun `getLogs filters by level`() = runBlocking {
        val result = GetLogsCommand(logCaptureService).execute(arguments("level" to JsonPrimitive("ERROR"))).decodeLogs()

        assertEquals(listOf("second boom"), result.logs.map { it.message })
        assertEquals(1, result.total)
    }

    @Test
    fun `getLogs filters by substring case-insensitively`() = runBlocking {
        val result = GetLogsCommand(logCaptureService).execute(arguments("contains" to JsonPrimitive("BOOM"))).decodeLogs()

        assertEquals(listOf("second boom"), result.logs.map { it.message })
    }

    @Test
    fun `getLogs rejects a limit above the hard cap`(): Unit = runBlocking {
        assertFailsWith<JetWhaleMcpArgumentException> {
            GetLogsCommand(logCaptureService).execute(arguments("limit" to JsonPrimitive(1001)))
        }
    }

    @Test
    fun `getLogs truncates an oversized message`() = runBlocking {
        logs.value = listOf(logEntry("x".repeat(3000), LogLevel.INFO))

        val message = GetLogsCommand(logCaptureService).execute(arguments()).decodeLogs().logs.single().message

        assertEquals(2000 + "…(truncated)".length, message.length)
        assertTrue(message.endsWith("…(truncated)"))
    }

    @Test
    fun `clearLogs reports how many entries were dropped`() = runBlocking {
        val result = ClearLogsCommand(logCaptureService).execute(arguments())

        assertEquals(3, Json.decodeFromString<ClearLogsResult>(result.text).cleared)
        verify { logCaptureService.clearLogs() }
    }
}

private fun logEntry(message: String, level: LogLevel) = LogEntry(
    timestamp = Instant.fromEpochMilliseconds(0),
    message = message,
    level = level,
)

private fun arguments(vararg entries: Pair<String, JsonPrimitive>) = JetWhaleMcpArguments(JsonObject(entries.toMap()))

private fun JetWhaleMcpResult.decodeLogs() = Json.decodeFromString<GetLogsResult>(text)
