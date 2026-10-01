package com.kitakkun.jetwhale.plugins.actions.host

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.kitakkun.jetwhale.host.ui.JwDropdownButton
import com.kitakkun.jetwhale.host.ui.JwDropdownMenuDefaults
import com.kitakkun.jetwhale.host.ui.JwFormField
import com.kitakkun.jetwhale.host.ui.JwIcon
import com.kitakkun.jetwhale.host.ui.JwIconButton
import com.kitakkun.jetwhale.host.ui.JwIconButtonDefaults
import com.kitakkun.jetwhale.host.ui.JwIcons
import com.kitakkun.jetwhale.host.ui.JwMenuItem
import com.kitakkun.jetwhale.host.ui.JwMetrics
import com.kitakkun.jetwhale.host.ui.JwPopupAnchor
import com.kitakkun.jetwhale.host.ui.JwShapes
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwSwitch
import com.kitakkun.jetwhale.host.ui.JwTextField
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.rememberJwPopupPositionProvider
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionParameter
import com.kitakkun.jetwhale.plugins.actions.protocol.ParameterType

/** The elevation of the host's own menus, which keeps its constant private. */
private val SuggestionMenuElevation = 10.dp

/**
 * The input for one argument property, chosen by how it is entered: a switch for a boolean, a menu
 * for an enum, a multi-line editor for JSON, a text field otherwise — offering the app's suggested
 * values from a menu under the field.
 */
@Composable
internal fun ParameterField(
    parameter: ActionParameter,
    value: String,
    error: String?,
    suggestions: List<String>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    JwFormField(
        label = if (parameter.optional) "${parameter.name} (optional)" else parameter.name,
        supportingText = error ?: parameter.description,
        isError = error != null,
        modifier = modifier,
    ) {
        when (parameter.type) {
            ParameterType.BOOLEAN if parameter.optional || parameter.nullable -> ChoiceMenu(
                text = value.ifEmpty { if (parameter.optional) "Default" else "null" },
                choices = listOf("", "true", "false"),
                onChoose = onValueChange,
            )

            ParameterType.BOOLEAN -> JwSwitch(
                checked = value == "true",
                contentDescription = parameter.name,
                onCheckedChange = { onValueChange(it.toString()) },
            )

            ParameterType.ENUM -> ChoiceMenu(
                text = value.ifEmpty { "Default" },
                choices = listOfNotNull("".takeIf { parameter.optional }) + parameter.enumValues,
                onChoose = onValueChange,
            )

            ParameterType.JSON -> JwTextField(
                value = value,
                onValueChange = onValueChange,
                isError = error != null,
                singleLine = false,
                minLines = 3,
                placeholder = "JSON",
                modifier = Modifier.fillMaxWidth(),
            )

            else -> SuggestionTextField(
                name = parameter.name,
                value = value,
                suggestions = suggestions,
                isError = error != null,
                onValueChange = onValueChange,
            )
        }
    }
}

/**
 * A text field whose trailing ▾ opens [suggestions] in a menu under it. Typing narrows the menu to
 * the suggestions containing the text; ↓ and ↑ move through it, Enter takes the highlighted one and
 * Escape closes it.
 */
@Composable
private fun SuggestionTextField(
    name: String,
    value: String,
    suggestions: List<String>,
    isError: Boolean,
    onValueChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var narrowing by remember { mutableStateOf(false) }
    var highlightedIndex by remember { mutableIntStateOf(-1) }
    var fieldWidth by remember { mutableIntStateOf(0) }
    val fieldFocus = remember { FocusRequester() }
    val shown = if (narrowing) suggestions.filter { it.contains(value, ignoreCase = true) } else suggestions
    val open = { narrowingNow: Boolean ->
        expanded = true
        narrowing = narrowingNow
        highlightedIndex = -1
    }
    val choose = { suggestion: String ->
        onValueChange(suggestion)
        expanded = false
    }
    Box {
        JwTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                if (suggestions.isNotEmpty()) open(true)
            },
            isError = isError,
            trailingIcon = if (suggestions.isEmpty()) {
                null
            } else {
                {
                    val openFromButton = {
                        fieldFocus.requestFocus()
                        open(false)
                    }
                    JwIconButton(tooltip = "Suggested values for $name", onClick = openFromButton, size = JwIconButtonDefaults.inlineSize) {
                        JwIcon(imageVector = JwIcons.ChevronDown, contentDescription = null)
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(fieldFocus)
                .onSizeChanged { fieldWidth = it.width }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || suggestions.isEmpty()) return@onPreviewKeyEvent false
                    when {
                        event.key == Key.DirectionDown && !expanded -> open(false)
                        event.key == Key.DirectionDown -> highlightedIndex = (highlightedIndex + 1).coerceAtMost(shown.lastIndex)
                        event.key == Key.DirectionUp && expanded -> highlightedIndex = (highlightedIndex - 1).coerceAtLeast(0)
                        event.key == Key.Enter && expanded && highlightedIndex in shown.indices -> choose(shown[highlightedIndex])
                        event.key == Key.Escape && expanded -> expanded = false
                        else -> return@onPreviewKeyEvent false
                    }
                    true
                },
        )
        if (expanded && shown.isNotEmpty()) {
            Popup(
                popupPositionProvider = rememberJwPopupPositionProvider(JwPopupAnchor.BelowStart),
                onDismissRequest = { expanded = false },
                // Not focusable, so typing stays in the field, whose key handler drives the menu.
                properties = PopupProperties(focusable = false),
            ) {
                Column(
                    modifier = Modifier
                        .width(with(LocalDensity.current) { fieldWidth.toDp() })
                        .heightIn(max = JwDropdownMenuDefaults.maxHeight)
                        .shadow(SuggestionMenuElevation, JwShapes.medium)
                        .background(JwTheme.colors.popupBackground, JwShapes.medium)
                        .border(JwMetrics.borderWidth, JwTheme.colors.popupBorder, JwShapes.medium)
                        .padding(JwSpacing.extraSmall)
                        .verticalScroll(rememberScrollState()),
                ) {
                    shown.forEachIndexed { index, suggestion ->
                        JwMenuItem(
                            text = suggestion,
                            selected = suggestion == value,
                            onClick = { choose(suggestion) },
                            modifier = if (index == highlightedIndex) Modifier.background(JwTheme.colors.hover, JwShapes.extraSmall) else Modifier,
                        )
                    }
                }
            }
        }
    }
}

/** A menu button listing [choices]; the empty string stands for "leave it to the default". */
@Composable
private fun ChoiceMenu(text: String, choices: List<String>, onChoose: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    JwDropdownButton(text = text, expanded = expanded, onExpandedChange = { expanded = it }, modifier = Modifier.fillMaxWidth(fraction = 0.4f)) {
        choices.forEach { choice ->
            JwMenuItem(
                text = choice.ifEmpty { "Default" },
                onClick = {
                    expanded = false
                    onChoose(choice)
                },
            )
        }
    }
}
