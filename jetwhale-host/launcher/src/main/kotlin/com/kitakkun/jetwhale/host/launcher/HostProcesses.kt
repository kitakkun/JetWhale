package com.kitakkun.jetwhale.host.launcher

import com.kitakkun.jetwhale.host.release.HostReleaseMetadata
import com.kitakkun.jetwhale.host.release.HostVersion
import com.kitakkun.jetwhale.host.release.HostVersionsDirectory
import com.kitakkun.jetwhale.host.release.LauncherContract
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/** A host version the launcher is about to start: one that verified, or the bundled one. */
class HostStart(
    val version: HostVersion,
    val metadata: HostReleaseMetadata,
    val jar: Path,
    val isBundled: Boolean,
)

fun interface HostProcesses {
    /** @param setAsideVersion A version this launch set aside, which the started host tells the user about. */
    fun start(start: HostStart, setAsideVersion: HostVersion?): HostProcess
}

interface HostProcess {
    val pid: Long

    /** The exit status once the process has ended, or null while it runs. */
    fun exitStatus(): Int?

    fun waitForExit(): Int
}

/**
 * Starts hosts on the launcher's own runtime with `java`. A GUI start writes the host's output to
 * [HostVersionsDirectory.hostLogFile], so the launcher can exit while the host runs and a failed start
 * leaves its output behind; a `--headless` start shares the launcher's standard streams.
 */
class JavaHostProcesses(
    private val commandLine: HostCommandLine,
    private val hostVersionsDirectory: HostVersionsDirectory,
    private val headless: Boolean,
) : HostProcesses {
    override fun start(start: HostStart, setAsideVersion: HostVersion?): HostProcess {
        val builder = ProcessBuilder(commandLine.build(start, setAsideVersion))
        if (headless) {
            builder.inheritIO()
        } else {
            Files.createDirectories(hostVersionsDirectory.logsDirectory)
            builder.redirectErrorStream(true)
                .redirectOutput(hostVersionsDirectory.hostLogFile(start.version).toFile())
                .redirectInput(ProcessBuilder.Redirect.from(File(if (File.separatorChar == '\\') "NUL" else "/dev/null")))
        }
        val process = builder.start()
        return object : HostProcess {
            override val pid: Long = process.pid()

            override fun exitStatus(): Int? = if (process.isAlive) null else process.exitValue()

            override fun waitForExit(): Int = process.waitFor()
        }
    }
}

/**
 * The command that starts a host version: the version's own JVM arguments, then what the launcher
 * contract passes, then its jar and main class with the arguments the launcher received.
 *
 * The `skiko.library.path` that the package sets for the launcher is not passed on: skiko then
 * extracts the native library that matches the host jar, instead of loading whatever the launcher's
 * directory holds.
 *
 * @param launcherExecutable Null when the launcher cannot tell its own executable, and the host then
 * cannot restart through it.
 * @param appDataDirectoryOverride The `jetwhale.appDataDir` the launcher was given, if any.
 */
class HostCommandLine(
    private val javaExecutable: Path,
    private val platformKey: String,
    private val logsDirectory: Path,
    private val launcherExecutable: String?,
    private val hostDirectory: Path,
    private val appDataDirectoryOverride: String?,
    private val hostArguments: List<String>,
) {
    fun build(start: HostStart, setAsideVersion: HostVersion?): List<String> = buildList {
        add(javaExecutable.toString())
        addAll(start.metadata.jvmArgsFor(platformKey))
        add("-XX:ErrorFile=${logsDirectory.resolve("hs_err_pid%p.log")}")
        add("-D${LauncherContract.CONTRACT_PROPERTY}=${LauncherContract.VERSION}")
        launcherExecutable?.let { add("-D${LauncherContract.EXECUTABLE_PROPERTY}=$it") }
        add("-D${LauncherContract.HOST_DIRECTORY_PROPERTY}=$hostDirectory")
        appDataDirectoryOverride?.let { add("-D$APP_DATA_DIR_PROPERTY=$it") }
        setAsideVersion?.let { add("-D${LauncherContract.SET_ASIDE_VERSION_PROPERTY}=${it.name}") }
        add("-cp")
        add(start.jar.toString())
        add(start.metadata.mainClass)
        addAll(hostArguments)
    }
}

/** The host's own override of `~/.jetwhale`, which the launcher honors and passes on. */
const val APP_DATA_DIR_PROPERTY = "jetwhale.appDataDir"
