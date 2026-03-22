package com.kitakkun.jetwhale.host.ui

import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed

private val isMacOS: Boolean = System.getProperty("os.name").lowercase().contains("mac")

val KeyEvent.isShortcutModifierPressed: Boolean
    get() = if (isMacOS) isMetaPressed else isCtrlPressed
