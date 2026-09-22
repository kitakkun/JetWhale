package com.kitakkun.jetwhale.host.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * True when the host renders inside an IDE tool window rather than its own window. The tool window
 * already carries the app's name, so chrome that only repeats it stays out.
 */
val LocalEmbeddedInIde = staticCompositionLocalOf { false }
