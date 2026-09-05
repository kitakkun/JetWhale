package com.kitakkun.jetwhale.host.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse

/**
 * Colors the host and its plugins need that Material 3's [ColorScheme] has no slot for: the chrome
 * of a tool window and the semantic tones a debugger reports in. They are derived from the applied
 * [ColorScheme] wherever they can be, so a custom theme keeps a coherent look without declaring them.
 */
@Immutable
public class JwExtendedColors internal constructor(
    /** Background of the sidebar and other secondary panes beside the main content. */
    public val sidebarBackground: Color,
    /** Background of toolbars and headers that sit on top of the content they control. */
    public val toolbarBackground: Color,
    /** Hairline borders between panes and around inputs. */
    public val border: Color,
    /** Background of a row the pointer is hovering. */
    public val hover: Color,
    /** Background of the selected row in a list or sidebar. */
    public val selection: Color,
    /** Text on [selection]. */
    public val onSelection: Color,
    /** De-emphasized text: descriptions, timestamps, secondary columns. */
    public val textSecondary: Color,
    /** Text of a disabled control. */
    public val textDisabled: Color,
    /** Strong green: a passing state, a healthy connection, a 2xx status. */
    public val success: Color,
    /** Text or icon on [success]. */
    public val onSuccess: Color,
    /** Soft green background for a tinted tag or banner. */
    public val successContainer: Color,
    /** Text or icon on [successContainer]. */
    public val onSuccessContainer: Color,
    /** Strong amber: something to look at, not yet an error. */
    public val warning: Color,
    /** Text or icon on [warning]. */
    public val onWarning: Color,
    /** Soft amber background for a tinted tag or banner. */
    public val warningContainer: Color,
    /** Text or icon on [warningContainer]. */
    public val onWarningContainer: Color,
    /** Strong blue: neutral information, a 3xx status. Usually the theme's primary. */
    public val info: Color,
    /** Text or icon on [info]. */
    public val onInfo: Color,
    /** Soft blue background for a tinted tag or banner. */
    public val infoContainer: Color,
    /** Text or icon on [infoContainer]. */
    public val onInfoContainer: Color,
    /**
     * Marks what an AI agent is operating right now. Deliberately not derived from the scheme: it
     * has to stand out against the accent-tinted selection of the very row it decorates.
     */
    public val aiAccent: Color,
    /** Text or icon on [aiAccent]. */
    public val onAiAccent: Color,
) {
    /**
     * A copy with the given colors replaced, for a theme that wants its own tones on top of the
     * derived ones. Every parameter defaults to this instance's value.
     */
    public fun copy(
        sidebarBackground: Color = this.sidebarBackground,
        toolbarBackground: Color = this.toolbarBackground,
        border: Color = this.border,
        hover: Color = this.hover,
        selection: Color = this.selection,
        onSelection: Color = this.onSelection,
        textSecondary: Color = this.textSecondary,
        textDisabled: Color = this.textDisabled,
        success: Color = this.success,
        onSuccess: Color = this.onSuccess,
        successContainer: Color = this.successContainer,
        onSuccessContainer: Color = this.onSuccessContainer,
        warning: Color = this.warning,
        onWarning: Color = this.onWarning,
        warningContainer: Color = this.warningContainer,
        onWarningContainer: Color = this.onWarningContainer,
        info: Color = this.info,
        onInfo: Color = this.onInfo,
        infoContainer: Color = this.infoContainer,
        onInfoContainer: Color = this.onInfoContainer,
        aiAccent: Color = this.aiAccent,
        onAiAccent: Color = this.onAiAccent,
    ): JwExtendedColors = JwExtendedColors(
        sidebarBackground = sidebarBackground,
        toolbarBackground = toolbarBackground,
        border = border,
        hover = hover,
        selection = selection,
        onSelection = onSelection,
        textSecondary = textSecondary,
        textDisabled = textDisabled,
        success = success,
        onSuccess = onSuccess,
        successContainer = successContainer,
        onSuccessContainer = onSuccessContainer,
        warning = warning,
        onWarning = onWarning,
        warningContainer = warningContainer,
        onWarningContainer = onWarningContainer,
        info = info,
        onInfo = onInfo,
        infoContainer = infoContainer,
        onInfoContainer = onInfoContainer,
        aiAccent = aiAccent,
        onAiAccent = onAiAccent,
    )

    /** Factory for the extended colors; [JwTheme] calls [from] for the scheme it applies. */
    public companion object {
        /**
         * Derives the extended colors from [colorScheme], picking the fixed tones for [darkTheme].
         *
         * A scheme may leave tokens [Color.Unspecified] — a user theme that declares only some of
         * them does — and Unspecified survives `copy(alpha)` as translucent black, so every derived
         * color falls back to a plain black-or-white for the theme's polarity rather than vanishing.
         */
        public fun from(colorScheme: ColorScheme, darkTheme: Boolean): JwExtendedColors {
            val ink = if (darkTheme) Color.White else Color.Black
            val paper = if (darkTheme) Color.Black else Color.White
            val onSurface = colorScheme.onSurface.takeOrElse { ink }
            val primary = colorScheme.primary.takeOrElse { if (darkTheme) Color(0xFF6A9BF5) else Color(0xFF2F6FE4) }
            return JwExtendedColors(
                sidebarBackground = colorScheme.surfaceContainerLow.takeOrElse { colorScheme.surface.takeOrElse { paper } },
                toolbarBackground = colorScheme.surface.takeOrElse { paper },
                border = colorScheme.outlineVariant.takeOrElse { onSurface.copy(alpha = 0.15f) },
                hover = onSurface.copy(alpha = if (darkTheme) 0.08f else 0.05f),
                selection = primary.copy(alpha = if (darkTheme) 0.28f else 0.14f),
                onSelection = onSurface,
                textSecondary = colorScheme.onSurfaceVariant.takeOrElse { onSurface.copy(alpha = 0.7f) },
                textDisabled = onSurface.copy(alpha = 0.38f),
                success = if (darkTheme) Color(0xFF5DBB63) else Color(0xFF1E8E3E),
                onSuccess = if (darkTheme) Color(0xFF07300F) else Color(0xFFFFFFFF),
                successContainer = if (darkTheme) Color(0xFF1B4423) else Color(0xFFD9F0DE),
                onSuccessContainer = if (darkTheme) Color(0xFFC9EFD0) else Color(0xFF0B3D18),
                warning = if (darkTheme) Color(0xFFE5A43B) else Color(0xFFB26A00),
                onWarning = if (darkTheme) Color(0xFF3A2400) else Color(0xFFFFFFFF),
                warningContainer = if (darkTheme) Color(0xFF4A3410) else Color(0xFFFBEBCF),
                onWarningContainer = if (darkTheme) Color(0xFFFFE2B0) else Color(0xFF4A2C00),
                info = if (darkTheme) Color(0xFF6A9BF5) else Color(0xFF2F6FE4),
                onInfo = if (darkTheme) Color(0xFF0B2247) else Color(0xFFFFFFFF),
                infoContainer = if (darkTheme) Color(0xFF1F3A6B) else Color(0xFFDCE7FB),
                onInfoContainer = if (darkTheme) Color(0xFFD6E3FF) else Color(0xFF0B2F6B),
                aiAccent = Color(0xFFFF8A00),
                onAiAccent = Color(0xFF000000),
            )
        }
    }
}
