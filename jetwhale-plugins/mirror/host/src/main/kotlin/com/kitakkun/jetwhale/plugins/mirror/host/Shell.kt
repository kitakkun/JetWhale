package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.completeWith
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.time.Duration

/** A device-control operation failed or is not supported; [message] is shown to the user as is. */
internal class DeviceControlException(message: String, cause: Throwable?) : Exception(message, cause)

internal fun deviceControlError(message: String): DeviceControlException = DeviceControlException(message, null)

/** Starts external processes; tests replace it to run without adb, simctl or idb. */
internal fun interface ProcessLauncher {
    fun start(command: List<String>): Process
}

internal object SystemProcessLauncher : ProcessLauncher {
    /**
     * The PATH the started processes get, or null to leave them the host's. A tool can search PATH
     * itself: a pyenv or asdf shim looks there for what it runs, and idb for idb_companion.
     */
    @Volatile
    var launchedProcessPathVariable: String? = null

    override fun start(command: List<String>): Process = try {
        ProcessBuilder(if (runsOnWindows) command.take(1) + command.drop(1).map(::windowsCommandLineArgument) else command)
            .apply { launchedProcessPathVariable?.let { environment()["PATH"] = it } }
            .start()
    } catch (e: IOException) {
        throw DeviceControlException("failed to launch '${command.first()}': ${e.message}", e)
    }
}

internal val runsOnWindows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

/**
 * [argument] written so that a Windows program parsing its command line with the C runtime's
 * rules, as adb and ffmpeg do, reads it back unchanged.
 *
 * On Windows the JDK joins the arguments into one command line and passes an argument holding a
 * double quote as it is, so the program reads `\"` as an escaped quote and drops the backslash:
 * the text that `adb shell input text` gets for `say "hi"` would reach the device shell with bare
 * quotes, which it then removes. An argument with a quote is therefore quoted here, with each quote
 * and the backslashes before it escaped; the JDK passes an argument that is already quoted as it is.
 */
internal fun windowsCommandLineArgument(argument: String): String {
    if ('"' !in argument) return argument
    return buildString {
        append('"')
        var backslashes = 0
        for (character in argument) {
            when (character) {
                '\\' -> backslashes++

                '"' -> {
                    repeat(backslashes * 2 + 1) { append('\\') }
                    append('"')
                    backslashes = 0
                }

                else -> {
                    repeat(backslashes) { append('\\') }
                    append(character)
                    backslashes = 0
                }
            }
        }
        repeat(backslashes * 2) { append('\\') }
        append('"')
    }
}

internal class CommandResult(
    val exitCode: Int,
    val stdout: ByteArray,
    val stderr: String,
) {
    val stdoutText: String get() = stdout.decodeToString()
}

/**
 * Runs [command] to completion; stdout stays bytes because screenshots arrive on it as PNG.
 *
 * A command still running after [timeout] is ended and throws [DeviceControlException]; one whose
 * caller is cancelled is ended too. Ending a command asks its process to exit, and kills it if it has
 * not exited [COMMAND_EXIT_WAIT_MILLIS] later.
 */
internal suspend fun runCommand(timeout: Duration, vararg command: String): CommandResult = withContext(Dispatchers.IO) {
    val process = SystemProcessLauncher.start(command.toList())
    // Both pipes are read at once, since a process stalls once a pipe it writes to is full. A
    // process the command started can hold a pipe open after the command is ended, so the reads run
    // on threads that nothing waits for once the command is given up on.
    val stdout = readOnDaemonThread(process.inputStream) { it.readBytes() }
    val stderr = readOnDaemonThread(process.errorStream) { it.bufferedReader().readText() }
    try {
        withTimeoutOrNull(timeout) {
            CommandResult(exitCode = runInterruptible { process.waitFor() }, stdout = stdout.await(), stderr = stderr.await())
        } ?: throw deviceControlError("'${command.joinToString(" ")}' did not finish within $timeout")
    } finally {
        if (process.isAlive) {
            process.destroy()
            if (!process.waitFor(COMMAND_EXIT_WAIT_MILLIS, TimeUnit.MILLISECONDS)) process.destroyForcibly()
        }
    }
}

