package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.annotation.VisibleForTesting
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
 * The tools this plugin drives, located once per process when first awaited. A tool found nowhere
 * else is looked for on the login shell's PATH, which can take seconds to read, so callers suspend on
 * it rather than block the thread that first needs a tool, which may be the UI thread.
 */
private val toolPaths: Deferred<MirrorToolPaths> = toolLocationScope.async(start = CoroutineStart.LAZY) {
    val home = System.getProperty("user.home")
    val toolSearchResult = MirrorToolLocator(
        hostPathVariable = System.getenv("PATH"),
        wellKnownDirectories = if (runsOnWindows) emptyList() else WellKnownToolDirectories(File(home)).list(),
        androidSdkDirectories = listOfNotNull(
            System.getenv("ANDROID_HOME"),
            System.getenv("ANDROID_SDK_ROOT"),
            "$home/Library/Android/sdk",
            "$home/Android/Sdk",
            System.getenv("LOCALAPPDATA")?.let { "$it/Android/Sdk" },
        ),
        loginShellPathVariableResolver = if (runsOnWindows) {
            null
        } else {
            val shellPath = System.getenv("SHELL")?.takeIf(String::isNotBlank) ?: if (System.getProperty("os.name").orEmpty().startsWith("Mac")) "/bin/zsh" else "/bin/sh"
            LoginShellPathVariableResolver(shellPath = shellPath, timeout = 5.seconds)
        },
    ).locateTools()
    // On Windows the search adds nothing to the PATH, and the variable is spelled `Path` there, so
    // setting `PATH` would add a second one beside it.
    if (!runsOnWindows) SystemProcessLauncher.launchedProcessPathVariable = toolSearchResult.searchedDirectories.joinToString(File.pathSeparator)
    toolSearchResult.toolPaths
}

/**
 * The directories this plugin's tools are commonly installed in on macOS and Linux, for the user
 * whose home is [homeDirectory], searched whether or not a PATH lists them:
 * - Homebrew's, where it links idb_companion, ffmpeg, libimobiledevice's iproxy and the adb of its
 *   android-platform-tools cask: `/opt/homebrew` on Apple silicon, `/usr/local` on Intel Macs;
 * - `~/.local/bin`, where pipx and uv install idb's client;
 * - pip's per-user directories on macOS, newest Python first, where `pip3 install fb-idb` puts idb
 *   when it cannot write to the Python it runs under, as with Xcode's;
 * - pyenv's shims, where idb lands when pip runs under a pyenv Python.
 */
@VisibleForTesting
internal class WellKnownToolDirectories(private val homeDirectory: File) {
    fun list(): List<String> {
        val pipUserDirectories = File(homeDirectory, "Library/Python").listFiles(File::isDirectory).orEmpty()
            .sortedWith(compareByDescending<File> { it.name.substringBefore('.').toIntOrNull() }.thenByDescending { it.name.substringAfter('.').toIntOrNull() })
            .map { File(it, "bin").path }
        return listOf("/opt/homebrew/bin", "/usr/local/bin", File(homeDirectory, ".local/bin").path) + pipUserDirectories + File(homeDirectory, ".pyenv/shims").path
    }
}

private val ffmpegPath: Deferred<String?> = toolLocationScope.async(start = CoroutineStart.LAZY) { toolPaths.await().ffmpegPath }

/** How long a device's idb companion outlives its last user, so switching back to it is instant. */
private val COMPANION_IDLE_TIMEOUT = 3.minutes

// A host-only plugin gets an instance per debug session, but a device has one screen: the
// instances share the companions, so two of them watching one iPhone start one companion.
private val companions: Deferred<IdbCompanions?> = toolLocationScope.async(start = CoroutineStart.LAZY) {
    val located = toolPaths.await()
    val idbPath = located.idbPath ?: return@async null
    val idbCompanionPath = located.idbCompanionPath ?: return@async null
    IdbCompanions(
        idbCompanionPath = idbCompanionPath,
        idbPath = idbPath,
        launcher = SystemProcessLauncher,
        commands = { command -> runCommandChecked(IDB_COMMAND_TIMEOUT, *command.toTypedArray()) },
        ports = LocalPorts,
        idleTimeout = COMPANION_IDLE_TIMEOUT,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    ).also { shared ->
        Runtime.getRuntime().addShutdownHook(Thread(shared::destroyAllNow))
    }
}

/** How long `xcode-select -p` may take. It prints the developer directory it has on record, at once. */
private val XCODE_SELECT_TIMEOUT = 10.seconds

private val idbCanSendSimulatorInput: Deferred<Boolean> = toolLocationScope.async(start = CoroutineStart.LAZY) {
    if (toolPaths.await().xcrunPath == null) return@async false
    val developerDirectoryPath = try {
        runCommandChecked(XCODE_SELECT_TIMEOUT, "/usr/bin/xcode-select", "-p").stdoutText.trim()
    } catch (_: DeviceControlException) {
        return@async false
    }
    isSimulatorKitInPrivateFrameworks(File(developerDirectoryPath))
}

/** The team the runners sign with for iPhones; every Mirror instance in this host shows the same one. */
private val runnerSigningTeam = RunnerSigningTeam()

private val xcTestRunners: Deferred<XcTestRunners?> = toolLocationScope.async(start = CoroutineStart.LAZY) {
    val located = toolPaths.await()
    val xcrunPath = located.xcrunPath ?: return@async null
    XcTestRunners.onThisMac(stateDirectory = File(appDataDirectory(), "xctest-runner"), xcrunPath = xcrunPath, iproxyPath = located.iproxyPath, settings = runnerSigningTeam)
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
            discovery = DeviceDiscovery(toolPaths, companions, xcTestRunners, idbCanSendSimulatorInput, emulatorScreens),
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
