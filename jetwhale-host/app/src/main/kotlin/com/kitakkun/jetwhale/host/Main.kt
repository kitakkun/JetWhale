package com.kitakkun.jetwhale.host

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.awaitApplication
import com.kitakkun.jetwhale.host.cli.CommandLineArgumentsParser
import com.kitakkun.jetwhale.host.component.InitializingDialog
import com.kitakkun.jetwhale.host.component.ShuttingDownDialog
import com.kitakkun.jetwhale.host.di.JetWhaleAppGraph
import com.kitakkun.jetwhale.host.ui.isShortcutModifierPressed
import dev.zacsweers.metro.createGraph
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

fun main(args: Array<String>) = runBlocking {
    configureAppMetadata()

    CommandLineArgumentsParser().parse(args)

    val appGraph: JetWhaleAppGraph = createGraph()

    // Start capturing logs
    appGraph.logCaptureService.startCapture()

    appGraph.applicationLifecycleOwner.initialize()

    awaitApplication {
        val applicationState by appGraph
            .applicationLifecycleOwner
            .applicationStateFlow
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

        Window(
            title = "JetWhale",
            icon = painterResource(Res.drawable.app_icon),
            state = WindowState(position = WindowPosition.Aligned(Alignment.Center)),
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
                ApplicationLifecycleOwner.ApplicationState.INITIALIZING -> InitializingDialog()

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
