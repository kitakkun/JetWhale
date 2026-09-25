package com.kitakkun.jetwhale.plugins.coroutines.host

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwCodeBlock
import com.kitakkun.jetwhale.host.ui.JwEmptyState
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineDump

@Composable
internal fun DumpPane(dump: CoroutineDump?, onDump: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(JwSpacing.large)) {
        when {
            dump == null -> JwEmptyState(
                title = "No dump yet",
                description = "A dump lists every coroutine with the stack it is suspended at — the fastest way to see where a stuck coroutine waits. It needs kotlinx-coroutines-debug's DebugProbes, which only an app on the JVM can install. To see one coroutine's stack, select it in the Coroutines tab.",
                action = { JwButton(text = "Dump coroutines", onClick = onDump) },
            )

            dump.unavailableReason != null -> JwEmptyState(
                title = "This app cannot dump its coroutines",
                description = "${dump.unavailableReason}. On the JVM, add kotlinx-coroutines-debug to the app and install DebugProbes before its first coroutine starts:",
                action = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(JwSpacing.medium)) {
                        JwCodeBlock(text = INSTALL_DEBUG_PROBES_SNIPPET, copyLabel = "Copy")
                        JwButton(text = "Try again", onClick = onDump)
                    }
                },
            )

            else -> {
                JwButton(text = "Dump again", onClick = onDump)
                JwCodeBlock(text = dump.text, modifier = Modifier.fillMaxSize().padding(top = JwSpacing.medium).verticalScroll(rememberScrollState()), copyLabel = "Copy dump")
            }
        }
    }
}

private const val INSTALL_DEBUG_PROBES_SNIPPET = """// build.gradle.kts: debugImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-debug:<version>")
DebugProbes.install()"""
