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
 * What a running host publishes in [HostVersionsDirectory.instanceRecord]: a loopback port where it
 * takes activation requests, its process ID, and the token a request has to carry.
 */
@Serializable
data class HostInstanceRecord(
    val port: Int,
    val pid: Long,
    val token: String,
) {
    companion object {
        /** The line a client sends, followed by the token. */
        const val ACTIVATE_REQUEST = "activate"

        /** The line the host answers a request with when the token matched. */
        const val ACCEPTED_RESPONSE = "ok"

        private val json = Json { ignoreUnknownKeys = true }

        fun read(directory: HostVersionsDirectory): HostInstanceRecord? = try {
            json.decodeFromString(serializer(), Files.readString(directory.instanceRecord))
        } catch (_: IOException) {
            null
        } catch (_: SerializationException) {
            null
        }

        fun publish(directory: HostVersionsDirectory, record: HostInstanceRecord) {
            HostVersionsDirectory.writeAtomically(directory.instanceRecord, json.encodeToString(serializer(), record))
        }

        /**
         * Asks the running host to bring its window forward. The record can be missing, or its endpoint
         * not answer yet, while that host is still starting, so it tries again until [timeout].
         */
        fun requestActivation(directory: HostVersionsDirectory, timeout: Duration): Boolean {
            val deadline = TimeSource.Monotonic.markNow() + timeout
            while (true) {
                val record = read(directory)
                if (record != null && sendActivation(record)) return true
                if (deadline.hasPassedNow()) return false
                Thread.sleep(RETRY_INTERVAL.inWholeMilliseconds)
            }
        }

        private val RETRY_INTERVAL = 200.milliseconds

        private fun sendActivation(record: HostInstanceRecord): Boolean = try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), record.port), 1_000)
                socket.soTimeout = 2_000
                socket.getOutputStream().write("$ACTIVATE_REQUEST ${record.token}\n".toByteArray())
                socket.getOutputStream().flush()
                socket.getInputStream().bufferedReader().readLine() == ACCEPTED_RESPONSE
            }
        } catch (_: IOException) {
            false
        }
    }
}
