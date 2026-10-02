package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginUi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCapablePlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.minutes

// Instantiated by the host via the fully-qualified name declared in plugin-manifest.json.
@Suppress("UNUSED")
class MirrorHostPluginFactory : JetWhaleHostPluginFactory {
    override fun createPlugin(): JetWhaleHostPlugin = MirrorHostPlugin()
}

private val toolPaths: MirrorToolPaths by lazy(MirrorToolPaths::locate)

/** How long a device's idb companion outlives its last user, so switching back to it is instant. */
private val COMPANION_IDLE_TIMEOUT = 3.minutes

// A host-only plugin gets an instance per debug session, but a device has one screen: the
// instances share the companions, so two of them watching one iPhone start one companion.
private val companions: IdbCompanions? by lazy {
    val idbPath = toolPaths.idbPath ?: return@lazy null
    val idbCompanionPath = toolPaths.idbCompanionPath ?: return@lazy null
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
            discovery = DeviceDiscovery(toolPaths, companions, emulatorScreens),
            captures = MirrorCaptures(defaultCapturesRoot(), storage, pluginScope, ZoneId.systemDefault(), notices, toolPaths.ffmpegPath),
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
                if (liveInstances.decrementAndGet() == 0) companions?.releaseAll()
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
            StartRecordingCommand(mirror),
            StopRecordingCommand(mirror),
            ListCapturesCommand(mirror),
        )
    }
}
