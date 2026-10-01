package com.kitakkun.jetwhale.host.settings.component

import com.kitakkun.jetwhale.host.model.InstalledPluginVersion
import kotlinx.collections.immutable.ImmutableList

/** @property versions Every installed version of the plugin, newest first. */
data class PluginInfoUiState(
    val id: String,
    val name: String,
    val versions: ImmutableList<InstalledPluginVersion>,
)
