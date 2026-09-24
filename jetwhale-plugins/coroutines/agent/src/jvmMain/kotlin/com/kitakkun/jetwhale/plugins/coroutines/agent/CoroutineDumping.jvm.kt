package com.kitakkun.jetwhale.plugins.coroutines.agent

import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineDump
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.debug.DebugProbes
import java.io.ByteArrayOutputStream
import java.io.PrintStream

@OptIn(ExperimentalCoroutinesApi::class)
internal actual fun dumpCoroutines(): CoroutineDump = try {
    if (DebugProbes.isInstalled) {
        val output = ByteArrayOutputStream()
        PrintStream(output, true, Charsets.UTF_8.name()).use(DebugProbes::dumpCoroutines)
        CoroutineDump(text = output.toString(Charsets.UTF_8.name()), unavailableReason = null)
    } else {
        CoroutineDump(text = "", unavailableReason = "DebugProbes are not installed; call DebugProbes.install() when the app starts")
    }
} catch (_: NoClassDefFoundError) {
    // The library is compileOnly here: an app that did not add kotlinx-coroutines-debug has no DebugProbes class.
    CoroutineDump(text = "", unavailableReason = "kotlinx-coroutines-debug is not on the app's classpath")
}
