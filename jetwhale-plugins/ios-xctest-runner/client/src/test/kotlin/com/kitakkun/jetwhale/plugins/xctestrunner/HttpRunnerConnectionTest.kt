package com.kitakkun.jetwhale.plugins.xctestrunner

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HttpRunnerConnectionTest {
    private val httpClient = OkHttpClient()

    @Test
    fun `a command is posted to its path with its body and the run's token`() = withConnection(MockResponse().setBody("""{"ok":true}""")) { connection, server ->
        connection.send("/typeText", buildJsonObject { put("text", "hello") })

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/typeText", request.path)
        assertEquals("token-1", request.getHeader(RUNNER_TOKEN_HEADER))
        assertEquals("hello", Json.parseToJsonElement(request.body.readUtf8()).jsonObject.getValue("text").jsonPrimitive.content)
    }

    @Test
    fun `the status gives the runner's protocol and screen, ignoring what it does not know`() = withConnection(
        MockResponse().setBody("""{"ok":true,"protocolVersion":1,"screenWidthPixels":1206,"screenHeightPixels":2622,"scale":3,"eventSynthesis":true,"extra":1}"""),
    ) { connection, _ ->
        assertEquals(runnerStatus(protocolVersion = 1), connection.status())
    }

    @Test
    fun `the runner's error becomes the reason the command failed`() = withConnection(
        MockResponse().setBody("""{"ok":false,"error":"no button named volumeUp on this device"}"""),
    ) { connection, _ ->
        val failure = assertFailsWith<XcTestRunnerException> { connection.send("/pressButton", buildJsonObject { put("button", "volumeUp") }) }

        assertEquals("the XCTest runner failed to pressButton: no button named volumeUp on this device", failure.message)
    }

    @Test
    fun `an answer that is not JSON is a failure that quotes it`() = withConnection(MockResponse().setBody("<html>")) { connection, _ ->
        val failure = assertFailsWith<XcTestRunnerException> { connection.send("/tap", buildJsonObject { }) }

        assertEquals("the XCTest runner answered something other than JSON: <html>", failure.message)
    }

    @Test
    fun `a port nobody listens on is unreachable rather than a refusal`() = runBlocking {
        val closedPort = ServerSocket(0).use(ServerSocket::getLocalPort)

        assertFailsWith<RunnerUnreachableException> { HttpRunnerConnection(closedPort, "token-1", httpClient).status() }
        Unit
    }

    private fun withConnection(response: MockResponse, block: suspend (HttpRunnerConnection, MockWebServer) -> Unit) = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(response)
            server.start()
            block(HttpRunnerConnection(server.port, "token-1", httpClient), server)
        }
    }
}
