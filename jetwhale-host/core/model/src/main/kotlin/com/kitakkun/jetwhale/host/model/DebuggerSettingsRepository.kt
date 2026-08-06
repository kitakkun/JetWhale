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
    val followAiOperationEnabledFlow: StateFlow<Boolean>

    /**
     * The stored values, awaiting the initial read rather than reporting the default while it is
     * still unknown. Decide from these: [adbAutoPortMappingEnabledFlow] and [wssEnabledFlow] carry
     * the default until the store answers, which is fine to display but not to act on.
     */
    suspend fun readAdbAutoPortMappingEnabled(): Boolean
    suspend fun readWssEnabled(): Boolean

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
    suspend fun updateFollowAiOperationEnabled(enabled: Boolean)

    /**
     * Stores everything the debug server binds from in a single write, so a collector never observes
     * a half-applied configuration — a ws port paired with the wss port it is about to replace.
     */
    suspend fun updateDebugServerSettings(serverPort: Int, wssPort: Int, wssEnabled: Boolean)
}
