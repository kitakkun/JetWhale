package com.kitakkun.jetwhale.host.model

data class DebuggerBehaviorSettings(
    val adbAutoPortMappingEnabled: Boolean,
    val checkForUpdatesOnStartup: Boolean,
    val persistData: Boolean,
    val serverPort: Int,
    val mcpServerPort: Int,
    val wssPort: Int,
    val wssEnabled: Boolean,
)
