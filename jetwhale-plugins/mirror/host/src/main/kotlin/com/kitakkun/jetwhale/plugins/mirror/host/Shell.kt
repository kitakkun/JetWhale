package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** A device-control operation failed or is not supported; [message] is shown to the user as is. */
internal class DeviceControlException(message: String, cause: Throwable?) : Exception(message, cause)

internal fun deviceControlError(message: String): DeviceControlException = DeviceControlException(message, null)

/** Starts external processes; tests replace it to run without adb, simctl or idb. */
internal fun interface ProcessLauncher {
    fun start(command: List<String>): Process
}

internal val SystemProcessLauncher = ProcessLauncher { command ->
    try {
        ProcessBuilder(if (runsOnWindows) command.take(1) + command.drop(1).map(::windowsCommandLineArgument) else command).start()
    } catch (e: IOException) {
        throw DeviceControlException("failed to launch '${command.first()}': ${e.message}", e)
    }
}

private val runsOnWindows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

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

/** Runs [command] to completion; stdout stays bytes because screenshots arrive on it as PNG. */
internal suspend fun runCommand(vararg command: String): CommandResult = withContext(Dispatchers.IO) {
    val process = SystemProcessLauncher.start(command.toList())
    // Both pipes are drained at once so neither can fill up and stall the process.
    val stderr = async { process.errorStream.bufferedReader().use { it.readText() } }
    val stdout = process.inputStream.readBytes()
    CommandResult(exitCode = process.waitFor(), stdout = stdout, stderr = stderr.await())
}

/** Runs [command], throwing [DeviceControlException] with its output when it exits non-zero. */
internal suspend fun runCommandChecked(vararg command: String): CommandResult {
    val result = runCommand(*command)
    if (result.exitCode != 0) {
        val detail = result.stderr.ifBlank { result.stdoutText }.trim().take(500)
        throw deviceControlError("'${command.joinToString(" ")}' failed (exit ${result.exitCode}): $detail")
    }
    return result
}

/** The paths of the external tools this plugin drives, located once; a missing tool is null. */
internal class MirrorToolPaths(
    val adbPath: String?,
    val idbPath: String?,
    val idbCompanionPath: String?,
    val xcrunPath: String?,
    val ffmpegPath: String?,
) {
    companion object {
        fun locate(): MirrorToolPaths {
            val home = System.getProperty("user.home")
            val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
            val adbFileName = if (isWindows) "adb.exe" else "adb"
            val sdkDirectories = listOfNotNull(
                System.getenv("ANDROID_HOME"),
                System.getenv("ANDROID_SDK_ROOT"),
                "$home/Library/Android/sdk",
                "$home/Android/Sdk",
                System.getenv("LOCALAPPDATA")?.let { "$it/Android/Sdk" },
            )
            val searchDirectories = toolDirectories(System.getenv("PATH"))
            return MirrorToolPaths(
                adbPath = findToolPath(adbFileName, sdkDirectories.map { "$it/platform-tools" }) ?: findToolPath(adbFileName, searchDirectories),
                idbPath = findToolPath("idb", searchDirectories),
                idbCompanionPath = findToolPath("idb_companion", searchDirectories),
                xcrunPath = "/usr/bin/xcrun".takeIf(::isExecutable),
                ffmpegPath = findToolPath(if (isWindows) "ffmpeg.exe" else "ffmpeg", searchDirectories),
            )
        }
    }
}

/**
 * The directories searched for a tool: those on [pathVariable] (the PATH environment variable),
 * then Homebrew's. A GUI app on macOS does not inherit the login shell's PATH, so Homebrew's
 * directories are searched even when PATH lacks them.
 */
@VisibleForTesting
internal fun toolDirectories(pathVariable: String?): List<String> = pathVariable.orEmpty().split(File.pathSeparator).filter(String::isNotEmpty) + listOf("/opt/homebrew/bin", "/usr/local/bin")

/** The path of the first executable file named [name] in [directories], or null. */
@VisibleForTesting
internal fun findToolPath(name: String, directories: List<String>): String? = directories.map { "$it/$name" }.firstOrNull(::isExecutable)

private fun isExecutable(path: String): Boolean = File(path).let { it.isFile && it.canExecute() }
