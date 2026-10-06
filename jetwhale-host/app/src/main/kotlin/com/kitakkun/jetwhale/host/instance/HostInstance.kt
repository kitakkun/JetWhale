package com.kitakkun.jetwhale.host.instance

import com.kitakkun.jetwhale.host.release.HeldLock
import com.kitakkun.jetwhale.host.release.HostVersionsDirectory
import com.kitakkun.jetwhale.host.release.InstanceJson
import com.kitakkun.jetwhale.host.release.LockFiles
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.seconds

/**
 * A host the launcher started, as the one host of its app data directory. It holds `instance.lock`
 * from its start until the process ends, and takes requests to bring its window forward on a
 * loopback port that it publishes with a token in `instance.json` once it is up.
 */
class HostInstance private constructor(
    private val hostVersionsDirectory: HostVersionsDirectory,
    private val server: ServerSocket,
    private val token: String,
) {
    private val mutableBringToFrontRequests = MutableSharedFlow<Unit>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /**
     * A request to bring the window to the front, from a launcher or from a second host. The latest one
     * is kept for a collector that starts after it arrived, as the window's does after
     * [publishInstanceJson].
     */
    val bringToFrontRequests: Flow<Unit> = mutableBringToFrontRequests

    /**
     * Publishes `instance.json`, which marks this host as up: once its main window shows, or, with
     * `--headless`, once its servers are bound. The launcher lets the next launch go on once it
     * sees it, and reaches the host through it.
     */
    fun publishInstanceJson() {
        InstanceJson.publish(
            hostVersionsDirectory,
            InstanceJson(port = server.localPort, pid = ProcessHandle.current().pid(), token = token),
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(HostInstance::class.java)

        /**
         * Never closed: the OS releases it when the process ends. A lock whose channel is collected
         * is released too, so it is kept reachable here for the whole process.
         */
        @Volatile
        private var processInstanceLock: HeldLock? = null

        /**
         * Takes `instance.lock` for this process and starts taking requests; [publishInstanceJson] makes
         * them reachable. When another host holds the lock, asks that one to bring its window forward
         * instead.
         */
        fun claim(hostVersionsDirectory: HostVersionsDirectory, lockFiles: LockFiles): HostInstanceClaim {
            processInstanceLock = lockFiles.tryLock(hostVersionsDirectory.instanceLockFile)
                ?: return HostInstanceClaim.HeldByAnother(
                    broughtToFront = InstanceJson.requestBringToFront(hostVersionsDirectory, timeout = 5.seconds),
                )
            val server = ServerSocket(0, 8, InetAddress.getLoopbackAddress())
            val token = ByteArray(16).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
            val instance = HostInstance(hostVersionsDirectory, server, token)
            instance.serve()
            return HostInstanceClaim.Claimed(instance)
        }
    }

    private fun serve() {
        thread(isDaemon = true, name = "host-instance-bring-to-front") {
            while (!server.isClosed) {
                val connection = try {
                    server.accept()
                } catch (e: IOException) {
                    logger.warn("Stopped taking requests to bring the window forward", e)
                    break
                }
                connection.use(::answer)
            }
        }
    }

    private fun answer(connection: Socket) {
        try {
            val accepted = readRequest(connection) == "${InstanceJson.BRING_TO_FRONT_REQUEST} $token"
            if (accepted) mutableBringToFrontRequests.tryEmit(Unit)
            connection.getOutputStream().write("${if (accepted) InstanceJson.ACCEPTED_RESPONSE else "denied"}\n".toByteArray())
        } catch (e: IOException) {
            logger.warn("A request to bring the window to the front could not be read", e)
        }
    }
}

/**
 * Reads one request line, or null when the client sends more than a request can be or takes longer
 * than [REQUEST_DEADLINE_NANOS] in all, so no client holds the one thread that answers.
 */
private fun readRequest(connection: Socket): String? {
    val deadline = System.nanoTime() + REQUEST_DEADLINE_NANOS
    connection.soTimeout = REQUEST_DEADLINE_MILLIS
    val input = connection.getInputStream()
    val request = ByteArrayOutputStream()
    while (request.size() < MAX_REQUEST_BYTES && System.nanoTime() < deadline) {
        val byte = input.read()
        if (byte < 0 || byte == '\n'.code) return request.toString(Charsets.UTF_8)
        request.write(byte)
    }
    return null
}

private const val MAX_REQUEST_BYTES = 128
private const val REQUEST_DEADLINE_MILLIS = 2_000
private const val REQUEST_DEADLINE_NANOS = REQUEST_DEADLINE_MILLIS * 1_000_000L

sealed interface HostInstanceClaim {
    data class Claimed(val instance: HostInstance) : HostInstanceClaim

    /** Another host holds `instance.lock`; [broughtToFront] tells whether it answered the request to come to the front. */
    data class HeldByAnother(val broughtToFront: Boolean) : HostInstanceClaim
}
