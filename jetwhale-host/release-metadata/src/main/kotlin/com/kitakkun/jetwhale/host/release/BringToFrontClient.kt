package com.kitakkun.jetwhale.host.release

import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/** Asks the running host to bring its window to the front, at the endpoint its `instance.json` names. */
class BringToFrontClient(private val hostDirectory: HostDirectory) {
    /**
     * Sends the request with the token from `instance.json` and returns whether the host accepted it.
     * `instance.json` can be missing, or its endpoint not answer yet, while that host is still
     * starting, so it tries again until [timeout].
     */
    fun requestBringToFront(timeout: Duration): Boolean {
        val deadline = TimeSource.Monotonic.markNow() + timeout
        while (true) {
            val instanceJson = hostDirectory.readInstanceJson()
            if (instanceJson != null && sendRequest(instanceJson)) return true
            if (deadline.hasPassedNow()) return false
            Thread.sleep(RETRY_INTERVAL.inWholeMilliseconds)
        }
    }

    private fun sendRequest(instanceJson: InstanceJson): Boolean = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), instanceJson.port), 1_000)
            socket.soTimeout = 2_000
            socket.getOutputStream().write("${requestLine(instanceJson.token)}\n".toByteArray())
            socket.getOutputStream().flush()
            socket.getInputStream().bufferedReader().readLine() == ACCEPTED_RESPONSE
        }
    } catch (_: IOException) {
        false
    }

    companion object {
        /** The line a client sends to the host whose `instance.json` carries [token], without its line break. */
        fun requestLine(token: String): String = "bring-to-front $token"

        /** The line the host answers a request with when the token matched. */
        const val ACCEPTED_RESPONSE = "ok"

        private val RETRY_INTERVAL = 200.milliseconds
    }
}
