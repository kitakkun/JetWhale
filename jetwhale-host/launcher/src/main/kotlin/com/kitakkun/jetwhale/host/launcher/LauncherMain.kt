package com.kitakkun.jetwhale.host.launcher

import com.kitakkun.jetwhale.host.release.HostReleaseMetadataReader
import com.kitakkun.jetwhale.host.release.HostVersionsDirectory
import com.kitakkun.jetwhale.host.release.LauncherCapabilities
import com.kitakkun.jetwhale.host.release.LauncherContract
import com.kitakkun.jetwhale.host.release.LockFiles
import com.kitakkun.jetwhale.host.release.ReleaseMetadataSignatureVerifier
import com.kitakkun.jetwhale.host.release.hostPlatformKey
import com.kitakkun.jetwhale.host.release.runtimeModulesVisibleToHost
import java.awt.GraphicsEnvironment
import java.lang.management.ManagementFactory
import java.nio.file.Path
import javax.swing.JOptionPane
import kotlin.concurrent.thread
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private const val RESOURCES_DIR_PROPERTY = "compose.application.resources.dir"
private const val APP_PATH_PROPERTY = "jpackage.app-path"
private const val RELEASES_PAGE = "https://github.com/kitakkun/JetWhale/releases"
private val STARTUP_WINDOW = 30.seconds
private val AFTER_PROCESS_TIMEOUT = 60.seconds

/** The host's own override of `~/.jetwhale`, which the launcher honors too. */
internal const val APP_DATA_DIR_PROPERTY = "jetwhale.appDataDir"

/**
 * The packaged app's entry point. It chooses a host version under `<app data>/host/` or the bundled
 * one, and runs it in this process, which stays the app for as long as the host runs.
 */
fun main(args: Array<String>) {
    val arguments = LauncherArguments.parse(args.toList())
    val appData = System.getProperty(APP_DATA_DIR_PROPERTY)?.takeIf(String::isNotBlank)?.let(Path::of)
        ?: Path.of(System.getProperty("user.home"), ".jetwhale")
    val hostVersionsDirectory = HostVersionsDirectory(appData.resolve("host"))
    val log = FileLauncherLog(hostVersionsDirectory.logsDirectory.resolve("launcher.log"), echoToStandardError = arguments.headless)
    val runningHostChannel = InstanceRecordChannel(hostVersionsDirectory, activationTimeout = 5.seconds)

    val outcome = try {
        createHostLauncher(hostVersionsDirectory, runningHostChannel, log).launch(arguments.afterPid, arguments.retryVersion)
    } catch (e: Throwable) {
        log.write("Stopped by an unexpected error: ${e.stackTraceToString()}")
        showError("JetWhale Debugger could not start: $e", hostVersionsDirectory.logsDirectory, arguments.headless)
        exitProcess(1)
    }
    when (outcome) {
        is LaunchOutcome.Starting -> runHost(outcome, arguments, hostVersionsDirectory, runningHostChannel, log)

        is LaunchOutcome.ActivatedRunningHost -> exitProcess(0)

        is LaunchOutcome.RunningHostUnreachable -> exitProcess(1)

        is LaunchOutcome.NothingLeft -> {
            showError("JetWhale Debugger could not start any of its host versions.", hostVersionsDirectory.logsDirectory, arguments.headless)
            exitProcess(1)
        }
    }
}

private fun createHostLauncher(
    hostVersionsDirectory: HostVersionsDirectory,
    runningHostChannel: RunningHostChannel,
    log: LauncherLog,
): HostLauncher = HostLauncher(
    hostVersionsDirectory = hostVersionsDirectory,
    bundledHost = System.getProperty(RESOURCES_DIR_PROPERTY)?.let { BundledHost.read(Path.of(it).resolve("host")) },
    capabilities = LauncherCapabilities(
        contract = LauncherContract.VERSION,
        javaFeatureVersion = Runtime.version().feature(),
        modules = runtimeModulesVisibleToHost(),
        platformKey = currentPlatformKey(),
        jvmArguments = ManagementFactory.getRuntimeMXBean().inputArguments,
    ),
    metadataReader = HostReleaseMetadataReader(ReleaseMetadataSignatureVerifier.JetWhaleReleases),
    lockFiles = LockFiles.Os,
    runningHostChannel = runningHostChannel,
    processTable = OsProcessTable(exitTimeout = AFTER_PROCESS_TIMEOUT),
    log = log,
)

/**
 * Runs the chosen host on this thread and records how its start goes: a throw from its main, the end
 * of its startup window, or a shutdown of this JVM before either. A host's main that returns leaves
 * the JVM to end once the host's threads have.
 */
private fun runHost(
    starting: LaunchOutcome.Starting,
    arguments: LauncherArguments,
    hostVersionsDirectory: HostVersionsDirectory,
    runningHostChannel: RunningHostChannel,
    log: LauncherLog,
) {
    Runtime.getRuntime().addShutdownHook(Thread(starting.startup::shutDownWithoutFailure, "jetwhale-host-shutdown"))
    thread(isDaemon = true, name = "jetwhale-host-startup-window") {
        StartupWindow(
            length = STARTUP_WINDOW,
            pollInterval = 200.milliseconds,
            timeSource = TimeSource.Monotonic,
            sleep = { Thread.sleep(it.inWholeMilliseconds) },
        ).watch(starting.startup) { runningHostChannel.isPublishedBy(ProcessHandle.current().pid()) }
    }
    val inProcessHost = InProcessHost(
        hostVersionsDirectory = hostVersionsDirectory,
        platformKey = currentPlatformKey(),
        launcherExecutable = System.getProperty(APP_PATH_PROPERTY) ?: ProcessHandle.current().info().command().orElse(null),
        writesOutputToLog = !arguments.headless,
    )
    try {
        inProcessHost.run(starting.start, starting.setAsideVersion, arguments.hostArguments)
    } catch (e: Throwable) {
        System.err.println("JetWhale host ${starting.start.version} stopped: ${e.stackTraceToString()}")
        log.write("${starting.start.version} stopped: $e")
        starting.startup.hostFailed()
        exitProcess(1)
    }
}

private fun currentPlatformKey(): String = checkNotNull(hostPlatformKey(System.getProperty("os.name"), System.getProperty("os.arch"))) {
    "JetWhale has no host for ${System.getProperty("os.name")} on ${System.getProperty("os.arch")}"
}

private fun showError(problem: String, logs: Path, headless: Boolean) {
    val message = "$problem\n\nIts logs are in $logs.\nThe latest release is at $RELEASES_PAGE."
    if (headless || GraphicsEnvironment.isHeadless()) {
        System.err.println(message)
    } else {
        JOptionPane.showMessageDialog(null, message, "JetWhale Debugger", JOptionPane.ERROR_MESSAGE)
    }
}
