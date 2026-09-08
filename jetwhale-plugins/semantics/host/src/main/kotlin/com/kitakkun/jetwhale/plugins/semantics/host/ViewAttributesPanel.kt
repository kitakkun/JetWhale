package com.kitakkun.jetwhale.plugins.semantics.host

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.ui.JwDropdownButton
import com.kitakkun.jetwhale.host.ui.JwMenuItem
import com.kitakkun.jetwhale.host.ui.JwSectionHeader
import com.kitakkun.jetwhale.host.ui.JwShapes
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwStatusLine
import com.kitakkun.jetwhale.host.ui.JwSwitch
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTextField
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.JwTone
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttribute
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttributeValue

/**
 * The platform attributes of the selected Android `View` node, and an editor for the ones that can
 * be written.
 *
 * A view of [ViewAttributeStore] and nothing more: the read, the write and their outcome live on
 * the plugin instance, so this panel can be closed and reopened without any of them being redone or
 * lost, and an agent writing through the same store is seen here.
 */
@Composable
internal fun ViewAttributesPanel(
    attributes: List<ViewAttribute>?,
    message: String?,
    writeStatus: String?,
    writeFailed: Boolean,
    onCommit: (ViewAttribute, String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(JwSpacing.small)) {
        JwSectionHeader(title = "View attributes", contentPadding = PaddingValues(0.dp))
        JwText(
            text = "Edits are temporary: a relayout, a rebind, or the app writing the property itself takes the value back.",
            style = JwTheme.textStyles.labelSmall,
            color = JwTheme.colors.textSecondary,
        )

        when {
            attributes == null && message == null -> JwText(
                text = "Reading the view's attributes…",
                style = JwTheme.textStyles.bodySmall,
                color = JwTheme.colors.textSecondary,
            )

            attributes == null -> JwStatusLine(text = message.orEmpty(), tone = JwTone.Warning)

            else -> {
                for ((group, rows) in attributes.groupBy { it.group }) {
                    JwSectionHeader(title = group, contentPadding = PaddingValues(0.dp))
                    for (attribute in rows) {
                        AttributeRow(attribute = attribute, onCommit = { text -> onCommit(attribute, text) })
                    }
                }
            }
        }

        writeStatus?.let { JwStatusLine(text = it, tone = if (writeFailed) JwTone.Error else JwTone.Accent) }
    }
}

/** Fits "backgroundColor", the longest attribute label. */
private val AttributeLabelWidth = 120.dp

/** Wide enough for `#AARRGGBB` and for the enum names, without crowding the pane. */
private val AttributeEditorWidth = 180.dp

/** The two halves of the layout-size editor: the wider one fits `MATCH_PARENT` without eliding it. */
private val LayoutSizeChoiceWidth = 170.dp
private val LayoutSizePixelWidth = 62.dp

private val ColorSwatchSize = 16.dp

/** The layout-size choice that is a length rather than one of the constants. */
private const val FIXED_CHOICE = "Fixed"

@Composable
private fun AttributeRow(attribute: ViewAttribute, onCommit: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium),
    ) {
        JwText(
            text = attribute.label,
            style = JwTheme.textStyles.bodySmall,
            color = JwTheme.colors.textSecondary,
            modifier = Modifier.width(AttributeLabelWidth),
        )
        val value = attribute.value
        when {
            !attribute.editable -> JwText(text = value.asText(), style = JwTheme.textStyles.code)

            value is ViewAttributeValue.BooleanValue -> JwSwitch(
                checked = value.value,
                onCheckedChange = { onCommit(it.toString()) },
                contentDescription = attribute.label,
            )

            value is ViewAttributeValue.EnumValue -> EnumEditor(value = value, onCommit = onCommit)

            value is ViewAttributeValue.LayoutSizeValue -> LayoutSizeEditor(value = value, onCommit = onCommit)

            value is ViewAttributeValue.ColorValue -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(JwSpacing.small),
            ) {
                Box(
                    Modifier
                        .size(ColorSwatchSize)
                        .background(Color(value.argb), JwShapes.extraSmall)
                        .border(1.dp, JwTheme.colors.border, JwShapes.extraSmall),
                )
                TextEditor(
                    current = value.asText(),
                    enabled = true,
                    placeholder = null,
                    modifier = Modifier.width(AttributeEditorWidth),
                    onCommit = onCommit,
                )
            }

            else -> TextEditor(
                current = value.asText(),
                enabled = true,
                placeholder = null,
                modifier = Modifier.width(AttributeEditorWidth),
                onCommit = onCommit,
            )
        }
    }
}

