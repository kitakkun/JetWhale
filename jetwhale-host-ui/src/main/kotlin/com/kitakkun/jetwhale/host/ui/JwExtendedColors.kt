package com.kitakkun.jetwhale.host.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Colors the host and its plugins need that Material 3's [ColorScheme] has no slot for: the chrome
 * of a tool window and the semantic tones a debugger reports in. They are derived from the applied
 * [ColorScheme] wherever they can be, so a custom theme keeps a coherent look without declaring them.
 */
@Immutable
public class JwExtendedColors(
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
    public val success: Color,
    public val onSuccess: Color,
    public val successContainer: Color,
    public val onSuccessContainer: Color,
    public val warning: Color,
    public val onWarning: Color,
    public val warningContainer: Color,
    public val onWarningContainer: Color,
    public val info: Color,
    public val onInfo: Color,
    public val infoContainer: Color,
    public val onInfoContainer: Color,
    /**
     * Marks what an AI agent is operating right now. Deliberately not derived from the scheme: it
     * has to stand out against the accent-tinted selection of the very row it decorates.
     */
    public val aiAccent: Color,
    public val onAiAccent: Color,
) {
    public companion object {
        /** Derives the extended colors from [colorScheme], picking the fixed tones for [darkTheme]. */
        public fun from(colorScheme: ColorScheme, darkTheme: Boolean): JwExtendedColors = JwExtendedColors(
            sidebarBackground = colorScheme.surfaceContainerLow,
            toolbarBackground = colorScheme.surface,
            border = colorScheme.outlineVariant,
            hover = colorScheme.onSurface.copy(alpha = if (darkTheme) 0.08f else 0.05f),
            selection = colorScheme.primary.copy(alpha = if (darkTheme) 0.28f else 0.14f),
            onSelection = colorScheme.onSurface,
            textSecondary = colorScheme.onSurfaceVariant,
            textDisabled = colorScheme.onSurface.copy(alpha = 0.38f),
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
