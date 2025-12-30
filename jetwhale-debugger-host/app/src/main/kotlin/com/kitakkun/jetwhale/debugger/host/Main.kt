package com.kitakkun.jetwhale.debugger.host

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.awaitApplication
import com.kitakkun.jetwhale.debugger.host.cli.CommandLineArgumentsParser
import com.kitakkun.jetwhale.debugger.host.component.InitializingDialog
import com.kitakkun.jetwhale.debugger.host.component.ShuttingDownDialog
import com.kitakkun.jetwhale.debugger.host.di.JetWhaleAppGraph
import dev.zacsweers.metro.createGraph
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) = runBlocking {
    CommandLineArgumentsParser().parse(args)

    val appGraph: JetWhaleAppGraph = createGraph()

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
            state = WindowState(position = WindowPosition.Aligned(Alignment.Center)),
            onCloseRequest = appGraph.applicationLifecycleOwner::shutdown,
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
