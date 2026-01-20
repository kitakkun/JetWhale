package com.kitakkun.jetwhale.host.model

data class DebuggerBehaviorSettings(
    val adbAutoPortMappingEnabled: Boolean,
    val persistData: Boolean,
    val serverPort: Int,
)
