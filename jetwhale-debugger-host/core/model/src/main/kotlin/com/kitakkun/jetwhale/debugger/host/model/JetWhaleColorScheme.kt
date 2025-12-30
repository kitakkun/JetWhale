package com.kitakkun.jetwhale.debugger.host.model

sealed interface JetWhaleColorScheme {
    data class Dynamic(
        val lightColorScheme: JetWhaleColorScheme,
        val darkColorScheme: JetWhaleColorScheme,
    ) : JetWhaleColorScheme {
        companion object {
            val BuiltIn = Dynamic(
                lightColorScheme = Static.Light,
                darkColorScheme = Static.Dark,
            )
        }
    }

    sealed interface Static : JetWhaleColorScheme {
        data object Light : Static
        data object Dark : Static
        data class Custom(val colors: Map<ThemeColorTokens, Int>) : Static
    }
}

enum class ThemeColorTokens {
    Primary,
    OnPrimary,
    PrimaryContainer,
    OnPrimaryContainer,
    InversePrimary,
    Secondary,
    OnSecondary,
    SecondaryContainer,
    OnSecondaryContainer,
    Tertiary,
    OnTertiary,
    TertiaryContainer,
    OnTertiaryContainer,
    Background,
    OnBackground,
    Surface,
    OnSurface,
    SurfaceVariant,
    OnSurfaceVariant,
    SurfaceTint,
    InverseSurface,
    InverseOnSurface,
    Error,
    OnError,
    ErrorContainer,
    OnErrorContainer,
    Outline,
    OutlineVariant,
    Scrim,
    SurfaceBright,
    SurfaceDim,
    SurfaceContainer,
    SurfaceContainerHigh,
    SurfaceContainerHighest,
    SurfaceContainerLow,
    SurfaceContainerLowest,
    PrimaryFixed,
    PrimaryFixedDim,
    OnPrimaryFixed,
    OnPrimaryFixedVariant,
    SecondaryFixed,
    SecondaryFixedDim,
    OnSecondaryFixed,
    OnSecondaryFixedVariant,
    TertiaryFixed,
    TertiaryFixedDim,
    OnTertiaryFixed,
    OnTertiaryFixedVariant,
}
