package com.kitakkun.jetwhale.host.data.util

import com.kitakkun.jetwhale.host.model.HostOs
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import java.io.File
import kotlin.time.Duration.Companion.seconds

/**
 * Finds the adb executable from what a process sees: its environment, its home directory, its OS,
 * the directories adb is commonly installed in and, as a last resort, the PATH its user's login shell
 * sets up.
 *
 * `ANDROID_HOME` and `ANDROID_SDK_ROOT` are consulted first: someone who has set them has said which
 * SDK they mean, and a stray `/usr/local/bin/adb` from an unrelated install should not outrank that.
 * Then come the conventional SDK locations per OS (Windows keeps the SDK under LOCALAPPDATA and names
 * the executable `adb.exe`), then each entry of this process's own `PATH`, then [wellKnownDirectories].
 * Only when none of them holds adb is the PATH that [loginShellPathVariableResolver] reads searched:
 * starting the login shell runs the user's startup files, which can take seconds, and on macOS asks
 * the user for access when those files live in a protected folder such as Documents.
 *
 * @property wellKnownDirectories directories searched whether or not a `PATH` lists them.
 * @property loginShellPathVariableResolver reads the login shell's PATH on the first [find] that does
 * not find adb elsewhere, and only then; null skips it.
 */
class AdbLocator(
    environment: Map<String, String>,
    private val userHome: String?,
    private val isWindows: Boolean,
    private val wellKnownDirectories: List<String>,
    private val loginShellPathVariableResolver: LoginShellPathVariableResolver?,
) {
    // Windows matches variable names case-insensitively, but System.getenv() keeps their spelling
    // (usually `Path`) in a map that does not.
    private val environment: Map<String, String> = if (isWindows) environment.toSortedMap(String.CASE_INSENSITIVE_ORDER) else environment

    private val loginShellPathVariable: String? by lazy { loginShellPathVariableResolver?.resolveLoginShellPathVariable() }

    val executableName: String = if (isWindows) "adb.exe" else "adb"

    /**
     * The adb executable, or null when no SDK location, `PATH` entry or well-known directory holds
     * one. The first call that has to read the login shell's PATH may wait for the shell for as long
     * as its resolver allows.
     */
    fun find(): File? {
        val sdkRoots = listOfNotNull(
            environment["ANDROID_HOME"],
            environment["ANDROID_SDK_ROOT"],
            userHome?.let { "$it/Android/Sdk" },
            userHome?.let { "$it/Library/Android/sdk" },
            environment["LOCALAPPDATA"]?.let { "$it/Android/Sdk" },
        )
        return findAdbIn(sdkRoots.map { "$it/platform-tools" } + directoriesOn(environment["PATH"]) + wellKnownDirectories)
            ?: loginShellPathVariable?.let { findAdbIn(directoriesOn(it)) }
    }

    private fun directoriesOn(pathVariable: String?): List<String> = pathVariable?.split(if (isWindows) ';' else ':').orEmpty().filter(String::isNotBlank)

    private fun findAdbIn(directories: List<String>): File? = directories.map { File(it, executableName) }.firstOrNull { it.isFile && it.canExecute() }

    companion object {
        fun ofCurrentProcess(): AdbLocator {
            val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
            return AdbLocator(
                environment = System.getenv(),
                userHome = System.getProperty("user.home"),
                isWindows = isWindows,
                // Homebrew's android-platform-tools cask links adb into its prefix: /opt/homebrew
                // on Apple silicon, /usr/local on Intel Macs.
                wellKnownDirectories = if (isWindows) emptyList() else listOf("/opt/homebrew/bin", "/usr/local/bin"),
                // A Windows app gets the user's PATH however it is started.
                loginShellPathVariableResolver = if (isWindows) {
                    null
                } else {
                    LoginShellPathVariableResolver(
                        shellPath = System.getenv("SHELL")?.takeIf(String::isNotBlank) ?: if (HostOs.current == HostOs.MAC) "/bin/zsh" else "/bin/sh",
                        timeout = 5.seconds,
                    )
                },
            )
        }
    }
}

@ContributesTo(AppScope::class)
interface AdbLocatorProvider {
    @Provides
    @SingleIn(AppScope::class)
    fun provideAdbLocator(): AdbLocator = AdbLocator.ofCurrentProcess()
}
