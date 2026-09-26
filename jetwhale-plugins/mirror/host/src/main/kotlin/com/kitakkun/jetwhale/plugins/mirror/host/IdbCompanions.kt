package com.kitakkun.jetwhale.plugins.mirror.host

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.InputStream
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.time.Duration

/** How long a companion gets to report its port; a device that needs pairing never does. */
private const val COMPANION_START_TIMEOUT_MILLIS = 20_000L

/**
 * The idb companions of physical iOS devices. idb reaches a simulator by itself, but a device needs
 * a companion process of its own, which idb is then told about. Each device's companion is started
 * by the first user. When the last one releases it, it is kept for [idleTimeout] before it stops:
 * starting one and connecting idb takes seconds, and switching away from a device and back is the
 * common case. A device that disappears has its companion stopped at once.
 */
internal class IdbCompanions(
    private val idbCompanion: String,
    private val idb: String,
    private val launcher: ProcessLauncher,
    private val commands: CommandRunner,
    private val ports: PortSource,
    private val idleTimeout: Duration,
    private val scope: CoroutineScope,
) {
    private class Running(val process: Process, val port: Int, var users: Int) {
        var idleStop: Job? = null
    }

    private val mutex = Mutex()
    private val running = ConcurrentHashMap<String, Running>()

    /** Starts the companion of [udid] if it is not running yet; pair every call with [release]. */
    suspend fun acquire(udid: String): Unit = mutex.withLock {
        running[udid]?.let {
            it.idleStop?.cancel()
            it.idleStop = null
            it.users++
            return@withLock
        }
        val port = ports.freePort()
        val process = launcher.start(listOf(idbCompanion, "--udid", udid, "--grpc-port", "$port"))
        try {
            awaitReady(process)
            commands.runChecked(listOf(idb, "connect", "localhost", "$port"))
        } catch (e: DeviceControlException) {
            process.destroyForcibly()
            throw e
        }
        running[udid] = Running(process, port, users = 1)
    }

    suspend fun release(udid: String): Unit = mutex.withLock {
        val companion = running[udid] ?: return@withLock
        companion.users--
        if (companion.users > 0) return@withLock
        companion.idleStop = scope.launch {
            delay(idleTimeout)
            mutex.withLock {
                if (companion.users > 0 || running[udid] !== companion) return@withLock
                running.remove(udid)
                stop(companion)
            }
        }
    }

    /** Stops the companion of a device that is gone, whoever still uses it: it has nothing left to reach. */
    suspend fun forget(udid: String): Unit = mutex.withLock {
        val companion = running.remove(udid) ?: return@withLock
        companion.idleStop?.cancel()
        stop(companion)
    }

    /** Stops every companion, whoever still uses it; for when the plugin goes away. */
    suspend fun releaseAll(): Unit = mutex.withLock {
        running.values.forEach {
            it.idleStop?.cancel()
            stop(it)
        }
        running.clear()
    }

    fun isRunning(udid: String): Boolean = running.containsKey(udid)

    /** Kills every companion at once, without waiting for anything; for when the host exits. */
    fun destroyAllNow() {
        running.values.forEach { it.process.destroyForcibly() }
    }

    private suspend fun stop(companion: Running) {
        companion.process.destroy()
        if (!companion.process.waitFor(3, TimeUnit.SECONDS)) companion.process.destroyForcibly()
        // Best effort: a failed disconnect leaves a stale entry in idb's target list and nothing more.
        try {
            commands.runChecked(listOf(idb, "disconnect", "localhost", "${companion.port}"))
        } catch (_: DeviceControlException) {
        }
    }

    // The companion keeps logging for as long as it runs, so both pipes are drained for its whole
    // life, not just until it is ready: a full pipe would stall it.
    private suspend fun awaitReady(process: Process) {
        val ready = CompletableDeferred<Unit>()
        val output = StringBuilder()
        listOf(process.inputStream, process.errorStream).forEach { stream -> drain(stream, output, ready) }
        // The companion is another process, so its time is real time, whatever clock the caller runs on.
        try {
            withContext(Dispatchers.IO) { withTimeout(COMPANION_START_TIMEOUT_MILLIS) { ready.await() } }
        } catch (e: TimeoutCancellationException) {
            throw DeviceControlException("idb_companion did not start: ${output.lines().takeLast(5).joinToString(" / ")}", e)
        }
    }

    private fun drain(stream: InputStream, output: StringBuilder, ready: CompletableDeferred<Unit>) {
        thread(isDaemon = true, name = "idb-companion-output") {
            stream.bufferedReader().forEachLine { line ->
                synchronized(output) { output.appendLine(line) }
                if ("\"grpc_port\"" in line) ready.complete(Unit)
            }
        }
    }
}

/** Runs a command to completion, throwing [DeviceControlException] when it fails. */
internal fun interface CommandRunner {
    suspend fun runChecked(command: List<String>)
}

/** Hands out a TCP port nothing listens on yet. */
internal fun interface PortSource {
    fun freePort(): Int
}

internal val LocalPorts = PortSource { ServerSocket(0).use(ServerSocket::getLocalPort) }
