package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginUi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCapablePlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.xctestrunner.XcTestRunners
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

// Instantiated by the host via the fully-qualified name declared in plugin-manifest.json.
@Suppress("UNUSED")
class MirrorHostPluginFactory : JetWhaleHostPluginFactory {
    override fun createPlugin(): JetWhaleHostPlugin = MirrorHostPlugin()
}

private val toolLocationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * The directories the tools this plugin drives are looked for in, read once per process when first
 * awaited. They include the login shell's PATH, which can take seconds to read, so callers suspend
 * on it rather than block the thread that first needs a tool, which may be the UI thread.
 */
private val toolSearchDirectories: Deferred<List<String>> = toolLocationScope.async(start = CoroutineStart.LAZY) {
    val loginShellPathVariable = if (runsOnWindows) {
        null
    } else {
        val shellPath = System.getenv("SHELL")?.takeIf(String::isNotBlank) ?: if (System.getProperty("os.name").orEmpty().startsWith("Mac")) "/bin/zsh" else "/bin/sh"
        LoginShellPathVariableResolver(shellPath = shellPath, timeout = 5.seconds).resolveLoginShellPathVariable()
    }
    val searchDirectories = toolDirectories(loginShellPathVariable = loginShellPathVariable, pathVariable = System.getenv("PATH"))
    if (loginShellPathVariable != null) SystemProcessLauncher.launchedProcessPathVariable = searchDirectories.joinToString(File.pathSeparator)
    searchDirectories
}

/** The tools this plugin drives, located once per process when first awaited. */
private val toolPaths: Deferred<MirrorToolPaths> = toolLocationScope.async(start = CoroutineStart.LAZY) {
    val searchDirectories = toolSearchDirectories.await()
    val home = System.getProperty("user.home")
    MirrorToolLocator(
        searchDirectories = searchDirectories,
        androidSdkDirectories = listOfNotNull(
            System.getenv("ANDROID_HOME"),
            System.getenv("ANDROID_SDK_ROOT"),
            "$home/Library/Android/sdk",
            "$home/Android/Sdk",
            System.getenv("LOCALAPPDATA")?.let { "$it/Android/Sdk" },
        ),
    ).locateToolPaths()
}

private val ffmpegPath: Deferred<String?> = toolLocationScope.async(start = CoroutineStart.LAZY) { toolPaths.await().ffmpegPath }

/** How long a device's idb companion outlives its last user, so switching back to it is instant. */
private val COMPANION_IDLE_TIMEOUT = 3.minutes

// A host-only plugin gets an instance per debug session, but a device has one screen: the instances
// share the companions, so two of them watching one iPhone start one companion.
private val companions: Deferred<IdbCompanions?> = toolLocationScope.async(start = CoroutineStart.LAZY) {
    val located = toolPaths.await()
    val idbPath = located.idbPath ?: return@async null
    val idbCompanionPath = located.idbCompanionPath ?: return@async null
    IdbCompanions(
        idbCompanionPath = idbCompanionPath,
        idbPath = idbPath,
        launcher = SystemProcessLauncher,
        commands = { command -> runCommandChecked(*command.toTypedArray()) },
        ports = LocalPorts,
        idleTimeout = COMPANION_IDLE_TIMEOUT,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    ).also { shared ->
        Runtime.getRuntime().addShutdownHook(Thread(shared::destroyAllNow))
    }
}

/** The team the runners sign with for iPhones; every Mirror instance in this host shows the same one. */
private val runnerSigningTeam = RunnerSigningTeam()

private val iproxyPath: Deferred<String?> = toolLocationScope.async(start = CoroutineStart.LAZY) {
    toolSearchDirectories.await().map { File(it, "iproxy") }.firstOrNull { it.isFile && it.canExecute() }?.path
}

private val xcTestRunners: Deferred<XcTestRunners?> = toolLocationScope.async(start = CoroutineStart.LAZY) {
    val xcrunPath = toolPaths.await().xcrunPath ?: return@async null
    XcTestRunners.onThisMac(stateDirectory = File(appDataDirectory(), "xctest-runner"), xcrunPath = xcrunPath, iproxyPath = iproxyPath.await(), settings = runnerSigningTeam)
}

private val liveInstances = AtomicInteger()

private val emulatorScreens: EmulatorScreens by lazy {
    EmulatorScreens(
        emulatorRunningDirectories(
            osName = System.getProperty("os.name").orEmpty(),
            home = File(System.getProperty("user.home")),
            temp = File(System.getProperty("java.io.tmpdir")),
            runtimeDir = System.getenv("XDG_RUNTIME_DIR"),
        ),
    )
}

@OptIn(ExperimentalJetWhaleApi::class)
private class MirrorHostPlugin :
    JetWhaleHostPlugin(),
    JetWhaleHostPluginUi,
    JetWhaleMcpCapablePlugin {

    init {
        liveInstances.incrementAndGet()
    }

    private val mirror by lazy {
        val notices = MirrorNotices(pluginScope)
        DeviceMirror(
            discovery = DeviceDiscovery(toolPaths, companions, xcTestRunners, iproxyPath, emulatorScreens),
            developmentTeamSetting = DevelopmentTeamSetting(storage, pluginScope, runnerSigningTeam),
            captures = MirrorCaptures(File(appDataDirectory(), "plugin-data/com.kitakkun.jetwhale.mirror/captures"), storage, pluginScope, ZoneId.systemDefault(), notices, ffmpegPath, CaptureClipboard(osascriptPath = "/usr/bin/osascript".takeIf { File(it).canExecute() })),
            notices = notices,
            scope = pluginScope,
        )
    }

    override fun onDispose() {
        // onDispose cannot suspend, and pluginScope is cancelled with the instance, so the running
        // recordings are stopped and saved here before it returns.
        runBlocking {
            try {
                mirror.dispose()
            } finally {
                // await() starts a lazy Deferred, so awaiting companions that no look has asked for
                // would locate the tools only to release nothing.
                if (liveInstances.decrementAndGet() == 0 && companions.isCompleted) companions.await()?.releaseAll()
            }
        }
    }

    @Composable
    override fun Content() {
        MirrorScreenRoot(mirror)
    }

    override val mcpCommands: List<JetWhaleMcpCommand> by lazy {
        listOf(
            ListDevicesCommand(mirror),
            CaptureScreenshotCommand(mirror),
            TapCommand(mirror),
            SwipeCommand(mirror),
            PressButtonCommand(mirror),
            SetScreenCommand(mirror),
            InputTextCommand(mirror),
            KeepRunnerAliveCommand(mirror),
            StartRecordingCommand(mirror),
            StopRecordingCommand(mirror),
            ListCapturesCommand(mirror),
        )
    }
}

/**
 * The host's app data: `jetwhale.appDataDir` (a sandbox for development launches) or `~/.jetwhale`.
 * Captures go under this plugin's directory there unless the user picks a folder, and the XCTest
 * runners keep their builds and shared state there, where every plugin finds them.
 */
private fun appDataDirectory(): File = File(System.getProperty("jetwhale.appDataDir")?.takeIf(String::isNotBlank) ?: "${System.getProperty("user.home")}/.jetwhale")
