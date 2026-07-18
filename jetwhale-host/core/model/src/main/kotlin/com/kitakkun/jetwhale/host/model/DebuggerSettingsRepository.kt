package com.kitakkun.jetwhale.host.model

import kotlinx.coroutines.flow.StateFlow

interface DebuggerSettingsRepository {
    val adbAutoPortMappingEnabledFlow: StateFlow<Boolean>
    val checkForUpdatesOnStartupFlow: StateFlow<Boolean>
    val persistDataFlow: StateFlow<Boolean>
    val serverPortFlow: StateFlow<Int>
    val mcpServerPortFlow: StateFlow<Int>
    val wssPortFlow: StateFlow<Int>
    val wssEnabledFlow: StateFlow<Boolean>
    suspend fun readServerPort(): Int
    suspend fun readMcpServerPort(): Int
    suspend fun readWssPort(): Int
    suspend fun updatePersistData(enabled: Boolean)
    suspend fun updateAdbAutoPortMappingEnabled(enabled: Boolean)
    suspend fun readCheckForUpdatesOnStartup(): Boolean
    suspend fun updateCheckForUpdatesOnStartup(enabled: Boolean)
    suspend fun updateServerPort(port: Int)
    suspend fun updateMcpServerPort(port: Int)
    suspend fun updateWssPort(port: Int)
    suspend fun updateWssEnabled(enabled: Boolean)
}
