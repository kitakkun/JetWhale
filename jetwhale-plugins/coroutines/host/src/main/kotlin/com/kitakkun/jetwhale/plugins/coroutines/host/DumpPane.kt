package com.kitakkun.jetwhale.plugins.coroutines.host

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwCodeBlock
import com.kitakkun.jetwhale.host.ui.JwEmptyState
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTextField
import com.kitakkun.jetwhale.host.ui.JwTheme
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

            else -> DumpText(text = dump.text, onDump = onDump)
        }
    }
}

@Composable
private fun DumpText(text: String, onDump: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val shown = filterDump(text, query)
    Row(horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium), verticalAlignment = Alignment.CenterVertically) {
        JwButton(text = "Dump again", onClick = onDump)
        JwTextField(value = query, onValueChange = { query = it }, placeholder = "Only coroutines whose stack contains…", modifier = Modifier.width(DumpFilterWidth))
        JwText(text = "${countDumpedCoroutines(shown)} of ${countDumpedCoroutines(text)} coroutines", style = JwTheme.textStyles.labelSmall, color = JwTheme.colors.textSecondary)
    }
    JwCodeBlock(text = shown, modifier = Modifier.fillMaxSize().padding(top = JwSpacing.medium).verticalScroll(rememberScrollState()), copyLabel = "Copy dump")
}

private val DumpFilterWidth = 320.dp

/**
 * [text], a DebugProbes dump, with only the coroutines whose block contains [query], ignoring case;
 * the dump's header stays. A dump lists the libraries' coroutines too — the agent's own, the HTTP
 * client's — so the app's are easy to lose among them.
 */
internal fun filterDump(text: String, query: String): String {
    if (query.isBlank()) return text
    val blocks = text.split(DumpBlockSeparator)
    val (header, coroutines) = blocks.partition { !it.startsWith(DUMP_COROUTINE_PREFIX) }
    return (header + coroutines.filter { it.contains(query, ignoreCase = true) }).joinToString("\n\n")
}

internal fun countDumpedCoroutines(text: String): Int = text.split(DumpBlockSeparator).count { it.startsWith(DUMP_COROUTINE_PREFIX) }

private val DumpBlockSeparator = Regex("\n\\s*\n")

private const val DUMP_COROUTINE_PREFIX = "Coroutine "

private const val INSTALL_DEBUG_PROBES_SNIPPET = """// build.gradle.kts: debugImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-debug:<version>")
DebugProbes.install()"""
