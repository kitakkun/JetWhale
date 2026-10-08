package com.kitakkun.jetwhale.plugins.mirror.host

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
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

/** How long a cleanup that cannot wait, such as the host's exit, waits for idb to forget companions. */
private const val DISCONNECT_DEADLINE_MILLIS = 3_000L

/**
 * The idb companions of iOS devices and simulators: a companion process per device, which idb is
 * then told about. A device needs one started for it; for a simulator, idb would start one itself,
 * but would never stop it. Each companion is started by the first user. When the last one releases
 * it, it is kept for [idleTimeout] before it stops: starting one and connecting idb takes seconds,
 * and switching away from a device and back is the common case. A device or simulator that
 * disappears has its companion stopped at once.
 */
internal class IdbCompanions(
    private val idbCompanionPath: String,
    private val idbPath: String,
    private val launcher: ProcessLauncher,
    private val commands: CommandRunner,
    private val ports: PortSource,
    private val idleTimeout: Duration,
    private val scope: CoroutineScope,
) {
    private class RunningCompanion(val process: Process, val port: Int, var users: Int) {
        var idleStop: Job? = null
    }

    private val mutex = Mutex()
    private val running = ConcurrentHashMap<String, RunningCompanion>()

    /** Starts the companion of [udid] if it is not running yet; pair every call with [release]. */
    suspend fun acquire(udid: String): Unit = mutex.withLock {
        running[udid]?.let {
            it.idleStop?.cancel()
            it.idleStop = null
            it.users++
            return@withLock
        }
        val port = ports.freePort()
        val process = launcher.start(listOf(idbCompanionPath, "--udid", udid, "--grpc-port", "$port"))
        // Until it is recorded below, nothing else stops this companion, not even the host's exit, so
        // any failure ends it here, a cancelled caller's included. idb may have recorded it already.
        var recorded = false
        var connectStarted = false
        try {
            awaitReady(process)
            connectStarted = true
            commands.runChecked(listOf(idbPath, "connect", "localhost", "$port"))
            running[udid] = RunningCompanion(process, port, users = 1)
            recorded = true
        } finally {
            if (!recorded) {
                process.destroyForcibly()
                if (connectStarted) withContext(NonCancellable + Dispatchers.IO) { disconnectIdbWithDeadline(listOf(port)) }
            }
        }
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
    suspend fun stopCompanionEvenIfInUse(udid: String): Unit = mutex.withLock {
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

    /** Kills every companion at once and tells idb they are gone; for when the host exits. */
    fun destroyAllNow() {
        running.values.forEach { it.process.destroyForcibly() }
        // idb records a companion it was told about in state that every idb client shares, and an
        // `idb --udid` call for that device or simulator fails on the dead one until it is disconnected.
        disconnectIdbWithDeadline(running.values.map(RunningCompanion::port))
    }

    /**
     * Tells idb to forget the companions at [companionPorts], ending any disconnect still running
     * after [DISCONNECT_DEADLINE_MILLIS].
     */
    private fun disconnectIdbWithDeadline(companionPorts: List<Int>) {
        val disconnectProcesses = companionPorts.mapNotNull { port ->
            try {
                launcher.start(listOf(idbPath, "disconnect", "localhost", "$port"))
            } catch (_: DeviceControlException) {
                null
            }
        }
        val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(DISCONNECT_DEADLINE_MILLIS)
        disconnectProcesses.forEach { disconnectProcess ->
            if (!disconnectProcess.waitFor(maxOf(deadlineNanos - System.nanoTime(), 0), TimeUnit.NANOSECONDS)) disconnectProcess.destroyForcibly()
        }
    }

    private suspend fun stop(companion: RunningCompanion) {
        companion.process.destroy()
        if (!companion.process.waitFor(3, TimeUnit.SECONDS)) companion.process.destroyForcibly()
        disconnectIdb(companion.port)
    }

    private suspend fun disconnectIdb(port: Int) {
        try {
            commands.runChecked(listOf(idbPath, "disconnect", "localhost", "$port"))
        } catch (_: DeviceControlException) {
            // A failed disconnect leaves only a stale entry in idb's target list.
        }
    }

    // The companion keeps logging for as long as it runs, so both pipes are drained for its whole
    // life, not just until it is ready: a full pipe would stall it.
    private suspend fun awaitReady(process: Process) {
        val ready = CompletableDeferred<Unit>()
        val output = StringBuilder()
        listOf(process.inputStream, process.errorStream).forEach { stream -> drain(stream, output, ready) }
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
