package com.kitakkun.jetwhale.host

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.awt.ComposeWindow

val LocalComposeWindow = staticCompositionLocalOf<ComposeWindow> {
    error("No Window provided")
}
