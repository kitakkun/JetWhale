package com.kitakkun.jetwhale.plugins.mirror.host

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.io.File

/** A device-control operation failed or is not supported; [message] is caller-facing. */
class DeviceControlException(message: String, cause: Throwable? = null) : Exception(message, cause)

internal class CommandResult(
    val exitCode: Int,
    val stdout: ByteArray,
    val stderr: String,
) {
    val stdoutText: String get() = stdout.decodeToString()
}

/**
 * Runs an external command and captures stdout as raw bytes (screenshots arrive as binary PNG
 * data on stdout) and stderr as text.
 */
internal suspend fun runCommand(vararg command: String): CommandResult = withContext(Dispatchers.IO) {
    val process = try {
        ProcessBuilder(*command).start()
    } catch (e: java.io.IOException) {
        throw DeviceControlException("failed to launch '${command.first()}': ${e.message}", e)
    }
    // Drain stderr concurrently so neither pipe can fill up and deadlock the process.
    val stderr = async { process.errorStream.bufferedReader().readText() }
    val stdout = process.inputStream.readBytes()
    val exitCode = process.waitFor()
    CommandResult(exitCode = exitCode, stdout = stdout, stderr = stderr.await())
}

/** Runs [command] and returns stdout text, throwing [DeviceControlException] on a non-zero exit. */
internal suspend fun runCommandChecked(vararg command: String): CommandResult {
    val result = runCommand(*command)
    if (result.exitCode != 0) {
        val detail = result.stderr.ifBlank { result.stdoutText }.trim().take(500)
        throw DeviceControlException("'${command.joinToString(" ")}' failed (exit ${result.exitCode}): $detail")
    }
    return result
}

/**
 * Finds the absolute path to the adb executable by checking common installation locations,
 * falling back to "adb" on the PATH.
 */
internal fun findAdbPath(): String {
    val homeDir = System.getProperty("user.home")
    val androidHome = System.getenv("ANDROID_HOME")
    val androidSdkRoot = System.getenv("ANDROID_SDK_ROOT")

    val candidatePaths = listOfNotNull(
        "/usr/bin/adb",
        "/usr/local/bin/adb",
        homeDir?.let { "$it/Android/Sdk/platform-tools/adb" },
        homeDir?.let { "$it/Library/Android/sdk/platform-tools/adb" },
        androidHome?.let { "$it/platform-tools/adb" },
        androidSdkRoot?.let { "$it/platform-tools/adb" },
    )

    return candidatePaths.firstOrNull { path ->
        File(path).exists() && File(path).canExecute()
    } ?: "adb"
}

/** Finds the idb executable (used to drive iOS simulator input), or null when not installed. */
internal fun findIdbPath(): String? {
    val candidatePaths = listOf(
        "/usr/local/bin/idb",
        "/opt/homebrew/bin/idb",
    )
    candidatePaths.firstOrNull { File(it).canExecute() }?.let { return it }
    // Fall back to a PATH lookup.
    val pathDirs = System.getenv("PATH")?.split(File.pathSeparator).orEmpty()
    return pathDirs.map { File(it, "idb") }.firstOrNull { it.canExecute() }?.absolutePath
}
