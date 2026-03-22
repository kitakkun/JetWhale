package com.kitakkun.jetwhale.host.ui

import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import java.util.Locale

private val isMacOS: Boolean =
    System.getProperty("os.name")?.lowercase(Locale.ROOT)?.contains("mac") == true

val KeyEvent.isShortcutModifierPressed: Boolean
    get() = if (isMacOS) isMetaPressed else isCtrlPressed