private fun <T> readOnDaemonThread(stream: InputStream, read: (InputStream) -> T): Deferred<T> {
    val result = CompletableDeferred<T>()
    thread(isDaemon = true, name = "mirror-command-output") { result.completeWith(runCatching { stream.use(read) }) }
    return result
}

private const val COMMAND_EXIT_WAIT_MILLIS = 2_000L

/** Runs [command] as [runCommand] does, throwing [DeviceControlException] with its output when it exits non-zero. */
internal suspend fun runCommandChecked(timeout: Duration, vararg command: String): CommandResult {
    val result = runCommand(timeout, *command)
    if (result.exitCode != 0) {
        val detail = result.stderr.ifBlank { result.stdoutText }.trim().take(500)
        throw deviceControlError("'${command.joinToString(" ")}' failed (exit ${result.exitCode}): $detail")
    }
    return result
}

/** The paths of the external tools this plugin drives; a missing tool is null. */
internal data class MirrorToolPaths(
    val adbPath: String?,
    val idbPath: String?,
    val idbCompanionPath: String?,
    val xcrunPath: String?,
    val ffmpegPath: String?,
)

/** The tools [MirrorToolLocator] found, and the directories it searched for them, in the order it searched them. */
internal data class MirrorToolSearchResult(
    val toolPaths: MirrorToolPaths,
    val searchedDirectories: List<String>,
)

/**
 * Finds the tools this plugin drives: adb first in the Android SDKs at [androidSdkDirectories], then
 * each tool on [hostPathVariable], this process's own PATH, then in [wellKnownDirectories]. Only when
 * one of them is still missing is the PATH that [loginShellPathVariableResolver] reads searched after
 * those: starting the login shell runs the user's startup files, which can take seconds, and on macOS
 * asks the user for access when those files live in a protected folder such as Documents.
 */
internal class MirrorToolLocator(
    private val hostPathVariable: String?,
    private val wellKnownDirectories: List<String>,
    private val androidSdkDirectories: List<String>,
    private val loginShellPathVariableResolver: LoginShellPathVariableResolver?,
) {
    fun locateTools(): MirrorToolSearchResult {
        val directories = (directoriesOn(hostPathVariable) + wellKnownDirectories).distinct()
        val toolPaths = findToolPathsIn(directories)
        val isAnySearchedToolMissing = listOf(toolPaths.adbPath, toolPaths.idbPath, toolPaths.idbCompanionPath, toolPaths.ffmpegPath).any {
            it ==
                null
        }
        val loginShellPathVariable = if (isAnySearchedToolMissing) loginShellPathVariableResolver?.resolveLoginShellPathVariable() else null
        if (loginShellPathVariable == null) return MirrorToolSearchResult(toolPaths, directories)
        val directoriesWithLoginShell = (directories + directoriesOn(loginShellPathVariable)).distinct()
        return MirrorToolSearchResult(findToolPathsIn(directoriesWithLoginShell), directoriesWithLoginShell)
    }

    private fun findToolPathsIn(directories: List<String>): MirrorToolPaths {
        val adbFileName = if (runsOnWindows) "adb.exe" else "adb"
        return MirrorToolPaths(
            adbPath =
            findToolPath(adbFileName, androidSdkDirectories.map { "$it/platform-tools" }) ?: findToolPath(adbFileName, directories),
            idbPath = findToolPath("idb", directories),
            idbCompanionPath = findToolPath("idb_companion", directories),
            xcrunPath = "/usr/bin/xcrun".takeIf(::isExecutable),
            ffmpegPath = findToolPath(if (runsOnWindows) "ffmpeg.exe" else "ffmpeg", directories),
        )
    }

    private fun directoriesOn(pathVariable: String?): List<String> = pathVariable?.split(
        File.pathSeparator,
    ).orEmpty().filter(String::isNotEmpty)
}

/** The path of the first executable file named [name] in [directories], or null. */
@VisibleForTesting
internal fun findToolPath(name: String, directories: List<String>): String? = directories.map { "$it/$name" }.firstOrNull(::isExecutable)

private fun isExecutable(path: String): Boolean = File(path).let { it.isFile && it.canExecute() }
