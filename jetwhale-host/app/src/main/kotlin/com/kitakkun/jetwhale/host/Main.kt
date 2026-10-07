package com.kitakkun.jetwhale.host

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.awaitApplication
import ch.qos.logback.classic.Level
import com.kitakkun.jetwhale.host.cli.CommandLineArgumentsParser
import com.kitakkun.jetwhale.host.cli.JetWhaleLogLevel
import com.kitakkun.jetwhale.host.component.InitializingDialog
import com.kitakkun.jetwhale.host.component.ShuttingDownDialog
import com.kitakkun.jetwhale.host.di.JetWhaleAppGraph
import com.kitakkun.jetwhale.host.instance.HostInstance
import com.kitakkun.jetwhale.host.instance.HostInstanceClaim
import com.kitakkun.jetwhale.host.menu.LocalMainWindowMenuCommands
import com.kitakkun.jetwhale.host.menu.MainWindowMenuCommands
import com.kitakkun.jetwhale.host.menu.MainWindowMenus
import com.kitakkun.jetwhale.host.model.AdditionalPluginDirectories
import com.kitakkun.jetwhale.host.model.HostLaunch
import com.kitakkun.jetwhale.host.model.HostOs
import com.kitakkun.jetwhale.host.model.PersistedWindowState
import com.kitakkun.jetwhale.host.release.HostDirectory
import com.kitakkun.jetwhale.host.release.HostVersion
import com.kitakkun.jetwhale.host.release.LauncherContract
import com.kitakkun.jetwhale.host.release.LockFiles
import com.kitakkun.jetwhale.host.ui.JwTheme
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.painterResource
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.Desktop
import java.awt.Taskbar
import java.awt.desktop.QuitResponse
import java.nio.file.Path
import java.util.Properties
import java.util.concurrent.atomic.AtomicReference
import javax.imageio.ImageIO
import kotlin.system.exitProcess
import ch.qos.logback.classic.Logger as LogbackLogger

private val DefaultWindowSize = DpSize(1280.dp, 800.dp)

fun main(args: Array<String>) = runBlocking {
    defaultToSystemAppearance(System.getProperties())
    val cliOptions = CommandLineArgumentsParser().parse(args)
    val hostInstance = claimLauncherInstance()

    if (cliOptions.headless) {
        // AWT reads this once, when its first class is loaded, so it has to be set before anything
        // else here touches AWT.
        System.setProperty("java.awt.headless", "true")
    } else {
        configureAppMetadata()
    }

    cliOptions.logLevel?.let(::applyLogLevel)

    val appGraph: JetWhaleAppGraph = createGraphFactory<JetWhaleAppGraph.Factory>()
        .create(
            serverPortOverrides = cliOptions.serverPortOverrides,
            mcpPermissionOverride = cliOptions.mcpPermissionOverride,
            additionalPluginDirectories = AdditionalPluginDirectories(cliOptions.pluginDirs),
            hostLaunch = hostLaunchOf(args),
        )

    appGraph.logCaptureService.startCapture()

    appGraph.applicationLifecycleOwner.initialize()

    if (cliOptions.headless) {
        exitProcess(appGraph.headlessHostRunner.run(onReady = { hostInstance?.publishInstanceJson() }))
    }

    val windowState = appGraph.windowStateRepository.loadWindowState().toWindowState()

    awaitApplication {
        JetWhaleMainWindow(appGraph = appGraph, windowState = windowState, hostInstance = hostInstance)
    }
}

/** How the launcher, if it started this host, described it in the launcher contract's properties. */
private fun hostLaunchOf(args: Array<String>): HostLaunch {
    val contract = System.getProperty(LauncherContract.CONTRACT_PROPERTY)?.toIntOrNull() ?: return HostLaunch.Standalone
    val hostDirectory = System.getProperty(LauncherContract.HOST_DIRECTORY_PROPERTY) ?: return HostLaunch.Standalone
    return HostLaunch.ByLauncher(
        launcherContract = contract,
        launcherExecutable = System.getProperty(LauncherContract.EXECUTABLE_PROPERTY),
        hostDirectoryPath = Path.of(hostDirectory),
        setAsideVersion = System.getProperty(LauncherContract.SET_ASIDE_VERSION_PROPERTY)?.let(HostVersion::parse),
        arguments = args.toList(),
        javaToolOptions = System.getenv("JAVA_TOOL_OPTIONS"),
    )
}

