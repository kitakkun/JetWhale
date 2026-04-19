package com.kitakkun.jetwhale.host.model

import kotlinx.coroutines.flow.StateFlow

interface DebuggerSettingsRepository {
    val adbAutoPortMappingEnabledFlow: StateFlow<Boolean>
    val persistDataFlow: StateFlow<Boolean>
    val serverPortFlow: StateFlow<Int>
    val mcpServerPortFlow: StateFlow<Int>
    suspend fun readServerPort(): Int
    suspend fun readMcpServerPort(): Int
    suspend fun updatePersistData(enabled: Boolean)
    suspend fun updateAdbAutoPortMappingEnabled(enabled: Boolean)
    suspend fun updateServerPort(port: Int)
    suspend fun updateMcpServerPort(port: Int)
}
