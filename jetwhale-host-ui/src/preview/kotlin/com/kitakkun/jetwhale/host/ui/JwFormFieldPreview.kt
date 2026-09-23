package com.kitakkun.jetwhale.host.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun JwFormFieldPreview() {
    JwTheme(darkTheme = false) {
        JwFormField(label = "Group id", supportingText = "Required", isError = true) {
            JwTextField(value = "com.example", onValueChange = {}, isError = true)
        }
    }
}
