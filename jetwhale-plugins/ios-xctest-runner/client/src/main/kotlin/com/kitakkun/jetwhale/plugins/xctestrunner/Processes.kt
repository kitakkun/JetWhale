package com.kitakkun.jetwhale.plugins.xctestrunner

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.net.ServerSocket
import kotlin.concurrent.thread

/** Starts external processes; tests replace it to run without xcodebuild or iproxy. */
internal fun interface ProcessLauncher {
    fun start(command: List<String>): Process
}

internal val SystemProcessLauncher = ProcessLauncher { command ->
    try {
        ProcessBuilder(command).start()
    } catch (e: IOException) {
        throw XcTestRunnerStartException("failed to launch '${command.first()}': ${e.message}", e)
    }
}

/** What a command printed, and how it exited. */
internal class CommandOutput(val exitCode: Int, val text: String)

/** Runs a command to completion and hands back what it printed, whatever its exit code. */
internal fun interface CommandOutputRunner {
    suspend fun run(command: List<String>): CommandOutput
}

internal val SystemCommandOutputRunner = CommandOutputRunner { command ->
    withContext(Dispatchers.IO) {
        val process = SystemProcessLauncher.start(command)
        coroutineScope {
            // Both pipes are read at once, since either one filling up would stall the process.
            val errorOutput = async { process.errorStream.bufferedReader().use { it.readText() } }
            val output = process.inputStream.bufferedReader().use { it.readText() }
            CommandOutput(exitCode = process.waitFor(), text = "$output\n${errorOutput.await()}")
        }
    }
}

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

/** How many of a process's last output lines are kept, to explain one that failed. */
private const val KEPT_OUTPUT_LINES = 200

/** How long to wait for an exited process's pipes to be read to the end. */
private const val DRAIN_TIMEOUT_MILLIS = 2_000L

/**
 * The last lines [process] printed. Both pipes are drained for the process's whole life, since a
 * full pipe would stall it.
 */
internal class KeptOutput(process: Process) {
    private val lines = ArrayDeque<String>()

    private val readerThreads = listOf(process.inputStream, process.errorStream).map { stream ->
        thread(isDaemon = true, name = "xctest-runner-output") { keepLastLinesOf(stream) }
    }

    /** What the process printed, once it has exited and its pipes have been read to the end. */
    fun textOnceDrained(): String {
        readerThreads.forEach { it.join(DRAIN_TIMEOUT_MILLIS) }
        return synchronized(lines) { lines.joinToString("\n") }
    }

    private fun keepLastLinesOf(stream: InputStream) {
        try {
            stream.bufferedReader().forEachLine { line ->
                synchronized(lines) {
                    lines.addLast(line)
                    if (lines.size > KEPT_OUTPUT_LINES) lines.removeFirst()
                }
            }
        } catch (_: IOException) {
            // The stream closed as the process ended; the lines read so far are kept.
        }
    }
}
