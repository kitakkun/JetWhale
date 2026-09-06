package com.kitakkun.jetwhale.host.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.kitakkun.jetwhale.host.sdk.LocalJetWhaleDarkTheme

private val LocalJwColors = staticCompositionLocalOf<JwColors> { error("JwTheme is not applied above this composable") }
private val LocalJwTextStyles = staticCompositionLocalOf<JwTextStyles> { error("JwTheme is not applied above this composable") }

/**
 * The color text and icons draw with unless told otherwise: a control that paints a background
 * provides the matching foreground here, so a [JwText] or [JwIcon] inside needs no color of its
 * own. [JwTheme] sets it to [JwColors.onSurface].
 */
public val LocalJwContentColor: ProvidableCompositionLocal<Color> = compositionLocalOf { Color.Black }

/**
 * The style [JwText] uses unless given one: [JwTheme] sets it to [JwTextStyles.body], and a control
 * whose text should read differently — a button, a tag — provides its own for its content.
 */
public val LocalJwTextStyle: ProvidableCompositionLocal<TextStyle> = compositionLocalOf { TextStyle.Default }

/**
 * The JetWhale look — the colors, type scale and shapes every Jw component draws with — for a
 * desktop tool window. It depends on nothing but Compose foundation; a Material theme can be
 * layered inside or outside it for content that still uses Material widgets.
 *
 * The host applies it around every plugin's `Content()`, so a plugin never calls this itself — it
 * reads [JwTheme.colors] and [JwTheme.textStyles]. Calling it inside a plugin is only useful to
 * deliberately re-theme a subtree, or in previews and tests.
 *
 * @param colors the scheme; the built-in ones are [JwColors.light] and [JwColors.dark]. Its
 * [JwColors.isDark] is published through [LocalJetWhaleDarkTheme].
 * @param textStyles the type scale; [JwTextStyles.default] unless the host configures otherwise.
 * @param content the UI to theme.
 */
@Composable
public fun JwTheme(
    colors: JwColors,
    textStyles: JwTextStyles = JwTextStyles.default(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalJwColors provides colors,
        LocalJwTextStyles provides textStyles,
        LocalJetWhaleDarkTheme provides colors.isDark,
        LocalJwContentColor provides colors.onSurface,
        LocalJwTextStyle provides textStyles.body,
        content = content,
    )
}

/**
 * [JwTheme] with the built-in scheme for [darkTheme]. For previews and tests of a plugin's UI,
 * where the host is not there to supply its configured scheme.
 *
 * @param darkTheme which built-in scheme to apply.
 * @param content the UI to theme.
 */
@Composable
public fun JwTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    JwTheme(colors = if (darkTheme) JwColors.dark() else JwColors.light(), content = content)
}

/** Accessors for the values the enclosing [JwTheme] applied. */
public object JwTheme {
    /** The applied colors. */
    public val colors: JwColors
        @Composable
        @ReadOnlyComposable
        get() = LocalJwColors.current

    /** The applied type scale. */
    public val textStyles: JwTextStyles
        @Composable
        @ReadOnlyComposable
        get() = LocalJwTextStyles.current

    /** Whether the applied scheme is dark; the same value as [LocalJetWhaleDarkTheme]. */
    public val isDark: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalJwColors.current.isDark
}