@Composable
private fun EnumEditor(value: ViewAttributeValue.EnumValue, onCommit: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.width(AttributeEditorWidth)) {
        JwDropdownButton(
            text = value.value,
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            for (option in value.options) {
                JwMenuItem(
                    text = option,
                    selected = option == value.value,
                    onClick = {
                        expanded = false
                        onCommit(option)
                    },
                )
            }
        }
    }
}

/**
 * A layout size, whose editor is one shape whichever of the two cases the value is in: the
 * constants and "Fixed" in a dropdown, and a pixel field that only "Fixed" enables.
 *
 * Picking "Fixed" writes nothing on its own — there is no length to write yet — it only opens the
 * field, so the choice is remembered here until the number that follows it lands.
 */
@Composable
private fun LayoutSizeEditor(value: ViewAttributeValue.LayoutSizeValue, onCommit: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var fixed by remember(value) { mutableStateOf(value.constant == null) }
    val choice = if (fixed) FIXED_CHOICE else value.constant.orEmpty()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(JwSpacing.small),
    ) {
        Box(Modifier.width(LayoutSizeChoiceWidth)) {
            JwDropdownButton(
                text = choice,
                expanded = expanded,
                onExpandedChange = { expanded = it },
            ) {
                for (constant in value.constants) {
                    JwMenuItem(
                        text = constant,
                        selected = constant == choice,
                        onClick = {
                            expanded = false
                            fixed = false
                            onCommit(constant)
                        },
                    )
                }
                JwMenuItem(
                    text = FIXED_CHOICE,
                    selected = fixed,
                    onClick = {
                        expanded = false
                        fixed = true
                    },
                )
            }
        }
        TextEditor(
            current = value.px?.toString().orEmpty(),
            enabled = fixed,
            placeholder = "px",
            modifier = Modifier.width(LayoutSizePixelWidth),
            onCommit = onCommit,
        )
    }
}

/**
 * A field that commits on Enter or when it loses focus — never on every keystroke, which would send
 * a write to the app for each character typed.
 *
 * A commit repeats nothing: the draft only reaches the app when it differs from what was last sent.
 * Enter followed by a blur, or Enter twice, would otherwise write the same value again — the draft
 * does not converge on [current] until the write comes back, and never converges at all when the
 * write is refused.
 */
@Composable
private fun TextEditor(
    current: String,
    enabled: Boolean,
    placeholder: String?,
    modifier: Modifier,
    onCommit: (String) -> Unit,
) {
    var draft by remember(current) { mutableStateOf(current) }
    var committed by remember(current) { mutableStateOf(current) }

    fun commit() {
        if (draft == committed) return
        committed = draft
        onCommit(draft)
    }

    JwTextField(
        value = draft,
        onValueChange = { draft = it },
        enabled = enabled,
        placeholder = placeholder,
        textStyle = JwTheme.textStyles.code,
        // Enter arrives as the field's own IME action rather than being taken off the key stream
        // before it: an input method composing a word — a Japanese one converting kana — spends
        // Enter on accepting its candidate, and a preview handler would swallow that keystroke and
        // write the half-composed text instead.
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { commit() }),
        modifier = modifier.onFocusChanged { state ->
            if (!state.isFocused) commit()
        },
    )
}
