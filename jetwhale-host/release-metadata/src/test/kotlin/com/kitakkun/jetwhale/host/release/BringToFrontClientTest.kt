package com.kitakkun.jetwhale.host.release

import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class BringToFrontClientTest {
    private val hostDirectory = HostDirectory(Files.createTempDirectory("bring-to-front").resolve("host"))
    private val bringToFrontClient = BringToFrontClient(hostDirectory)
    private val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
    private val requests = CopyOnWriteArrayList<String>()

    init {
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = runCatching(server::accept).getOrNull() ?: break
                socket.use {
                    val request = it.getInputStream().bufferedReader().readLine()
                    requests += request
                    val answer = if (request == "bring-to-front secret") "ok" else "denied"
                    it.getOutputStream().write("$answer\n".toByteArray())
                }
            }
        }
    }

    @AfterTest
    fun closeServer() {
        server.close()
    }

    @Test
    fun `asks the host at the published endpoint with its token`() {
        hostDirectory.publishInstanceJson(InstanceJson(port = server.localPort, pid = 42, token = "secret"))

        assertTrue(bringToFrontClient.requestBringToFront(2_000.milliseconds))
        assertEquals(listOf("bring-to-front secret"), requests)
    }

    @Test
    fun `gives up when the host refuses the token`() {
        hostDirectory.publishInstanceJson(InstanceJson(port = server.localPort, pid = 42, token = "stale"))

        assertFalse(bringToFrontClient.requestBringToFront(0.milliseconds))
    }

    @Test
    fun `gives up when nothing is published`() {
        assertFalse(bringToFrontClient.requestBringToFront(0.milliseconds))
        assertTrue(requests.isEmpty())
    }
}
