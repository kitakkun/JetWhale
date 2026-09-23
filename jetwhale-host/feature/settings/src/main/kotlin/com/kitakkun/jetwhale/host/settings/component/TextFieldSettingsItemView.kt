package com.kitakkun.jetwhale.host.settings.component

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.kitakkun.jetwhale.host.ui.JwTextField
import com.kitakkun.jetwhale.host.ui.JwTheme

@Composable
context(_: SettingsContentScope)
fun TextFieldSettingsItemView(
    label: String,
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    readonly: Boolean = false,
) {
    SettingsItemRow(
        label = label,
        modifier = modifier,
    ) {
        JwTextField(
            value = text,
            onValueChange = onTextChange,
            readOnly = readonly,
            modifier = Modifier.width(SettingsControlWidth),
        )
    }
}

@Preview
@Composable
private fun TextFieldSettingsItemViewPreview() {
    JwTheme(darkTheme = false) {
        SettingOptionView(label = "Debug server") {
            TextFieldSettingsItemView(
                label = "Port",
                text = "5080",
                onTextChange = {},
            )
        }
    }
}
