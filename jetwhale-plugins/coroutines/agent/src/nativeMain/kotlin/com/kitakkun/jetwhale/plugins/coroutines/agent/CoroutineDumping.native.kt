package com.kitakkun.jetwhale.plugins.coroutines.agent

import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineDetail
import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineDump
import kotlinx.coroutines.Job

private const val NO_DEBUG_PROBES = "Kotlin/Native has no DebugProbes"

internal actual fun dumpCoroutines(): CoroutineDump = CoroutineDump(text = "", unavailableReason = NO_DEBUG_PROBES)

internal actual fun describeCoroutine(id: String, job: Job): CoroutineDetail = CoroutineDetail(id = id, found = true, debugState = null, suspensionStack = emptyList(), creationStack = emptyList(), stackUnavailableReason = NO_DEBUG_PROBES)
