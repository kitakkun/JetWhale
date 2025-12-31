package com.kitakkun.jetwhale.host

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent

@Composable
fun KeyboardShortcutHandlerProvider(
    onPressSettingsShortcut: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = Modifier.onKeyEvent { keyEvent ->
            if (keyEvent.isMetaPressed && keyEvent.key == Key.Comma) {
                onPressSettingsShortcut()
                true
            } else {
                false
            }
        },
        content = content,
    )
}