/**
 * Has macOS draw the window frame in the system's light or dark appearance, unless the launcher or
 * the command line chose one. AWT reads the property once, when it starts, so this runs before
 * anything touches AWT.
 */
@VisibleForTesting
internal fun defaultToSystemAppearance(properties: Properties) {
    properties.putIfAbsent("apple.awt.application.appearance", "system")
}

/**
 * Makes a host the launcher started the only one of its app data directory: it takes
 * `instance.lock`, or asks the host that holds it to bring its window forward and exits. Null for a
 * host started any other way.
 */
private fun claimLauncherInstance(): HostInstance? {
    if (System.getProperty(LauncherContract.CONTRACT_PROPERTY) == null) return null
    val hostDirectory = System.getProperty(LauncherContract.HOST_DIRECTORY_PROPERTY) ?: return null
    return when (val claim = HostInstance.claim(HostDirectory(Path.of(hostDirectory)), LockFiles.Os)) {
        is HostInstanceClaim.Claimed -> claim.instance
        is HostInstanceClaim.HeldByAnother -> exitProcess(0)
    }
}

/**
 * Applies the application name and icon at runtime so they are correct even when the host is
 * launched as a plain JVM process (e.g. the `runJetWhale`/`runJetWhaleLocal` Gradle tasks or
 * `java -jar` on the uber jar). Packaged distributions get them from the installer metadata,
 * where these calls are harmless no-ops.
 */
private fun configureAppMetadata() {
    // Read by macOS AWT during initialization, so this must run before any AWT class is touched.
    System.setProperty("apple.awt.application.name", "JetWhale")

    if (Taskbar.isTaskbarSupported()) {
        val taskbar = Taskbar.getTaskbar()
        if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
            object {}.javaClass.getResource("/icon.png")?.let { iconUrl ->
                taskbar.iconImage = ImageIO.read(iconUrl)
            }
        }
    }
}

/** Restores the geometry of the previous run; an unplaced or absent one opens centered. */
private fun PersistedWindowState?.toWindowState(): WindowState {
    val x = this?.x
    val y = this?.y
    return WindowState(
        size = this?.let { DpSize(it.width.dp, it.height.dp) } ?: DefaultWindowSize,
        position = if (x != null && y != null) {
            WindowPosition(x.dp, y.dp)
        } else {
            WindowPosition.Aligned(Alignment.Center)
        },
    )
}

