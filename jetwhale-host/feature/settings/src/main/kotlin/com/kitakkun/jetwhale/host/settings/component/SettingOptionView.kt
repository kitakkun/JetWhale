package com.kitakkun.jetwhale.host.settings.component

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.ui.JwDropdownButton
import com.kitakkun.jetwhale.host.ui.JwMenuItem
import com.kitakkun.jetwhale.host.ui.JwPanel
import com.kitakkun.jetwhale.host.ui.JwSwitch
import com.kitakkun.jetwhale.host.ui.JwTextField

/** Width shared by every control on the right of a [SettingsItemRow], so a page's controls align. */
val SettingsControlWidth = 220.dp

/** A titled panel of related settings. */
@Composable
fun SettingOptionView(
    label: String,
    content: @Composable SettingsContentScope.() -> Unit,
) {
    JwPanel(title = label) {
        with(object : SettingsContentScope {}) {
            content()
        }
    }
}

interface SettingsContentScope

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
        JwDropdownButton(
            text = itemNameProvider(currentItem),
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.width(SettingsControlWidth),
        ) {
            items.forEach {
                JwMenuItem(
                    text = itemNameProvider(it),
                    selected = it == currentItem,
                    onClick = {
                        expanded = false
                        onSelect(it)
                    },
                )
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
        JwSwitch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            contentDescription = label,
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
        JwTextField(
            value = text,
            onValueChange = onTextChange,
            readOnly = readonly,
            modifier = Modifier.width(SettingsControlWidth),
        )
    }
}
