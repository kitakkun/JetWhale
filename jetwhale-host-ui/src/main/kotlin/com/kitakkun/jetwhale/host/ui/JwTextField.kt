package com.kitakkun.jetwhale.host.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Shrinks the clear glyph to sit inside an inline icon button. */
private val ClearIconInset = 3.dp

/**
 * A single-line or multi-line input of [JwMetrics.controlHeight] per line, with a hairline
 * border that turns accent-colored on focus and error-colored while [isError]. [placeholder] shows
 * while [value] is empty.
 *
 * @param value the current text.
 * @param onValueChange called with the new text on every edit.
 * @param enabled false greys the field out and ignores input.
 * @param readOnly keeps the field enabled — selectable, copyable — but rejects edits.
 * @param textStyle the text's style; [JwTypography.code] for identifiers and JSON.
 * @param placeholder shown while [value] is empty.
 * @param leadingIcon content before the text, usually a [JwIcon].
 * @param trailingIcon an optional control after the text, such as a clear button.
 * @param isError colors the border as an error; put the message in a [JwFormField].
 * @param visualTransformation mirrors `BasicTextField`'s; password masking, say.
 * @param keyboardOptions mirrors `BasicTextField`'s.
 * @param keyboardActions mirrors `BasicTextField`'s.
 * @param singleLine keeps the text on one line and makes Enter an action rather than a newline.
 * A [minLines] above 1 overrides it: the field is then multi-line.
 * @param maxLines the most lines a multi-line field grows to.
 * @param minLines the fewest lines the field reserves, for an editor that should look like one
 * before anything is typed. Above 1 it makes the field multi-line whatever [singleLine] says.
 */
@Composable
public fun JwTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    placeholder: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val singleLine = singleLine && minLines <= 1
    val maxLines = if (singleLine) 1 else maxLines.coerceAtLeast(minLines)
    val scheme = MaterialTheme.colorScheme
    val colors = JwTheme.colors
    val borderColor = when {
        !enabled -> colors.border.copy(alpha = 0.5f)
        isError -> scheme.error
        focused -> scheme.primary
        else -> scheme.outline
    }
    val contentColor = if (enabled) scheme.onSurface else colors.textDisabled
    val shape = MaterialTheme.shapes.small
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        textStyle = textStyle.copy(color = contentColor),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        cursorBrush = SolidColor(scheme.primary),
        interactionSource = interactionSource,
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = JwMetrics.controlHeight)
                    .background(scheme.surfaceContainerLowest, shape)
                    .border(if (focused || isError) JwMetrics.focusStrokeWidth else JwMetrics.borderWidth, borderColor, shape)
                    .padding(horizontal = JwSpacing.medium, vertical = if (singleLine) 0.dp else JwSpacing.small),
                verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(JwSpacing.small),
            ) {
                CompositionLocalProvider(LocalContentColor provides colors.textSecondary) {
                    leadingIcon?.invoke()
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
                    ) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(
                                text = placeholder,
                                style = textStyle,
                                color = colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                    trailingIcon?.invoke()
                }
            }
        },
    )
}

/**
 * A [JwTextField] dressed as a filter box: search glyph in front, a clear button once typed.
 *
 * @param value the current query.
 * @param onValueChange called with the new query on every edit, and with "" when cleared.
 * @param clearLabel the clear button's tooltip and accessibility name, in the UI's language.
 * @param placeholder what can be searched for, shown while [value] is empty.
 * @param enabled false greys the field out and ignores input.
 */
@Composable
public fun JwSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    clearLabel: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
) {
    JwTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = placeholder,
        enabled = enabled,
        leadingIcon = {
            JwIcon(imageVector = JwIcons.Search, contentDescription = null)
        },
        trailingIcon = if (value.isEmpty()) {
            null
        } else {
            {
                JwIconButton(
                    onClick = { onValueChange("") },
                    tooltip = clearLabel,
                    size = JwIconButtonDefaults.inlineSize,
                ) {
                    JwIcon(imageVector = JwIcons.Close, contentDescription = null, modifier = Modifier.padding(ClearIconInset))
                }
            }
        },
    )
}
