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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class HostInstanceRecordTest {
    private val versions = HostVersionsDirectory(Files.createTempDirectory("instance-record").resolve("host"))
    private val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
    private val requests = CopyOnWriteArrayList<String>()

    init {
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = runCatching(server::accept).getOrNull() ?: break
                socket.use {
                    val request = it.getInputStream().bufferedReader().readLine()
                    requests += request
                    val answer = if (request == "activate secret") "ok" else "denied"
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
    fun `reads back the record it published`() {
        val record = HostInstanceRecord(port = 5000, pid = 42, token = "secret")

        HostInstanceRecord.publish(versions, record)

        assertEquals(record, HostInstanceRecord.read(versions))
    }

    @Test
    fun `has no record before a host publishes one`() {
        assertNull(HostInstanceRecord.read(versions))
    }

    @Test
    fun `asks the host at the published endpoint with its token`() {
        HostInstanceRecord.publish(versions, HostInstanceRecord(port = server.localPort, pid = 42, token = "secret"))

        assertTrue(HostInstanceRecord.requestActivation(versions, 2_000.milliseconds))
        assertEquals(listOf("activate secret"), requests)
    }

    @Test
    fun `gives up when the host refuses the token`() {
        HostInstanceRecord.publish(versions, HostInstanceRecord(port = server.localPort, pid = 42, token = "stale"))

        assertFalse(HostInstanceRecord.requestActivation(versions, 0.milliseconds))
    }

    @Test
    fun `gives up when no record appears`() {
        assertFalse(HostInstanceRecord.requestActivation(versions, 0.milliseconds))
        assertTrue(requests.isEmpty())
    }
}
