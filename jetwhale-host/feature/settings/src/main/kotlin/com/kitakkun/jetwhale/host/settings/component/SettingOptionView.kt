package com.kitakkun.jetwhale.host.settings.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingOptionView(
    label: String,
    content: @Composable SettingsContentScope.() -> Unit,
) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
        )
        Column {
            with(object : SettingsContentScope {}) {
                content()
            }
        }
    }
}

interface SettingsContentScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
context(_: SettingsContentScope)
fun <T> DropdownSettingsItemView(
    label: String,
    currentItem: T,
    items: List<T>,
    onSelect: (T) -> Unit,
    itemNameProvider: (T) -> String,
) {
    var expanded by remember { mutableStateOf(false) }
    SettingsItemRow(
        label = label,
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            Card(
                onClick = { expanded = true },
            ) {
                Text(
                    text = itemNameProvider(currentItem),
                    modifier = Modifier.padding(
                        vertical = 8.dp,
                        horizontal = 16.dp,
                    ),
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                items.forEach {
                    DropdownMenuItem(
                        text = { Text(text = itemNameProvider(it)) },
                        onClick = {
                            expanded = false
                            onSelect(it)
                        },
                        trailingIcon = {
                            if (it == currentItem) {
                                Icon(Icons.Default.Check, null)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
context(_: SettingsContentScope)
fun SwitchSettingsItemView(
    label: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsItemRow(
        label = label,
    ) {
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
context(_: SettingsContentScope)
fun TextFieldSettingsItemView(
    label: String,
    text: String,
    readonly: Boolean = false,
    onTextChange: (String) -> Unit,
) {
    SettingsItemRow(
        label = label,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            readOnly = readonly,
        )
    }
}
