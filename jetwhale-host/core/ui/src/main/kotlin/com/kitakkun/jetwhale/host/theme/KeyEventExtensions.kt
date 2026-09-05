package com.kitakkun.jetwhale.host.ui

import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import com.kitakkun.jetwhale.host.model.HostOs

val KeyEvent.isShortcutModifierPressed: Boolean
    get() = if (HostOs.current == HostOs.MAC) isMetaPressed else isCtrlPressed
