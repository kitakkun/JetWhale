package com.kitakkun.jetwhale.plugins.coroutines.agent

import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineDetail
import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineDump
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.debug.DebugProbes
import java.io.ByteArrayOutputStream
import java.io.PrintStream

private const val NOT_INSTALLED = "DebugProbes are not installed; call DebugProbes.install() when the app starts"

// The library is compileOnly here: an app that did not add kotlinx-coroutines-debug has no DebugProbes class.
private const val NOT_ON_CLASSPATH = "kotlinx-coroutines-debug is not on the app's classpath"

@OptIn(ExperimentalCoroutinesApi::class)
internal actual fun dumpCoroutines(): CoroutineDump = try {
    if (DebugProbes.isInstalled) {
        val output = ByteArrayOutputStream()
        PrintStream(output, true, Charsets.UTF_8.name()).use(DebugProbes::dumpCoroutines)
        CoroutineDump(text = output.toString(Charsets.UTF_8.name()), unavailableReason = null)
    } else {
        CoroutineDump(text = "", unavailableReason = NOT_INSTALLED)
    }
} catch (_: NoClassDefFoundError) {
    CoroutineDump(text = "", unavailableReason = NOT_ON_CLASSPATH)
}

@OptIn(ExperimentalCoroutinesApi::class)
internal actual fun describeCoroutine(id: String, job: Job): CoroutineDetail {
    fun unavailable(reason: String) = CoroutineDetail(id = id, found = true, debugState = null, suspensionStack = emptyList(), creationStack = emptyList(), stackUnavailableReason = reason)
    val coroutines = try {
        if (!DebugProbes.isInstalled) return unavailable(NOT_INSTALLED)
        DebugProbes.dumpCoroutinesInfo()
    } catch (_: NoClassDefFoundError) {
        return unavailable(NOT_ON_CLASSPATH)
    }
    val info = coroutines.firstOrNull { it.job === job }
        ?: return unavailable(
            // DebugProbes follow a coroutine's own body, so they drop one whose body has returned
            // while its children still run. Only a coroutine is its own CoroutineScope.
            if (job is CoroutineScope) {
                "its own body has finished and it is waiting for its children, which have stacks of their own (or it started before DebugProbes were installed)"
            } else {
                "it is a scope's Job rather than a coroutine, so it never runs code of its own; its children have stacks"
            },
        )
    return CoroutineDetail(
        id = id,
        found = true,
        debugState = info.state.name,
        suspensionStack = info.lastObservedStackTrace().map(StackTraceElement::toString),
        creationStack = info.creationStackTrace.map(StackTraceElement::toString),
        stackUnavailableReason = null,
    )
}
