package com.kitakkun.jetwhale.host.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Every color the components draw with, in the vocabulary of a tool window rather than Material's
 * roles: surfaces for panes, text emphasis levels, one accent, and the semantic tones a debugger
 * reports in.
 *
 * Obtain one from [light] or [dark] and adjust it with [copy]; there is no public constructor, so
 * the set can grow without breaking callers. The host maps its configured theme onto this and
 * applies it through [JwTheme]; a plugin reads it back as [JwTheme.colors].
 */
@Immutable
public class JwColors internal constructor(
    /** The background of content panes. */
    public val surface: Color,
    /** Body text and icons on [surface] and the other surfaces. */
    public val onSurface: Color,
    /** De-emphasized text that must still be read: descriptions, placeholders, counts, hints. */
    public val textSecondary: Color,
    /**
     * Text of a disabled control — and only that. It sits below the contrast text needs, as a
     * disabled control may; a placeholder, a count or a hint is [textSecondary].
     */
    public val textDisabled: Color,
    /** Background of the sidebar and other secondary panes beside the main content. */
    public val sidebarBackground: Color,
    /** Background of toolbars and headers that sit on top of the content they control. */
    public val toolbarBackground: Color,
    /** Background of a panel or an input: the lightest surface, so it reads as a sheet on the pane. */
    public val panelBackground: Color,
    /** Background of a menu or a dialog floating over the content. */
    public val elevatedBackground: Color,
    /** Hairline borders between panes and around panels. Decorative: lighter than a control's edge. */
    public val border: Color,
    /** The edge of a control — an input, a secondary button — strong enough to find the control by. */
    public val controlBorder: Color,
    /** Background of a row the pointer is hovering. */
    public val hover: Color,
    /** Background of the selected row in a list or sidebar. */
    public val selection: Color,
    /** Text on [selection]. */
    public val onSelection: Color,
    /** The one accent: primary buttons, links, the selected tab's underline, the focus ring. */
    public val accent: Color,
    /** Text or icon on [accent]. */
    public val onAccent: Color,
    /** Soft accent background for a tinted tag or banner. */
    public val accentContainer: Color,
    /** Text or icon on [accentContainer]. */
    public val onAccentContainer: Color,
    /** Soft neutral background: a [JwTone.Neutral] tag or banner, an unfilled count badge. */
    public val neutralContainer: Color,
    /** Strong red: a failure, a 4xx/5xx status, a destructive action. */
    public val error: Color,
    /** Text or icon on [error]. */
    public val onError: Color,
    /** Soft red background for a tinted tag or banner. */
    public val errorContainer: Color,
    /** Text or icon on [errorContainer]. */
    public val onErrorContainer: Color,
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
    /** Strong blue: neutral information, a 3xx status. Usually the same as [accent]. */
    public val info: Color,
    /** Text or icon on [info]. */
    public val onInfo: Color,
    /** Soft blue background for a tinted tag or banner. */
    public val infoContainer: Color,
    /** Text or icon on [infoContainer]. */
    public val onInfoContainer: Color,
    /**
     * Marks what an AI agent is operating right now. Deliberately the same in every scheme: it has
     * to stand out against the accent-tinted selection of the very row it decorates.
     */
    public val aiAccent: Color,
    /** Text or icon on [aiAccent]. */
    public val onAiAccent: Color,
    /** Background of a tooltip: the inverse of the surfaces, so it floats. */
    public val tooltipBackground: Color,
    /** Text on [tooltipBackground]. */
    public val onTooltip: Color,
    /** Whether this is a dark scheme; decides which built-in scheme fills what a theme leaves out. */
    public val isDark: Boolean,
) {
    /** A copy with the given colors replaced. Every parameter defaults to this instance's value. */
    public fun copy(
        surface: Color = this.surface,
        onSurface: Color = this.onSurface,
        textSecondary: Color = this.textSecondary,
        textDisabled: Color = this.textDisabled,
        sidebarBackground: Color = this.sidebarBackground,
        toolbarBackground: Color = this.toolbarBackground,
        panelBackground: Color = this.panelBackground,
        elevatedBackground: Color = this.elevatedBackground,
        border: Color = this.border,
        controlBorder: Color = this.controlBorder,
        hover: Color = this.hover,
        selection: Color = this.selection,
        onSelection: Color = this.onSelection,
        accent: Color = this.accent,
        onAccent: Color = this.onAccent,
        accentContainer: Color = this.accentContainer,
        onAccentContainer: Color = this.onAccentContainer,
        neutralContainer: Color = this.neutralContainer,
        error: Color = this.error,
        onError: Color = this.onError,
        errorContainer: Color = this.errorContainer,
        onErrorContainer: Color = this.onErrorContainer,
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
        tooltipBackground: Color = this.tooltipBackground,
        onTooltip: Color = this.onTooltip,
        isDark: Boolean = this.isDark,
    ): JwColors = JwColors(
        surface = surface,
        onSurface = onSurface,
        textSecondary = textSecondary,
        textDisabled = textDisabled,
        sidebarBackground = sidebarBackground,
        toolbarBackground = toolbarBackground,
        panelBackground = panelBackground,
        elevatedBackground = elevatedBackground,
        border = border,
        controlBorder = controlBorder,
        hover = hover,
        selection = selection,
        onSelection = onSelection,
        accent = accent,
        onAccent = onAccent,
        accentContainer = accentContainer,
        onAccentContainer = onAccentContainer,
        neutralContainer = neutralContainer,
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
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
        tooltipBackground = tooltipBackground,
        onTooltip = onTooltip,
        isDark = isDark,
    )

    override fun equals(other: Any?): Boolean = other is JwColors &&
        surface == other.surface &&
        onSurface == other.onSurface &&
        textSecondary == other.textSecondary &&
        textDisabled == other.textDisabled &&
        sidebarBackground == other.sidebarBackground &&
        toolbarBackground == other.toolbarBackground &&
        panelBackground == other.panelBackground &&
        elevatedBackground == other.elevatedBackground &&
        border == other.border &&
        controlBorder == other.controlBorder &&
        hover == other.hover &&
        selection == other.selection &&
        onSelection == other.onSelection &&
        accent == other.accent &&
        onAccent == other.onAccent &&
        accentContainer == other.accentContainer &&
        onAccentContainer == other.onAccentContainer &&
        neutralContainer == other.neutralContainer &&
        error == other.error &&
        onError == other.onError &&
        errorContainer == other.errorContainer &&
        onErrorContainer == other.onErrorContainer &&
        success == other.success &&
        onSuccess == other.onSuccess &&
        successContainer == other.successContainer &&
        onSuccessContainer == other.onSuccessContainer &&
        warning == other.warning &&
        onWarning == other.onWarning &&
        warningContainer == other.warningContainer &&
        onWarningContainer == other.onWarningContainer &&
        info == other.info &&
        onInfo == other.onInfo &&
        infoContainer == other.infoContainer &&
        onInfoContainer == other.onInfoContainer &&
        aiAccent == other.aiAccent &&
        onAiAccent == other.onAiAccent &&
        tooltipBackground == other.tooltipBackground &&
        onTooltip == other.onTooltip &&
        isDark == other.isDark

    override fun hashCode(): Int {
        var result = isDark.hashCode()
        for (color in arrayOf(
            surface, onSurface, textSecondary, textDisabled,
            sidebarBackground, toolbarBackground, panelBackground, elevatedBackground,
            border, controlBorder, hover, selection, onSelection,
            accent, onAccent, accentContainer, onAccentContainer, neutralContainer,
            error, onError, errorContainer, onErrorContainer,
            success, onSuccess, successContainer, onSuccessContainer,
            warning, onWarning, warningContainer, onWarningContainer,
            info, onInfo, infoContainer, onInfoContainer,
            aiAccent, onAiAccent, tooltipBackground, onTooltip,
        )) {
            result = 31 * result + color.hashCode()
        }
        return result
    }

    public companion object {
        /**
         * The built-in light scheme: white surfaces, near-black text, blue accent. The same
         * instance every call.
         */
        public fun light(): JwColors = Light

        /**
         * The built-in dark scheme: charcoal surfaces, off-white text, lighter blue accent. The same
         * instance every call.
         */
        public fun dark(): JwColors = Dark

        private val Light: JwColors = JwColors(
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF1D1F23),
            textSecondary = Color(0xFF5D6270),
            textDisabled = Color(0xFF1D1F23).copy(alpha = 0.38f),
            sidebarBackground = Color(0xFFF7F8FA),
            toolbarBackground = Color(0xFFFFFFFF),
            panelBackground = Color(0xFFFFFFFF),
            elevatedBackground = Color(0xFFF1F2F5),
            border = Color(0xFFDFE2E8),
            controlBorder = Color(0xFF868B97),
            hover = Color(0xFF1D1F23).copy(alpha = 0.05f),
            selection = Color(0xFF2F6FE4).copy(alpha = 0.14f),
            onSelection = Color(0xFF1D1F23),
            accent = Color(0xFF2F6FE4),
            onAccent = Color(0xFFFFFFFF),
            accentContainer = Color(0xFFDCE7FB),
            onAccentContainer = Color(0xFF0B2F6B),
            neutralContainer = Color(0xFFEAECF0),
            error = Color(0xFFD93025),
            onError = Color(0xFFFFFFFF),
            errorContainer = Color(0xFFFBE0DD),
            onErrorContainer = Color(0xFF5F1410),
            success = Color(0xFF1B8038),
            onSuccess = Color(0xFFFFFFFF),
            successContainer = Color(0xFFD9F0DE),
            onSuccessContainer = Color(0xFF0B3D18),
            warning = Color(0xFF9F5E00),
            onWarning = Color(0xFFFFFFFF),
            warningContainer = Color(0xFFFBEBCF),
            onWarningContainer = Color(0xFF4A2C00),
            info = Color(0xFF2F6FE4),
            onInfo = Color(0xFFFFFFFF),
            infoContainer = Color(0xFFDCE7FB),
            onInfoContainer = Color(0xFF0B2F6B),
            aiAccent = Color(0xFFFF8A00),
            onAiAccent = Color(0xFF000000),
            tooltipBackground = Color(0xFF2D2F34),
            onTooltip = Color(0xFFF1F2F5),
            isDark = false,
        )

        private val Dark: JwColors = JwColors(
            surface = Color(0xFF1E1F22),
            onSurface = Color(0xFFE6E7EA),
            textSecondary = Color(0xFFA5A9B4),
            textDisabled = Color(0xFFE6E7EA).copy(alpha = 0.38f),
            sidebarBackground = Color(0xFF222327),
            toolbarBackground = Color(0xFF1E1F22),
            panelBackground = Color(0xFF191A1D),
            elevatedBackground = Color(0xFF27282C),
            border = Color(0xFF3A3D45),
            controlBorder = Color(0xFF747986),
            hover = Color(0xFFE6E7EA).copy(alpha = 0.08f),
            selection = Color(0xFF6A9BF5).copy(alpha = 0.24f),
            onSelection = Color(0xFFE6E7EA),
            accent = Color(0xFF6A9BF5),
            onAccent = Color(0xFF0B2247),
            accentContainer = Color(0xFF1F3A6B),
            onAccentContainer = Color(0xFFD6E3FF),
            neutralContainer = Color(0xFF2D2F34),
            error = Color(0xFFF0655D),
            onError = Color(0xFF3B0907),
            errorContainer = Color(0xFF5F1410),
            onErrorContainer = Color(0xFFFBE0DD),
            success = Color(0xFF5DBB63),
            onSuccess = Color(0xFF07300F),
            successContainer = Color(0xFF1B4423),
            onSuccessContainer = Color(0xFFC9EFD0),
            warning = Color(0xFFE5A43B),
            onWarning = Color(0xFF3A2400),
            warningContainer = Color(0xFF4A3410),
            onWarningContainer = Color(0xFFFFE2B0),
            info = Color(0xFF6A9BF5),
            onInfo = Color(0xFF0B2247),
            infoContainer = Color(0xFF1F3A6B),
            onInfoContainer = Color(0xFFD6E3FF),
            aiAccent = Color(0xFFFF8A00),
            onAiAccent = Color(0xFF000000),
            tooltipBackground = Color(0xFFE6E7EA),
            onTooltip = Color(0xFF2D2F34),
            isDark = true,
        )
    }
}