@OptIn(FlowPreview::class)
@Composable
private fun ApplicationScope.JetWhaleMainWindow(
    appGraph: JetWhaleAppGraph,
    windowState: WindowState,
    hostInstance: HostInstance?,
) {
    val applicationState by appGraph
        .applicationLifecycleOwner
        .applicationStateFlow
        .collectAsState()

    val verifyingTrustRegistry by appGraph
        .pluginTrustService
        .verifyingTrustRegistryFlow
        .collectAsState()

    val coroutineScope = rememberCoroutineScope()
    val menuBringToFrontRequests = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST) }
    val mainWindowMenuCommands = remember(appGraph, coroutineScope) {
        MainWindowMenuCommands(
            hostNavigationService = appGraph.hostNavigationService,
            coroutineScope = coroutineScope,
            quit = appGraph.applicationLifecycleOwner::shutdown,
            bringMainWindowToFront = { menuBringToFrontRequests.tryEmit(Unit) },
        )
    }
    val systemQuitResponse = remember { AtomicReference<QuitResponse?>(null) }
    val isMac = HostOs.current == HostOs.MAC
    if (isMac) {
        MacApplicationMenuEffect(mainWindowMenuCommands = mainWindowMenuCommands, systemQuitResponse = systemQuitResponse)
    }

    LaunchedEffect(Unit) {
        appGraph
            .applicationLifecycleOwner
            .applicationStateFlow
            .collect {
                if (it == ApplicationLifecycleOwner.ApplicationState.STOPPED) {
                    exitApplication()
                    // macOS waits for an answer to a quit it asked for (the application menu, the
                    // Dock, a logout), so the answer comes once the host has shut down.
                    systemQuitResponse.getAndSet(null)?.performQuit()
                }
            }
    }

    LaunchedEffect(windowState) {
        snapshotFlow { Triple(windowState.size, windowState.position, windowState.placement) }
            // Only a floating window's geometry is saved: a maximized or full-screen one would
            // overwrite it, and the next launch would open a floating window the size of the
            // screen.
            .map { (size, position, placement) ->
                if (placement != WindowPlacement.Floating) return@map null
                PersistedWindowState(
                    width = size.width.value,
                    height = size.height.value,
                    x = position.takeIf(WindowPosition::isSpecified)?.x?.value,
                    y = position.takeIf(WindowPosition::isSpecified)?.y?.value,
                )
            }
            .debounce(500)
            .collect { state ->
                if (state != null) {
                    appGraph.windowStateRepository.saveWindowState(state)
                }
            }
    }

    Window(
        title = "JetWhale",
        icon = painterResource(Res.drawable.app_icon),
        state = windowState,
        onCloseRequest = appGraph.applicationLifecycleOwner::shutdown,
        // After the focused content has had the key, so a plugin's own shortcut wins. On macOS the
        // menu bar handles them instead: ⌘, and ⌘Q natively, the rest also only once the content
        // has declined the key.
        onKeyEvent = { keyEvent -> !isMac && mainWindowMenuCommands.runShortcut(keyEvent) },
    ) {
        LaunchedEffect(hostInstance) {
            hostInstance?.publishInstanceJson()
            merge(menuBringToFrontRequests, hostInstance?.bringToFrontRequests ?: emptyFlow()).collect {
                windowState.isMinimized = false
                window.toFront()
                window.requestFocus()
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.APP_REQUEST_FOREGROUND)) {
                    Desktop.getDesktop().requestForeground(true)
                }
            }
        }

        JwTheme(darkTheme = isSystemInDarkTheme()) {
            when (applicationState) {
                ApplicationLifecycleOwner.ApplicationState.INITIALIZING ->
                    InitializingDialog(verifyingTrustRegistry = verifyingTrustRegistry)

                ApplicationLifecycleOwner.ApplicationState.STOPPING -> ShuttingDownDialog()

                ApplicationLifecycleOwner.ApplicationState.NONE,
                ApplicationLifecycleOwner.ApplicationState.INITIALIZED,
                ApplicationLifecycleOwner.ApplicationState.STOPPED,
                -> Unit
            }
        }

        CompositionLocalProvider(LocalMainWindowMenuCommands provides mainWindowMenuCommands) {
            context(appGraph) {
                JetWhaleApp(
                    menuBar = {
                        if (isMac) {
                            MenuBar {
                                MainWindowMenus(pluginMenuItems = mainWindowMenuCommands.pluginMenuItems, onGoHome = mainWindowMenuCommands::goHome, onOpenLogViewer = mainWindowMenuCommands::openLogViewer, onOpenPlugin = mainWindowMenuCommands::openPlugin)
                            }
                        }
                    },
                )
            }
        }
    }
}

/**
 * Puts the host behind the macOS application menu's About, Settings… and Quit, which macOS shows
 * only once a handler is installed. A quit there is answered through [systemQuitResponse] after the
 * host's own shutdown.
 */
@Composable
private fun MacApplicationMenuEffect(mainWindowMenuCommands: MainWindowMenuCommands, systemQuitResponse: AtomicReference<QuitResponse?>) {
    DisposableEffect(mainWindowMenuCommands) {
        val desktop = Desktop.getDesktop()
        desktop.setAboutHandler { mainWindowMenuCommands.openInfo() }
        desktop.setPreferencesHandler { mainWindowMenuCommands.openSettings() }
        desktop.setQuitHandler { _, response ->
            systemQuitResponse.set(response)
            mainWindowMenuCommands.quit()
        }
        onDispose {
            desktop.setAboutHandler(null)
            desktop.setPreferencesHandler(null)
            desktop.setQuitHandler(null)
        }
    }
}

/**
 * Raises or lowers the root logger for this launch.
 *
 * Applied only when `--log-level` was passed. `logback.xml` configures the root at `trace`, so
 * defaulting this would silently reduce what the host logs — and so what the log viewer can show —
 * for every launch that never asked.
 *
 * Set before the graph is built, so nothing constructed there logs at the old level first.
 */
private fun applyLogLevel(level: JetWhaleLogLevel) {
    val root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)
    if (root !is LogbackLogger) {
        return
    }
    root.level = when (level) {
        JetWhaleLogLevel.DEBUG -> Level.DEBUG
        JetWhaleLogLevel.INFO -> Level.INFO
        JetWhaleLogLevel.WARN -> Level.WARN
        JetWhaleLogLevel.ERROR -> Level.ERROR
    }
}
