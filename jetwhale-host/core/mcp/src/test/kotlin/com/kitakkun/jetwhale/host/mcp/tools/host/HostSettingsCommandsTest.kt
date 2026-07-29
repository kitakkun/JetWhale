package com.kitakkun.jetwhale.host.mcp.tools.host

import com.kitakkun.jetwhale.host.model.DebugWebSocketServer
import com.kitakkun.jetwhale.host.model.DebugWebSocketServerStatus
import com.kitakkun.jetwhale.host.model.DebuggerSettingsRepository
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HostSettingsCommandsTest {

    private val serverPortFlow = MutableStateFlow(5080)
    private val wssPortFlow = MutableStateFlow(5443)
    private val wssEnabledFlow = MutableStateFlow(true)

    private val settingsRepository = mock<DebuggerSettingsRepository>(MockMode.autoUnit) {
        every { this@mock.serverPortFlow } returns this@HostSettingsCommandsTest.serverPortFlow
        every { this@mock.wssPortFlow } returns this@HostSettingsCommandsTest.wssPortFlow
        every { this@mock.wssEnabledFlow } returns this@HostSettingsCommandsTest.wssEnabledFlow
    }

    private val debugWebSocketServer = mock<DebugWebSocketServer>(MockMode.autoUnit) {
        every { statusFlow } returns MutableStateFlow(DebugWebSocketServerStatus.Started("localhost", 5080, 5443))
    }

    private val updateSettings = UpdateSettingsCommand(settingsRepository, debugWebSocketServer)

    @Test
    fun `updateSettings applies only the arguments that were supplied`() = runBlocking {
        val result = updateSettings.executeForText(arguments("persistData" to JsonPrimitive(true))).decodeSettings()

        assertEquals(mapOf("persistData" to "true"), result.applied)
        verifySuspend { settingsRepository.updatePersistData(true) }
        verifySuspend(VerifyMode.not) { settingsRepository.updateServerPort(any()) }
    }

    @Test
    fun `updateSettings persists the mcp port without restarting the mcp server`() = runBlocking {
        val result = updateSettings.executeForText(arguments("mcpServerPort" to JsonPrimitive(7100))).decodeSettings()

        assertFalse(result.mcpServerRestarted)
        assertFalse(result.debugServerRestarted)
        assertTrue(result.notes.any { "next time the host starts" in it })
        verifySuspend { settingsRepository.updateMcpServerPort(7100) }
        verifySuspend(VerifyMode.not) { debugWebSocketServer.stop() }
    }

    @Test
    fun `updateSettings restarts the debug server when the ws port changed`() = runBlocking {
        serverPortFlow.value = 5090
        val result = updateSettings.executeForText(arguments("serverPort" to JsonPrimitive(5090))).decodeSettings()

        assertTrue(result.debugServerRestarted)
        verifySuspend { debugWebSocketServer.stop() }
        verifySuspend { debugWebSocketServer.start("localhost", 5090, 5443) }
    }

    @Test
    fun `updateSettings restarts the debug server when adb auto port mapping changed`() = runBlocking {
        // The setting is read when the server starts, so it is inert until the server restarts.
        val result = updateSettings.executeForText(arguments("adbAutoPortMappingEnabled" to JsonPrimitive(true))).decodeSettings()

        assertTrue(result.debugServerRestarted)
        verifySuspend { debugWebSocketServer.stop() }
    }

    @Test
    fun `updateSettings does not claim a change is pending once it has restarted the server`() = runBlocking {
        val result = updateSettings.executeForText(arguments("adbAutoPortMappingEnabled" to JsonPrimitive(true))).decodeSettings()

        assertFalse(result.notes.any { "still running" in it })
    }

    @Test
    fun `updateSettings can persist a ws change without restarting when asked`() = runBlocking {
        val result = updateSettings.executeForText(
            arguments(
                "serverPort" to JsonPrimitive(5090),
                "restartDebugServer" to JsonPrimitive(false),
            ),
        ).decodeSettings()

        assertFalse(result.debugServerRestarted)
        assertTrue(result.notes.any { "jetwhale.restartDebugServer" in it })
        verifySuspend(VerifyMode.not) { debugWebSocketServer.stop() }
    }

    @Test
    fun `updateSettings rejects an out-of-range port before writing anything`(): Unit = runBlocking {
        assertFailsWith<JetWhaleMcpArgumentException> {
            updateSettings.executeForText(
                arguments(
                    "persistData" to JsonPrimitive(true),
                    "serverPort" to JsonPrimitive(70000),
                ),
            )
        }
        verifySuspend(VerifyMode.not) { settingsRepository.updatePersistData(any()) }
    }

    @Test
    fun `updateSettings rejects a call that changes nothing`(): Unit = runBlocking {
        assertFailsWith<JetWhaleMcpArgumentException> { updateSettings.executeForText(arguments()) }
    }

    @Test
    fun `restartDebugServer starts with the configured wss port when wss is enabled`() = runBlocking {
        RestartDebugServerCommand(settingsRepository, debugWebSocketServer).executeForText(arguments())

        verifySuspend { debugWebSocketServer.stop() }
        verifySuspend { debugWebSocketServer.start("localhost", 5080, 5443) }
    }

    @Test
    fun `restartDebugServer omits the wss port when wss is disabled`() = runBlocking {
        wssEnabledFlow.value = false

        val result = RestartDebugServerCommand(settingsRepository, debugWebSocketServer)
            .executeForText(arguments())
            .let { Json.decodeFromString<RestartDebugServerResult>(it) }

        verifySuspend { debugWebSocketServer.start("localhost", 5080, null) }
        assertTrue("dropped" in result.note)
    }

    @Test
    fun `restartDebugServer reports the state the server ended up in`() = runBlocking {
        val result = RestartDebugServerCommand(settingsRepository, debugWebSocketServer)
            .executeForText(arguments())
            .let { Json.decodeFromString<RestartDebugServerResult>(it) }

        assertEquals("Started", result.state)
        assertEquals(5080, result.port)
        assertEquals(5443, result.wssPort)
    }
}

private fun arguments(vararg entries: Pair<String, JsonPrimitive>) = JetWhaleMcpArguments(JsonObject(entries.toMap()))

private fun String.decodeSettings() = Json.decodeFromString<UpdateSettingsResult>(this)
