package com.kitakkun.jetwhale.host.data.util

import com.kitakkun.jetwhale.host.model.HostOs
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import java.io.File
import kotlin.time.Duration.Companion.seconds

/**
 * Finds the adb executable from what a process sees: its environment, its home directory, its OS and
 * the PATH its user's login shell sets up.
 *
 * `ANDROID_HOME` and `ANDROID_SDK_ROOT` are consulted first: someone who has set them has said which
 * SDK they mean, and a stray `/usr/local/bin/adb` from an unrelated install should not outrank that.
 * Then come the conventional SDK locations per OS (Windows keeps the SDK under LOCALAPPDATA and names
 * the executable `adb.exe`), then each entry of the PATH that [loginShellPathVariableResolver] reads,
 * then each entry of this process's own `PATH`, then [fixedDirectories].
 *
 * @property loginShellPathVariableResolver reads the login shell's PATH on the first [find], and only
 * then; null skips it.
 * @property fixedDirectories directories searched whether or not a `PATH` lists them, for when the
 * login shell's PATH cannot be read.
 */
class AdbLocator(
    environment: Map<String, String>,
    private val userHome: String?,
    private val isWindows: Boolean,
    private val loginShellPathVariableResolver: LoginShellPathVariableResolver?,
    private val fixedDirectories: List<String>,
) {
    // Windows matches variable names case-insensitively, but System.getenv() keeps their spelling
    // (usually `Path`) in a map that does not.
    private val environment: Map<String, String> = if (isWindows) environment.toSortedMap(String.CASE_INSENSITIVE_ORDER) else environment

    private val loginShellPathVariable: String? by lazy { loginShellPathVariableResolver?.resolveLoginShellPathVariable() }

    val executableName: String = if (isWindows) "adb.exe" else "adb"

    /**
     * The adb executable, or null when no SDK location, `PATH` entry or fixed directory holds one.
     * The first call may wait for the login shell for as long as its resolver allows.
     */
    fun find(): File? {
        val sdkRoots = listOfNotNull(
            environment["ANDROID_HOME"],
            environment["ANDROID_SDK_ROOT"],
            userHome?.let { "$it/Android/Sdk" },
            userHome?.let { "$it/Library/Android/sdk" },
            environment["LOCALAPPDATA"]?.let { "$it/Android/Sdk" },
        )
        val pathDirectories = listOfNotNull(loginShellPathVariable, environment["PATH"]).flatMap { it.split(if (isWindows) ';' else ':') }.filter(String::isNotBlank)
        return (sdkRoots.map { "$it/platform-tools" } + pathDirectories + fixedDirectories)
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
                // A Windows app gets the user's PATH however it is started.
                loginShellPathVariableResolver = if (isWindows) {
                    null
                } else {
                    LoginShellPathVariableResolver(
                        shellPath = System.getenv("SHELL")?.takeIf(String::isNotBlank) ?: if (HostOs.current == HostOs.MAC) "/bin/zsh" else "/bin/sh",
                        timeout = 5.seconds,
                    )
                },
                fixedDirectories = if (isWindows) emptyList() else listOf("/usr/bin", "/usr/local/bin", "/opt/homebrew/bin"),
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
