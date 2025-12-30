package com.kitakkun.jetwhale.host.model

sealed interface DebugWebSocketServerStatus {
    data object Starting : DebugWebSocketServerStatus
    data class Started(val host: String, val port: Int) : DebugWebSocketServerStatus
    data class Error(val message: String) : DebugWebSocketServerStatus
    data object Stopping : DebugWebSocketServerStatus
    data object Stopped : DebugWebSocketServerStatus
}
