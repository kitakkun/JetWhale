package com.kitakkun.jetwhale.plugins.mirror.host

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
        ProcessBuilder(command).start()
    } catch (e: IOException) {
        throw DeviceControlException("failed to launch '${command.first()}': ${e.message}", e)
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
    val stderr = async { process.errorStream.bufferedReader().readText() }
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

/** The external tools this plugin drives, located once; a missing one is null. */
internal class MirrorTools(
    val adb: String?,
    val idb: String?,
    val idbCompanion: String?,
    val xcrun: String?,
) {
    companion object {
        fun locate(): MirrorTools {
            val home = System.getProperty("user.home")
            val adb = if (System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)) "adb.exe" else "adb"
            val sdkDirectories = listOfNotNull(
                System.getenv("ANDROID_HOME"),
                System.getenv("ANDROID_SDK_ROOT"),
                "$home/Library/Android/sdk",
                "$home/Android/Sdk",
                System.getenv("LOCALAPPDATA")?.let { "$it/Android/Sdk" },
            )
            return MirrorTools(
                adb = sdkDirectories.map { "$it/platform-tools/$adb" }.firstOrNull(::isExecutable) ?: onPath(adb),
                idb = onPath("idb"),
                idbCompanion = onPath("idb_companion"),
                xcrun = "/usr/bin/xcrun".takeIf(::isExecutable),
            )
        }
    }
}

// A GUI app on macOS does not inherit the login shell's PATH, so Homebrew's directories are
// searched even when PATH lacks them.
private fun onPath(name: String): String? {
    val directories = System.getenv("PATH").orEmpty().split(File.pathSeparator) + listOf("/opt/homebrew/bin", "/usr/local/bin")
    return directories.map { "$it/$name" }.firstOrNull(::isExecutable)
}

private fun isExecutable(path: String): Boolean = File(path).let { it.isFile && it.canExecute() }
