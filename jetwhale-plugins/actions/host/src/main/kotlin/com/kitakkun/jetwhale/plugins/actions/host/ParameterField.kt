package com.kitakkun.jetwhale.plugins.actions.host

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kitakkun.jetwhale.host.ui.JwDropdownButton
import com.kitakkun.jetwhale.host.ui.JwFormField
import com.kitakkun.jetwhale.host.ui.JwMenuItem
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwSwitch
import com.kitakkun.jetwhale.host.ui.JwTextField
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionParameter
import com.kitakkun.jetwhale.plugins.actions.protocol.ParameterType

/**
 * The input for one argument property, chosen by how it is entered: a switch for a boolean, a menu
 * for an enum, a multi-line editor for JSON, a text field otherwise — with the app's suggested
 * values offered from a menu beside it.
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

            else -> Row(horizontalArrangement = Arrangement.spacedBy(JwSpacing.small), verticalAlignment = Alignment.CenterVertically) {
                JwTextField(
                    value = value,
                    onValueChange = onValueChange,
                    isError = error != null,
                    singleLine = parameter.type != ParameterType.JSON,
                    minLines = if (parameter.type == ParameterType.JSON) 3 else 1,
                    placeholder = if (parameter.type == ParameterType.JSON) "JSON" else null,
                    modifier = Modifier.weight(1f),
                )
                if (suggestions.isNotEmpty()) ChoiceMenu(text = "Suggested", choices = suggestions, onChoose = onValueChange)
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
