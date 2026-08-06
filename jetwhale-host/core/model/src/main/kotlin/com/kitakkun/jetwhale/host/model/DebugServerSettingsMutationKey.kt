package com.kitakkun.jetwhale.host.model

import soil.query.MutationKey

/**
 * Applies the debug server's connection settings. The ws port, the wss port and whether the wss
 * connector is exposed at all are only read when the server binds, so they are stored and restarted
 * as one unit rather than costing a restart — and a dropped session — per field.
 */
interface DebugServerSettingsMutationKey : MutationKey<Unit, DebugServerSettings>

data class DebugServerSettings(
    val serverPort: Int,
    val wssPort: Int,
    val wssEnabled: Boolean,
)
