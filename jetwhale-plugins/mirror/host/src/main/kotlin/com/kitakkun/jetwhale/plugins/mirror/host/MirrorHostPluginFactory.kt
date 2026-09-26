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
import kotlin.time.Duration.Companion.minutes

// Instantiated by the host via the fully-qualified name declared in plugin-manifest.json.
@Suppress("UNUSED")
class MirrorHostPluginFactory : JetWhaleHostPluginFactory {
    override fun createPlugin(): JetWhaleHostPlugin = MirrorHostPlugin()
}

private val tools: MirrorTools by lazy(MirrorTools::locate)

/** How long a device's idb companion outlives its last user, so switching back to it is instant. */
private val COMPANION_IDLE_TIMEOUT = 3.minutes

// A host-only plugin gets an instance per debug session, but a device has one screen: the
// instances share the companions, so two of them watching one iPhone start one companion.
private val companions: IdbCompanions? by lazy {
    val idb = tools.idb ?: return@lazy null
    val idbCompanion = tools.idbCompanion ?: return@lazy null
    IdbCompanions(
        idbCompanion = idbCompanion,
        idb = idb,
        launcher = SystemProcessLauncher,
        commands = { command -> runCommandChecked(*command.toTypedArray()) },
        ports = LocalPorts,
        idleTimeout = COMPANION_IDLE_TIMEOUT,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    ).also { shared ->
        // A companion is a separate process; one left behind by a host that quit would keep the
        // device's screen capture open until killed by hand.
        Runtime.getRuntime().addShutdownHook(Thread(shared::destroyAllNow))
    }
}

// Shared like the companions: its HTTP/2 connections to an emulator serve every instance.
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

    private val mirror by lazy {
        DeviceMirror(
            discovery = DeviceDiscovery(tools, companions, emulatorScreens),
            captures = MirrorCaptures(defaultCapturesRoot(), storage, pluginScope, ZoneId.systemDefault()),
            scope = pluginScope,
        )
    }

    override fun onDispose() {
        runBlocking { mirror.dispose() }
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
