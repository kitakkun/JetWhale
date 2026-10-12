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
import androidx.compose.runtime.Stable
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
import androidx.compose.ui.unit.Dp
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
    val menuState = remember { SuggestionMenuState() }
    var fieldWidth by remember { mutableIntStateOf(0) }
    val fieldFocus = remember { FocusRequester() }
    val shown = menuState.shownSuggestions(suggestions = suggestions, value = value)
    val choose = { suggestion: String ->
        onValueChange(suggestion)
        menuState.close()
    }
    Box {
        JwTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                if (suggestions.isNotEmpty()) menuState.open(narrowing = true)
            },
            isError = isError,
            trailingIcon = if (suggestions.isEmpty()) {
                null
            } else {
                {
                    val openFromButton = {
                        fieldFocus.requestFocus()
                        menuState.open(narrowing = false)
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
                    event.type == KeyEventType.KeyDown &&
                        suggestions.isNotEmpty() &&
                        menuState.onKeyDown(key = event.key, shown = shown, onChoose = choose)
                },
        )
        if (menuState.expanded && shown.isNotEmpty()) {
            SuggestionMenu(
                shown = shown,
                value = value,
                highlightedIndex = menuState.highlightedIndex,
                width = with(LocalDensity.current) { fieldWidth.toDp() },
                onChoose = choose,
                onDismissRequest = menuState::close,
            )
        }
    }
}

/**
 * Whether the suggestion menu is open, whether it narrows to the typed text, and which row the
 * arrow keys point at.
 */
@Stable
private class SuggestionMenuState {
    var expanded by mutableStateOf(false)
        private set
    private var narrowing by mutableStateOf(false)
    var highlightedIndex by mutableIntStateOf(-1)
        private set

    fun shownSuggestions(suggestions: List<String>, value: String): List<String> = if (narrowing) suggestions.filter { it.contains(value, ignoreCase = true) } else suggestions

    fun open(narrowing: Boolean) {
        expanded = true
        this.narrowing = narrowing
        highlightedIndex = -1
    }

    fun close() {
        expanded = false
    }

    /**
     * Moves through [shown] for an arrow key, takes the highlighted suggestion for Enter and closes
     * the menu for Escape; false for a key the menu does not use.
     */
    fun onKeyDown(key: Key, shown: List<String>, onChoose: (String) -> Unit): Boolean {
        when {
            key == Key.DirectionDown && !expanded -> open(narrowing = false)
            key == Key.DirectionDown -> highlightedIndex = (highlightedIndex + 1).coerceAtMost(shown.lastIndex)
            key == Key.DirectionUp && expanded -> highlightedIndex = (highlightedIndex - 1).coerceAtLeast(0)
            key == Key.Enter && expanded && highlightedIndex in shown.indices -> onChoose(shown[highlightedIndex])
            key == Key.Escape && expanded -> close()
            else -> return false
        }
        return true
    }
}

@Composable
private fun SuggestionMenu(
    shown: List<String>,
    value: String,
    highlightedIndex: Int,
    width: Dp,
    onChoose: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    Popup(
        popupPositionProvider = rememberJwPopupPositionProvider(JwPopupAnchor.BelowStart),
        onDismissRequest = onDismissRequest,
        // Not focusable, unlike JwDropdownMenu's popup, so typing stays in the field, whose key
        // handler drives the menu.
        properties = PopupProperties(focusable = false),
    ) {
        Column(
            modifier = Modifier
                .width(width)
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
                    onClick = { onChoose(suggestion) },
                    modifier = if (index == highlightedIndex) Modifier.background(JwTheme.colors.hover, JwShapes.extraSmall) else Modifier,
                )
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
