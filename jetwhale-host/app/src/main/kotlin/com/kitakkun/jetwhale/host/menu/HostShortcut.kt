package com.kitakkun.jetwhale.host.menu

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.kitakkun.jetwhale.host.model.HostOs
import com.kitakkun.jetwhale.host.theme.isShortcutModifierPressed

/** A key the host binds together with the platform's shortcut modifier: ⌘ on macOS, Ctrl elsewhere. */
internal data class HostShortcut(val key: Key, val withShift: Boolean)

internal fun HostShortcut.toKeyShortcut(): KeyShortcut = if (HostOs.current == HostOs.MAC) {
    KeyShortcut(key = key, meta = true, shift = withShift)
} else {
    KeyShortcut(key = key, ctrl = true, shift = withShift)
}

internal fun HostShortcut.matches(event: KeyEvent): Boolean = event.type == KeyEventType.KeyDown &&
    event.key == key &&
    event.isShortcutModifierPressed &&
    event.isShiftPressed == withShift &&
    !event.isAltPressed
