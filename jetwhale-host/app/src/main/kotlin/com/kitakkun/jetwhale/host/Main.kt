package com.kitakkun.jetwhale.host

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.awaitApplication
import com.kitakkun.jetwhale.host.cli.CommandLineArgumentsParser
import com.kitakkun.jetwhale.host.component.InitializingDialog
import com.kitakkun.jetwhale.host.component.ShuttingDownDialog
import com.kitakkun.jetwhale.host.di.JetWhaleAppGraph
import com.kitakkun.jetwhale.host.model.PersistedWindowState
import com.kitakkun.jetwhale.host.ui.isShortcutModifierPressed
import dev.zacsweers.metro.createGraph
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.painterResource
import java.awt.Taskbar
import javax.imageio.ImageIO

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

private val DefaultWindowSize = DpSize(1280.dp, 800.dp)

@OptIn(FlowPreview::class)
fun main(args: Array<String>) = runBlocking {
    configureAppMetadata()

    CommandLineArgumentsParser().parse(args)

    val appGraph: JetWhaleAppGraph = createGraph()

    // Start capturing logs
    appGraph.logCaptureService.startCapture()

    appGraph.applicationLifecycleOwner.initialize()

    val persistedWindowState = appGraph.windowStateRepository.loadWindowState()
    val initialWindowSize = persistedWindowState
        ?.let { DpSize(it.width.dp, it.height.dp) }
        ?: DefaultWindowSize
    val initialWindowPosition = persistedWindowState
        ?.takeIf { it.x != null && it.y != null }
        ?.let { WindowPosition(it.x!!.dp, it.y!!.dp) }
        ?: WindowPosition.Aligned(Alignment.Center)

    awaitApplication {
        val applicationState by appGraph
            .applicationLifecycleOwner
            .applicationStateFlow
            .collectAsState()

        val verifyingTrustRegistry by appGraph
            .pluginTrustService
            .verifyingTrustRegistryFlow
            .collectAsState()

        LaunchedEffect(Unit) {
            appGraph
                .applicationLifecycleOwner
                .applicationStateFlow
                .collect {
                    if (it == ApplicationLifecycleOwner.ApplicationState.STOPPED) {
                        exitApplication()
                    }
                }
        }

        val windowState = remember {
            WindowState(size = initialWindowSize, position = initialWindowPosition)
        }

        LaunchedEffect(windowState) {
            snapshotFlow { Triple(windowState.size, windowState.position, windowState.placement) }
                // Only persist normal window geometry; maximized/fullscreen must not overwrite it.
                .map { (size, position, placement) ->
                    if (placement != WindowPlacement.Floating) return@map null
                    PersistedWindowState(
                        width = size.width.value,
                        height = size.height.value,
                        x = position.takeIf { it.isSpecified }?.x?.value,
                        y = position.takeIf { it.isSpecified }?.y?.value,
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
            onPreviewKeyEvent = { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.isShortcutModifierPressed && keyEvent.key == Key.Q) {
                    appGraph.applicationLifecycleOwner.shutdown()
                    true
                } else {
                    false
                }
            },
        ) {
            when (applicationState) {
                ApplicationLifecycleOwner.ApplicationState.INITIALIZING ->
                    InitializingDialog(verifyingTrustRegistry = verifyingTrustRegistry)

                ApplicationLifecycleOwner.ApplicationState.STOPPING -> ShuttingDownDialog()

                ApplicationLifecycleOwner.ApplicationState.NONE,
                ApplicationLifecycleOwner.ApplicationState.INITIALIZED,
                ApplicationLifecycleOwner.ApplicationState.STOPPED,
                -> {
                    // show nothing
                }
            }

            context(appGraph) {
                JetWhaleApp()
            }
        }
    }
}
