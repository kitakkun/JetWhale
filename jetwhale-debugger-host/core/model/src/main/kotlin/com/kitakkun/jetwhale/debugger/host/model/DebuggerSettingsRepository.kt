package com.kitakkun.jetwhale.debugger.host.model

import kotlinx.coroutines.flow.StateFlow

interface DebuggerSettingsRepository {
    val adbAutoPortMappingEnabledFlow: StateFlow<Boolean>
    val persistDataFlow: StateFlow<Boolean>
    suspend fun updatePersistData(enabled: Boolean)
    suspend fun updateAdbAutoPortMappingEnabled(enabled: Boolean)
}
