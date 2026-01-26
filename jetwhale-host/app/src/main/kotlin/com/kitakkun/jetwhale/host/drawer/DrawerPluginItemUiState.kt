package com.kitakkun.jetwhale.host.drawer

import com.kitakkun.jetwhale.host.model.PluginAvailability
import com.kitakkun.jetwhale.host.model.PluginIconResource

data class DrawerPluginItemUiState(
    val name: String,
    val id: String,
    val activeIconResource: PluginIconResource?,
    val inactiveIconResource: PluginIconResource?,
    val pluginAvailability: PluginAvailability,
)
