package com.kitakkun.jetwhale.host.launcher

import com.kitakkun.jetwhale.host.release.HostReleaseMetadataReader
import com.kitakkun.jetwhale.host.release.HostVersionsDirectory
import com.kitakkun.jetwhale.host.release.LauncherCapabilities
import com.kitakkun.jetwhale.host.release.LauncherContract
import com.kitakkun.jetwhale.host.release.LockFiles
import com.kitakkun.jetwhale.host.release.ReleaseMetadataSignatureVerifier
import com.kitakkun.jetwhale.host.release.hostPlatformKey
import java.awt.Desktop
import java.awt.GraphicsEnvironment
import java.awt.desktop.AppReopenedListener
import java.lang.module.ModuleFinder
import java.nio.file.Path
import java.util.concurrent.TimeUnit
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

/**
 * The packaged app's entry point. It chooses a host version under `<app data>/host/` or the bundled
 * one, starts it on this runtime, and exits once the host has completed its start; with `--headless`
 * it stays and returns the host's exit status.
 */
fun main(args: Array<String>) {
    val arguments = LauncherArguments.parse(args.toList())
    val appDataOverride = System.getProperty(APP_DATA_DIR_PROPERTY)?.takeIf(String::isNotBlank)
    val appData = appDataOverride?.let(Path::of) ?: Path.of(System.getProperty("user.home"), ".jetwhale")
    val versions = HostVersionsDirectory(appData.resolve("host"))
    val launcher = createHostLauncher(arguments, versions, appDataOverride)

    val exitStatus = when (val outcome = launcher.launch(arguments.afterPid, arguments.retryVersion)) {
        is LaunchOutcome.Started -> if (arguments.headless) outcome.process.waitForExit() else 0

        is LaunchOutcome.Neither, is LaunchOutcome.ActivatedRunningHost -> 0

        is LaunchOutcome.RunningHostUnreachable -> 1

        is LaunchOutcome.Crashed -> outcome.exitStatus

        is LaunchOutcome.NothingLeft -> {
            showNothingLeft(versions.logsDirectory, arguments.headless)
            1
        }
    }
    exitProcess(exitStatus)
}

private fun createHostLauncher(
    arguments: LauncherArguments,
    versions: HostVersionsDirectory,
    appDataOverride: String?,
): HostLauncher {
    val platformKey = checkNotNull(hostPlatformKey(System.getProperty("os.name"), System.getProperty("os.arch"))) {
        "JetWhale has no host for ${System.getProperty("os.name")} on ${System.getProperty("os.arch")}"
    }
    val runningHost = InstanceRecordChannel(versions, activationTimeout = 5.seconds)
    if (!arguments.headless) forwardReopensToHost(runningHost)
    return HostLauncher(
        versions = versions,
        bundled = System.getProperty(RESOURCES_DIR_PROPERTY)?.let { BundledHost.read(Path.of(it).resolve("host")) },
        capabilities = LauncherCapabilities(
            contract = LauncherContract.VERSION,
            javaFeatureVersion = Runtime.version().feature(),
            modules = ModuleFinder.ofSystem().findAll().map { it.descriptor().name() }.toSet(),
            platformKey = platformKey,
        ),
        metadataReader = HostReleaseMetadataReader(ReleaseMetadataSignatureVerifier.JetWhaleReleases),
        locks = LockFiles.Os,
        runningHost = runningHost,
        hostProcesses = JavaHostProcesses(
            commandLine = HostCommandLine(
                javaExecutable = Path.of(System.getProperty("java.home"), "bin", javaExecutableName(platformKey, arguments.headless)),
                platformKey = platformKey,
                logsDirectory = versions.logsDirectory,
                launcherExecutable = System.getProperty(APP_PATH_PROPERTY) ?: ProcessHandle.current().info().command().orElse(null),
                hostDirectory = versions.root,
                appDataDirectoryOverride = appDataOverride,
                hostArguments = arguments.hostArguments,
            ),
            versions = versions,
            headless = arguments.headless,
        ),
        startupWindow = StartupWindow(
            length = STARTUP_WINDOW,
            pollInterval = 200.milliseconds,
            timeSource = TimeSource.Monotonic,
            sleep = { Thread.sleep(it.inWholeMilliseconds) },
        ),
        log = FileLauncherLog(versions.logsDirectory.resolve("launcher.log"), echoToStandardError = arguments.headless),
        waitForProcessExit = ::waitForProcessExit,
    )
}

/** `javaw.exe` on Windows, so a GUI host opens no console window; `java.exe` for `--headless`. */
private fun javaExecutableName(platformKey: String, headless: Boolean): String = when {
    !platformKey.startsWith("windows") -> "java"
    headless -> "java.exe"
    else -> "javaw.exe"
}

private fun waitForProcessExit(pid: Long) {
    ProcessHandle.of(pid).ifPresent { process ->
        process.onExit().completeOnTimeout(process, AFTER_PROCESS_TIMEOUT.inWholeSeconds, TimeUnit.SECONDS).join()
    }
}

/**
 * During the startup window macOS ties the app bundle to this process, so a reopen arrives here
 * rather than at the host, and is passed on to it.
 */
private fun forwardReopensToHost(runningHost: RunningHostChannel) {
    if (!System.getProperty("os.name").contains("mac", ignoreCase = true)) return
    if (!Desktop.isDesktopSupported()) return
    val desktop = Desktop.getDesktop()
    if (!desktop.isSupported(Desktop.Action.APP_EVENT_REOPENED)) return
    desktop.addAppEventListener(
        AppReopenedListener {
            thread(isDaemon = true, block = runningHost::requestActivation)
        },
    )
}

private fun showNothingLeft(logs: Path, headless: Boolean) {
    val message = "JetWhale Debugger could not start any of its host versions.\n\n" +
        "Its logs are in $logs.\nThe latest release is at $RELEASES_PAGE."
    if (headless || GraphicsEnvironment.isHeadless()) {
        System.err.println(message)
    } else {
        JOptionPane.showMessageDialog(null, message, "JetWhale Debugger", JOptionPane.ERROR_MESSAGE)
    }
}
