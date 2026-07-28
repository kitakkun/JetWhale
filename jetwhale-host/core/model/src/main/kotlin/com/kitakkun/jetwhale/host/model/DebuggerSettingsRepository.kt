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

    /**
     * Whether an AI agent connected to the MCP server may install official plugins. Off by default:
     * installing a plugin loads new code into the host process, so it stays an opt-in.
     */
    val mcpPluginInstallAllowedFlow: StateFlow<Boolean>
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
    suspend fun updateMcpPluginInstallAllowed(enabled: Boolean)
}
