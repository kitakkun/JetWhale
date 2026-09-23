package com.kitakkun.jetwhale.host.theme

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager

/**
 * Clears focus when a press lands where nothing handles it, so the focus ring of the control last
 * clicked goes away on a click into empty space. Compose moves focus only to a focusable target,
 * never away from one, so without this the ring stays until another control is clicked.
 *
 * Apply it to the root of a window or scene. It reads the press on the final pass, after every
 * descendant has had it: a clickable or a text field consumes the down and keeps its focus.
 */
@Composable
fun Modifier.clearFocusOnBlankPress(): Modifier {
    val focusManager = LocalFocusManager.current
    return pointerInput(focusManager) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
            if (!down.isConsumed) focusManager.clearFocus()
        }
    }
}
