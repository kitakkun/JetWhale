package com.kitakkun.jetwhale.host.instance

import com.kitakkun.jetwhale.host.release.HeldLock
import com.kitakkun.jetwhale.host.release.HostInstanceRecord
import com.kitakkun.jetwhale.host.release.HostVersionsDirectory
import com.kitakkun.jetwhale.host.release.LockFiles
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.seconds

/**
 * A host the launcher started, as the one host of its app data directory. It holds `instance.lock`
 * from its start, and takes requests to bring its window forward on a loopback port that it
 * publishes with a token in `instance.json` once it is up.
 */
class HostInstance private constructor(
    private val versions: HostVersionsDirectory,
    private val instanceLock: HeldLock,
    private val server: ServerSocket,
    private val token: String,
) {
    private val mutableActivationRequests = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** A request to bring the window forward, from a launcher or from a second host. */
    val activationRequests: Flow<Unit> = mutableActivationRequests

    /**
     * Publishes the record that marks this host as up: once its main window shows, or, with
     * `--headless`, once its servers are bound. The launcher judges the start by it, and reaches the
     * host through it.
     */
    fun publish() {
        HostInstanceRecord.publish(
            versions,
            HostInstanceRecord(port = server.localPort, pid = ProcessHandle.current().pid(), token = token),
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(HostInstance::class.java)

        /**
         * Takes `instance.lock` for this process and starts taking requests; [publish] makes them
         * reachable. When another host holds the lock, asks that one to bring its window forward
         * instead.
         */
        fun claim(versions: HostVersionsDirectory, locks: LockFiles): HostInstanceClaim {
            val lock = locks.tryLock(versions.instanceLock)
                ?: return HostInstanceClaim.HeldByAnother(
                    activated = HostInstanceRecord.requestActivation(versions, timeout = 5.seconds),
                )
            val server = ServerSocket(0, 8, InetAddress.getLoopbackAddress())
            val token = ByteArray(16).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
            val instance = HostInstance(versions, lock, server, token)
            instance.serve()
            return HostInstanceClaim.Claimed(instance)
        }
    }

    private fun serve() {
        thread(isDaemon = true, name = "host-instance-activation") {
            try {
                while (!server.isClosed) {
                    val connection = try {
                        server.accept()
                    } catch (_: IOException) {
                        break
                    }
                    connection.use(::answer)
                }
            } finally {
                instanceLock.close()
            }
        }
    }

    private fun answer(connection: Socket) {
        try {
            connection.soTimeout = 2_000
            val request = connection.getInputStream().bufferedReader().readLine()
            val accepted = request == "${HostInstanceRecord.ACTIVATE_REQUEST} $token"
            connection.getOutputStream().write("${if (accepted) HostInstanceRecord.ACCEPTED_RESPONSE else "denied"}\n".toByteArray())
            if (accepted) mutableActivationRequests.tryEmit(Unit)
        } catch (e: IOException) {
            logger.warn("An activation request could not be read", e)
        }
    }
}

sealed interface HostInstanceClaim {
    data class Claimed(val instance: HostInstance) : HostInstanceClaim

    /** Another host holds `instance.lock`; [activated] tells whether it answered the request to come forward. */
    data class HeldByAnother(val activated: Boolean) : HostInstanceClaim
}
