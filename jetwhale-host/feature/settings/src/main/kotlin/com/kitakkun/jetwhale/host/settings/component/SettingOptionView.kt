package com.kitakkun.jetwhale.host.settings.component

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.ui.JwDropdownButton
import com.kitakkun.jetwhale.host.ui.JwMenuItem
import com.kitakkun.jetwhale.host.ui.JwPanel
import com.kitakkun.jetwhale.host.ui.JwSwitch
import com.kitakkun.jetwhale.host.ui.JwTheme

/** Width shared by every control on the right of a [SettingsItemRow], so a page's controls align. */
val SettingsControlWidth = 220.dp

/** A titled panel of related settings. */
@Composable
fun SettingOptionView(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable SettingsContentScope.() -> Unit,
) {
    JwPanel(title = label, modifier = modifier) {
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
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    SettingsItemRow(
        label = label,
        modifier = modifier,
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
    modifier: Modifier = Modifier,
) {
    SettingsItemRow(
        label = label,
        modifier = modifier,
    ) {
        JwSwitch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            contentDescription = label,
        )
    }
}

@Preview
@Composable
private fun SettingOptionViewPreview() {
    JwTheme(darkTheme = false) {
        SettingOptionView(label = "Appearance") {
            DropdownSettingsItemView(
                label = "Language",
                currentItem = PreviewLanguage.English,
                items = PreviewLanguage.entries,
                onSelect = {},
                itemNameProvider = PreviewLanguage::label,
            )
            SwitchSettingsItemView(
                label = "Follow AI operations",
                isChecked = true,
                onCheckedChange = {},
            )
        }
    }
}

private enum class PreviewLanguage(val label: String) {
    English("English"),
    Japanese("Japanese"),
}
