package com.kitakkun.jetwhale.plugins.mainthread.protocol

import com.kitakkun.jetwhale.protocol.messaging.JetWhaleRequest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The pluginId shared by the Main Thread Monitor agent and host plugins. */
const val MAIN_THREAD_PLUGIN_ID: String = "com.kitakkun.jetwhale.mainthread"

/** Asks the agent for everything it has recorded since the last reset. */
@SerialName("mainthread/get_report")
@Serializable
data object GetMainThreadReport : JetWhaleRequest<MainThreadReport>

/** Replaces the thresholds the agent records with; the recorded data is kept. */
@SerialName("mainthread/update_settings")
@Serializable
data class UpdateMonitorSettings(
    val settings: MonitorSettings,
) : JetWhaleRequest<MonitorSettings>

/** Clears every recorded task, hotspot, violation and frame. */
@SerialName("mainthread/reset")
@Serializable
data object ResetMainThreadStats : JetWhaleRequest<MainThreadReport>
