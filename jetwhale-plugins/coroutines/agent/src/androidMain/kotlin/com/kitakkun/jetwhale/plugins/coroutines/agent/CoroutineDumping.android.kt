package com.kitakkun.jetwhale.plugins.coroutines.agent

import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineDetail
import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineDump
import kotlinx.coroutines.Job

// DebugProbes.install() fails on Android: ByteBuddy's self-attach reads the VM name through
// java.lang.management.ManagementFactory, which ART does not have.
private const val NO_DEBUG_PROBES = "DebugProbes cannot be installed on Android: they attach a JVM agent through ByteBuddy, which Android's runtime does not support"

internal actual fun dumpCoroutines(): CoroutineDump = CoroutineDump(text = "", unavailableReason = NO_DEBUG_PROBES)

internal actual fun describeCoroutine(id: String, job: Job): CoroutineDetail = CoroutineDetail(id = id, found = true, debugState = null, suspensionStack = emptyList(), creationStack = emptyList(), stackUnavailableReason = NO_DEBUG_PROBES)
