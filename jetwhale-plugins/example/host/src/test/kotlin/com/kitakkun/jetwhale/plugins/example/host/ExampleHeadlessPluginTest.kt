package com.kitakkun.jetwhale.plugins.example.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleAdb
import com.kitakkun.jetwhale.host.sdk.JetWhaleAdbResult
import com.kitakkun.jetwhale.host.sdk.JetWhaleAdbTimeoutException
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginContext
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCapablePlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpContent
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpResult
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration

@OptIn(ExperimentalJetWhaleApi::class)
class ExampleHeadlessPluginTest {
    @Test
    fun `adbVersion reports what adb version printed`() {
        val result = adbVersion { JetWhaleAdbResult(exitCode = 0, output = "Android Debug Bridge version 1.0.41", errorOutput = "") }

        assertEquals(false, result.isError)
        assertEquals("Android Debug Bridge version 1.0.41", result.text())
    }

    @Test
    fun `adbVersion is a failed call when adb exits with an error`() {
        val result = adbVersion { JetWhaleAdbResult(exitCode = 1, output = "", errorOutput = "adb: usage") }

        assertEquals(true, result.isError)
        assertEquals("adb version exited with code 1: adb: usage", result.text())
    }

    @Test
    fun `adbVersion is a failed call when adb does not answer`() {
        val result = adbVersion { throw JetWhaleAdbTimeoutException("adb version did not finish within 10s", null) }

        assertEquals(true, result.isError)
        assertEquals("adb could not report its version: adb version did not finish within 10s", result.text())
    }

    private fun adbVersion(answer: () -> JetWhaleAdbResult): JetWhaleMcpResult {
        val adb = object : JetWhaleAdb {
            override suspend fun run(vararg args: String, timeout: Duration): JetWhaleAdbResult = answer()

            override suspend fun <T> runStreaming(vararg args: String, timeout: Duration, consume: suspend (InputStream) -> T): T = error("adbVersion streams nothing")
        }
        val context = object : JetWhaleHostPluginContext {
            override val adb: JetWhaleAdb = adb
        }
        val plugin = ExampleHeadlessPluginFactory().createPlugin(context) as JetWhaleMcpCapablePlugin
        val command = plugin.mcpCommands.single { it.name == "com.kitakkun.jetwhale.example.headless.adbVersion" }
        return runBlocking { command.run(JetWhaleMcpArguments(JsonObject(emptyMap()))) }
    }

    private fun JetWhaleMcpResult.text(): String = (content.single() as JetWhaleMcpContent.Text).text
}
