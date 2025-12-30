package com.kitakkun.jetwhale.debugger.host.model

import kotlinx.collections.immutable.ImmutableList

data class AppearanceSettings(
    val appLanguage: AppLanguage,
    val activeColorScheme: JetWhaleColorSchemeId,
    val availableColorSchemes: ImmutableList<JetWhaleColorSchemeId>,
)
