package com.kitakkun.jetwhale.host.data.util

import java.io.File

/**
 * Finds the adb executable from what a process sees: its environment, its home directory and its OS.
 *
 * `ANDROID_HOME` and `ANDROID_SDK_ROOT` are consulted first: someone who has set them has said which
 * SDK they mean, and a stray `/usr/local/bin/adb` from an unrelated install should not outrank that.
 * Then come the conventional SDK locations per OS (Windows keeps the SDK under LOCALAPPDATA and names
 * the executable `adb.exe`), then [fixedDirectories], then each `PATH` entry.
 *
 * @property fixedDirectories directories searched whether or not `PATH` lists them, because an app
 * started from the macOS Dock does not inherit the shell's `PATH`.
 */
internal class AdbLocator(
    private val environment: Map<String, String>,
    private val userHome: String?,
    private val isWindows: Boolean,
    private val fixedDirectories: List<String>,
) {
    val executableName: String = if (isWindows) "adb.exe" else "adb"

    /** The adb executable, or null when no SDK location, fixed directory or `PATH` entry holds one. */
    fun find(): File? {
        val sdkRoots = listOfNotNull(
            environment["ANDROID_HOME"],
            environment["ANDROID_SDK_ROOT"],
            userHome?.let { "$it/Android/Sdk" },
            userHome?.let { "$it/Library/Android/sdk" },
            environment["LOCALAPPDATA"]?.let { "$it/Android/Sdk" },
        )
        val pathDirectories = environment["PATH"].orEmpty().split(if (isWindows) ';' else ':').filter(String::isNotBlank)
        return (sdkRoots.map { "$it/platform-tools" } + fixedDirectories + pathDirectories)
            .map { File(it, executableName) }
            .firstOrNull { it.isFile && it.canExecute() }
    }

    companion object {
        fun ofCurrentProcess(): AdbLocator {
            val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
            return AdbLocator(
                environment = System.getenv(),
                userHome = System.getProperty("user.home"),
                isWindows = isWindows,
                fixedDirectories = if (isWindows) emptyList() else listOf("/usr/bin", "/usr/local/bin", "/opt/homebrew/bin"),
            )
        }
    }
}
