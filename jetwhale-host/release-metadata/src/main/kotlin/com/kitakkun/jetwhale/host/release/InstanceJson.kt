package com.kitakkun.jetwhale.host.release

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.file.Files
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * What a running host publishes in [HostVersionsDirectory.instanceJsonFile]: a loopback port where it
 * takes requests to bring its window to the front, its process ID, and the token a request has to carry.
 */
@Serializable
data class InstanceJson(
    val port: Int,
    val pid: Long,
    val token: String,
) {
    companion object {
        /** The line a client sends, followed by the token. */
        const val BRING_TO_FRONT_REQUEST = "bring-to-front"

        /** The line the host answers a request with when the token matched. */
        const val ACCEPTED_RESPONSE = "ok"

        private val json = Json { ignoreUnknownKeys = true }

        fun read(hostVersionsDirectory: HostVersionsDirectory): InstanceJson? = try {
            json.decodeFromString(serializer(), Files.readString(hostVersionsDirectory.instanceJsonFile))
        } catch (_: IOException) {
            null
        } catch (_: SerializationException) {
            null
        }

        fun publish(hostVersionsDirectory: HostVersionsDirectory, instanceJson: InstanceJson) {
            HostVersionsDirectory.writeAtomically(hostVersionsDirectory.instanceJsonFile, json.encodeToString(serializer(), instanceJson))
        }

        /**
         * Asks the running host to bring its window to the front. `instance.json` can be missing, or its
         * endpoint not answer yet, while that host is still starting, so it tries again until [timeout].
         */
        fun requestBringToFront(hostVersionsDirectory: HostVersionsDirectory, timeout: Duration): Boolean {
            val deadline = TimeSource.Monotonic.markNow() + timeout
            while (true) {
                val instanceJson = read(hostVersionsDirectory)
                if (instanceJson != null && sendBringToFront(instanceJson)) return true
                if (deadline.hasPassedNow()) return false
                Thread.sleep(RETRY_INTERVAL.inWholeMilliseconds)
            }
        }

        private val RETRY_INTERVAL = 200.milliseconds

        private fun sendBringToFront(instanceJson: InstanceJson): Boolean = try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), instanceJson.port), 1_000)
                socket.soTimeout = 2_000
                socket.getOutputStream().write("$BRING_TO_FRONT_REQUEST ${instanceJson.token}\n".toByteArray())
                socket.getOutputStream().flush()
                socket.getInputStream().bufferedReader().readLine() == ACCEPTED_RESPONSE
            }
        } catch (_: IOException) {
            false
        }
    }
}
