package com.kitakkun.jetwhale.host.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.launch
import java.awt.datatransfer.StringSelection

/**
 * A read-only block of monospace text — a response body, a stack trace, a JSON key, a command to
 * paste — in a tinted, bordered box. The text is selectable; [copyLabel] adds a button that copies
 * all of it.
 *
 * @param text the text, shown as [JwTheme.textStyles.code]. Pass an [AnnotatedString] to keep syntax
 * coloring.
 * @param wrap `true` wraps long lines to the box; `false` keeps them whole and scrolls sideways,
 * which is right for code and URLs.
 * @param maxLines the most lines shown before the text is cut with an ellipsis — for a preview
 * that should not grow with its content.
 * @param copyLabel the copy button's tooltip and accessibility name; null for no button.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
public fun JwCodeBlock(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    wrap: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    copyLabel: String? = null,
) {
    val shape = JwShapes.small
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(JwTheme.colors.sidebarBackground, shape)
            .border(JwMetrics.borderWidth, JwTheme.colors.border, shape),
    ) {
        val scrolling = if (wrap) Modifier else Modifier.horizontalScroll(rememberScrollState())
        SelectionContainer(
            modifier = Modifier
                .padding(JwSpacing.large)
                .then(if (copyLabel != null) Modifier.padding(end = JwIconButtonDefaults.inlineSize + JwSpacing.small) else Modifier)
                .then(scrolling),
        ) {
            JwText(
                text = text,
                style = JwTheme.textStyles.code,
                softWrap = wrap,
                maxLines = maxLines,
                // Ellipsis together with softWrap = false makes Compose lay the text out on one
                // line, dropping hard line breaks; an unwrapped block scrolls instead, so it clips.
                overflow = if (wrap) TextOverflow.Ellipsis else TextOverflow.Clip,
            )
        }
        if (copyLabel != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(JwSpacing.extraSmall),
            ) {
                JwIconButton(
                    onClick = {
                        scope.launch { clipboard.setClipEntry(ClipEntry(StringSelection(text.text))) }
                    },
                    tooltip = copyLabel,
                    size = JwIconButtonDefaults.inlineSize,
                ) {
                    JwIcon(imageVector = JwIcons.Copy, contentDescription = null)
                }
            }
        }
    }
}

/** [JwCodeBlock] for plain text. */
@Composable
public fun JwCodeBlock(
    text: String,
    modifier: Modifier = Modifier,
    wrap: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    copyLabel: String? = null,
) {
    JwCodeBlock(
        text = AnnotatedString(text),
        modifier = modifier,
        wrap = wrap,
        maxLines = maxLines,
        copyLabel = copyLabel,
    )
}
