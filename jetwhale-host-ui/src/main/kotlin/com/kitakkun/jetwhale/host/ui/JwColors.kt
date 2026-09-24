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
 *
 * @property surface The background of content panes.
 * @property onSurface Body text and icons on [surface] and the other surfaces.
 * @property textSecondary De-emphasized text that must still be read: descriptions, placeholders,
 * counts, hints.
 * @property textDisabled Text of a disabled control — and only that. It sits below the contrast text
 * needs, as a disabled control may; a placeholder, a count or a hint is [textSecondary].
 * @property sidebarBackground Background of the sidebar and other secondary panes beside the main
 * content.
 * @property toolbarBackground Background of toolbars and headers that sit on top of the content they
 * control.
 * @property panelBackground Background of a panel or an input: the lightest surface, so it reads as
 * a sheet on the pane.
 * @property elevatedBackground Background of a menu or a dialog floating over the content.
 * @property border Hairline borders between panes and around panels. Decorative: lighter than a
 * control's edge.
 * @property controlBorder The edge of a control — an input, a secondary button — strong enough to
 * find the control by.
 * @property hover Background of a row the pointer is hovering.
 * @property selection Background of the selected row in a list or sidebar.
 * @property onSelection Text on [selection].
 * @property accent The one accent: primary buttons, links, the selected tab's underline, the focus
 * ring.
 * @property onAccent Text or icon on [accent].
 * @property accentContainer Soft accent background for a tinted tag or banner.
 * @property onAccentContainer Text or icon on [accentContainer].
 * @property neutralContainer Soft neutral background: a [JwTone.Neutral] tag or banner, an unfilled
 * count badge.
 * @property error Strong red: a failure, a 4xx/5xx status, a destructive action.
 * @property onError Text or icon on [error].
 * @property errorContainer Soft red background for a tinted tag or banner.
 * @property onErrorContainer Text or icon on [errorContainer].
 * @property success Strong green: a passing state, a healthy connection, a 2xx status.
 * @property onSuccess Text or icon on [success].
 * @property successContainer Soft green background for a tinted tag or banner.
 * @property onSuccessContainer Text or icon on [successContainer].
 * @property warning Strong amber: something to look at, not yet an error.
 * @property onWarning Text or icon on [warning].
 * @property warningContainer Soft amber background for a tinted tag or banner.
 * @property onWarningContainer Text or icon on [warningContainer].
 * @property info Strong blue: neutral information, a 3xx status. Usually the same as [accent].
 * @property onInfo Text or icon on [info].
 * @property infoContainer Soft blue background for a tinted tag or banner.
 * @property onInfoContainer Text or icon on [infoContainer].
 * @property aiAccent Marks what an AI agent is operating right now. Deliberately the same in every
 * scheme: it has to stand out against the accent-tinted selection of the very row it decorates.
 * @property onAiAccent Text or icon on [aiAccent].
 * @property tooltipBackground Background of a tooltip: the inverse of the surfaces, so it floats.
 * @property onTooltip Text on [tooltipBackground].
 * @property isDark Whether this is a dark scheme; decides which built-in scheme fills what a theme
 * leaves out.
 */
@Immutable
public class JwColors internal constructor(
    public val surface: Color,
    public val onSurface: Color,
    public val textSecondary: Color,
    public val textDisabled: Color,
    public val sidebarBackground: Color,
    public val toolbarBackground: Color,
    public val panelBackground: Color,
    public val elevatedBackground: Color,
    public val border: Color,
    public val controlBorder: Color,
    public val hover: Color,
    public val selection: Color,
    public val onSelection: Color,
    public val accent: Color,
    public val onAccent: Color,
    public val accentContainer: Color,
    public val onAccentContainer: Color,
    public val neutralContainer: Color,
    public val error: Color,
    public val onError: Color,
    public val errorContainer: Color,
    public val onErrorContainer: Color,
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
    public val aiAccent: Color,
    public val onAiAccent: Color,
    public val tooltipBackground: Color,
    public val onTooltip: Color,
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
