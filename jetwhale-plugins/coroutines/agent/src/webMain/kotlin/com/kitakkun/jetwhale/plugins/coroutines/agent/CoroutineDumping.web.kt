package com.kitakkun.jetwhale.plugins.coroutines.agent

import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineDump

internal actual fun dumpCoroutines(): CoroutineDump = CoroutineDump(text = "", unavailableReason = "Kotlin/JS and Kotlin/Wasm have no DebugProbes")
