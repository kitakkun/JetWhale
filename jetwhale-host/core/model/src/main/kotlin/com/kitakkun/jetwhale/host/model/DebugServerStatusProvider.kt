package com.kitakkun.jetwhale.host.model

import kotlinx.coroutines.flow.StateFlow

/** Exposes the current debug server status so collaborators can react to lifecycle transitions. */
interface DebugServerStatusProvider {
    val statusFlow: StateFlow<DebugWebSocketServerStatus>
}
