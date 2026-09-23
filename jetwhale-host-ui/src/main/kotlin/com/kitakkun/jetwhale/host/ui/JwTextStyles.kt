package com.kitakkun.jetwhale.host.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The type scale of a tool window: seven styles, sized for 13sp body text. Obtain the default
 * with [JwTextStyles.default] and adjust with [copy]; the host applies it through [JwTheme] and a
 * plugin reads it back as [JwTheme.textStyles].
 *
 * None of the styles carries a color: text takes the content color of what it sits in, or the
 * `color` passed to [JwText].
 *
 * @property title The name of a pane or a dialog: the toolbar title, a dialog title.
 * @property subtitle A heading inside a pane: a panel's header, a detail view's subject.
 * @property body Running text and row labels.
 * @property bodySmall Secondary lines: descriptions, timestamps, status.
 * @property label Control labels: buttons, tabs, menu items.
 * @property labelSmall Small labels: tags, counts, section headers, form-field names.
 * @property code Monospace: identifiers, URLs, JSON and anything else read character by character.
 */
@Immutable
public class JwTextStyles internal constructor(
    public val title: TextStyle,
    public val subtitle: TextStyle,
    public val body: TextStyle,
    public val bodySmall: TextStyle,
    public val label: TextStyle,
    public val labelSmall: TextStyle,
    public val code: TextStyle,
) {
    /** A copy with the given styles replaced. Every parameter defaults to this instance's value. */
    public fun copy(
        title: TextStyle = this.title,
        subtitle: TextStyle = this.subtitle,
        body: TextStyle = this.body,
        bodySmall: TextStyle = this.bodySmall,
        label: TextStyle = this.label,
        labelSmall: TextStyle = this.labelSmall,
        code: TextStyle = this.code,
    ): JwTextStyles = JwTextStyles(
        title = title,
        subtitle = subtitle,
        body = body,
        bodySmall = bodySmall,
        label = label,
        labelSmall = labelSmall,
        code = code,
    )

    override fun equals(other: Any?): Boolean = other is JwTextStyles &&
        title == other.title &&
        subtitle == other.subtitle &&
        body == other.body &&
        bodySmall == other.bodySmall &&
        label == other.label &&
        labelSmall == other.labelSmall &&
        code == other.code

    override fun hashCode(): Int {
        var result = title.hashCode()
        for (style in arrayOf(subtitle, body, bodySmall, label, labelSmall, code)) result = 31 * result + style.hashCode()
        return result
    }

    public companion object {
        /** The default scale, on the platform's sans-serif and monospace fonts. The same instance every call. */
        public fun default(): JwTextStyles = Default

        private val Default: JwTextStyles = JwTextStyles(
            title = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
            subtitle = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp),
            body = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
            bodySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
            label = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
            labelSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp),
            code = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
        )
    }
}
