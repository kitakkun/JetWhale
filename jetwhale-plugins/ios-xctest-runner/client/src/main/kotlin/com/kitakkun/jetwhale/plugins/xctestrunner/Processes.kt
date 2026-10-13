package com.kitakkun.jetwhale.plugins.xctestrunner

import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.io.InputStream
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.time.Duration

/** Starts external processes; tests replace it to run without xcodebuild or iproxy. */
internal fun interface ProcessLauncher {
    fun start(command: List<String>): Process
}

internal val SystemProcessLauncher = ProcessLauncher { command ->
    try {
        ProcessBuilder(command).setUtf8LangIfUnset().start()
    } catch (e: IOException) {
        throw XcTestRunnerStartException("failed to launch '${command.first()}': ${e.message}", e)
    }
}

/**
 * Sets the process's `LANG` to UTF-8 when it has none, as when the host is started from Finder.
 * Without a locale, devicectl, and xcodebuild where it relays CoreDevice's errors, write every
 * non-ASCII character as '?'. A `LANG` the user set is kept, and an `LC_` variable they set still
 * overrides it.
 */
@VisibleForTesting
internal fun ProcessBuilder.setUtf8LangIfUnset(): ProcessBuilder = apply { environment().putIfAbsent("LANG", "en_US.UTF-8") }

/** What a command printed, and how it exited. */
internal class CommandOutput(val exitCode: Int, val text: String)

/** Runs a command to completion and hands back what it printed, whatever its exit code. */
internal fun interface CommandOutputRunner {
    suspend fun run(command: List<String>): CommandOutput
}

/**
 * Runs commands as processes started by [processLauncher]. One that has not finished within
 * [timeout], or whose caller is cancelled, is ended, so that a build waiting on something that never
 * comes does not hold its lock and its caller forever. Ending a command asks its process to exit, and
 * kills it if it has not exited [COMMAND_EXIT_WAIT_MILLIS] later. Its output is read on threads of
 * its own, so a child process that keeps a pipe open after it ends does not hold them either.
 */
internal class ProcessCommandOutputRunner(
    private val processLauncher: ProcessLauncher,
    private val timeout: Duration,
) : CommandOutputRunner {
    override suspend fun run(command: List<String>): CommandOutput = withContext(Dispatchers.IO) {
        val process = processLauncher.start(command)
        val output = KeptOutput(process, maxLines = Int.MAX_VALUE)
        try {
            val exitedProcess = withTimeoutOrNull(timeout) { process.onExit().await() }
                ?: throw XcTestRunnerStartException("'${command.take(3).joinToString(" ")}' did not finish within $timeout, so it was ended", null)
            CommandOutput(exitCode = exitedProcess.exitValue(), text = output.textOnceDrained())
        } finally {
            if (process.isAlive) {
                process.destroy()
                if (!process.waitFor(COMMAND_EXIT_WAIT_MILLIS, TimeUnit.MILLISECONDS)) process.destroyForcibly()
            }
        }
    }
}

private const val COMMAND_EXIT_WAIT_MILLIS = 2_000L

/** Hands out a TCP port nothing listens on yet. */
internal fun interface PortSource {
    fun freePort(): Int
}

internal val LocalPorts = PortSource { ServerSocket(0).use(ServerSocket::getLocalPort) }

/** The processes on this machine, by pid; tests replace it to stand in for runners started elsewhere. */
internal interface ProcessTable {
    fun isAlive(pid: Long): Boolean

    /** Asks the process to end. */
    fun terminate(pid: Long)
}

internal object SystemProcessTable : ProcessTable {
    override fun isAlive(pid: Long): Boolean = ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)

    override fun terminate(pid: Long) {
        ProcessHandle.of(pid).ifPresent(ProcessHandle::destroy)
    }
}

/** How many of a running process's last output lines are kept, to explain one that failed. */
internal const val KEPT_OUTPUT_LINES = 200

/** How long to wait for an exited process's pipes to be read to the end. */
private const val DRAIN_TIMEOUT_MILLIS = 2_000L

/**
 * The last [maxLines] lines [process] printed. Both pipes are drained for the process's whole life,
 * since a full pipe would stall it.
 */
internal class KeptOutput(process: Process, private val maxLines: Int) {
    private val lines = ArrayDeque<String>()

    private val readerThreads = listOf(process.inputStream, process.errorStream).map { stream ->
        thread(start = false, isDaemon = true, name = "xctest-runner-output") { keepLastLinesOf(stream) }
    }

    init {
        readerThreads.forEach(Thread::start)
    }

    /**
     * What the process printed, once it has exited and its pipes have been read to the end, or once
     * [DRAIN_TIMEOUT_MILLIS] has passed: a child process that inherited a pipe keeps it open.
     */
    fun textOnceDrained(): String {
        readerThreads.forEach { it.join(DRAIN_TIMEOUT_MILLIS) }
        return synchronized(lines) { lines.joinToString("\n") }
    }

    private fun keepLastLinesOf(stream: InputStream) {
        try {
            stream.bufferedReader().forEachLine { line ->
                synchronized(lines) {
                    lines.addLast(line)
                    if (lines.size > maxLines) lines.removeFirst()
                }
            }
        } catch (_: IOException) {
            // Destroying the process closes its streams under this reader.
        }
    }
}
