package com.kitakkun.jetwhale.host.ui

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow

/**
 * Text in the theme's type scale. Without a [style] it takes [LocalJwTextStyle] — the body style,
 * or whatever the enclosing control provides — and without a [color] it takes
 * [LocalJwContentColor], so text inside a button, a tag or a selected row is colored by the
 * control, not by the caller.
 *
 * @param text what to show.
 * @param color the text color; unspecified means the content color.
 * @param style the style; null means [LocalJwTextStyle].
 * @param fontWeight overrides the style's weight.
 * @param fontFamily overrides the style's family.
 * @param textAlign how lines align inside the text's width.
 * @param textDecoration underline or strike-through, for a link or a removed value.
 * @param onTextLayout called with each layout result, to learn whether the text was truncated.
 * @param overflow what happens past [maxLines]: ellipsis, clip, or visible.
 * @param softWrap whether lines break at the text's width; `false` keeps every line whole.
 * @param maxLines the most lines shown.
 * @param minLines the fewest lines the text reserves.
 */
@Composable
public fun JwText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    textAlign: TextAlign? = null,
    textDecoration: TextDecoration? = null,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = resolveStyle(style, color, fontWeight, fontFamily, textAlign, textDecoration),
        onTextLayout = onTextLayout,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
    )
}

/** [JwText] for an [AnnotatedString], keeping its spans — syntax coloring, emphasis. */
@Composable
public fun JwText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    textAlign: TextAlign? = null,
    textDecoration: TextDecoration? = null,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = resolveStyle(style, color, fontWeight, fontFamily, textAlign, textDecoration),
        onTextLayout = onTextLayout,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
    )
}

@Composable
private fun resolveStyle(
    style: TextStyle?,
    color: Color,
    fontWeight: FontWeight?,
    fontFamily: FontFamily?,
    textAlign: TextAlign?,
    textDecoration: TextDecoration?,
): TextStyle {
    val base = style ?: LocalJwTextStyle.current
    val resolvedColor = color.takeOrElse { base.color.takeOrElse { LocalJwContentColor.current } }
    return base.merge(
        color = resolvedColor,
        fontWeight = fontWeight ?: base.fontWeight,
        fontFamily = fontFamily ?: base.fontFamily,
        textAlign = textAlign ?: base.textAlign,
        textDecoration = textDecoration ?: base.textDecoration,
    )
}
