package com.kitakkun.jetwhale.host.model

sealed interface McpServerStatus {
    data object Stopped : McpServerStatus
    data object Starting : McpServerStatus
    data class Running(val host: String, val port: Int) : McpServerStatus
    data object Stopping : McpServerStatus
    data class Error(val message: String) : McpServerStatus
}
