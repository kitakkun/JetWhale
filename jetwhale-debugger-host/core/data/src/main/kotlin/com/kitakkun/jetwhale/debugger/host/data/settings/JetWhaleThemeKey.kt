package com.kitakkun.jetwhale.debugger.host.data.settings

import kotlinx.serialization.Serializable

@Serializable
sealed interface JetWhaleThemeKey {
    companion object {
        val BuiltInDynamic = DynamicThemeKey(
            lightThemeId = "jetwhale_built_in_light",
            darkThemeId = "jetwhale_built_in_dark",
        )
        val BuiltInStaticLight = StaticThemeKey(
            themeId = "jetwhale_built_in_light",
        )
        val BuiltInStaticDark = StaticThemeKey(
            themeId = "jetwhale_built_in_dark",
        )
    }
}

@Serializable
data class DynamicThemeKey(
    val lightThemeId: String,
    val darkThemeId: String,
) : JetWhaleThemeKey

@Serializable
data class StaticThemeKey(
    val themeId: String,
) : JetWhaleThemeKey