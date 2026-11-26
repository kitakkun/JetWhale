package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.ui.geometry.Offset

/**
 * Moves the view of a device whose screen looks into a 3D scene, as the Android XR emulator's does:
 * where the view faces, and whether the room around it shows through the scene's virtual
 * environment. An operation the device cannot take throws [DeviceControlException] saying why.
 */
internal interface XrViewController {
    /** Turns the view [rightDegrees] to the right and [upDegrees] up from where it faces now; negative values turn it left or down. */
    suspend fun turnView(rightDegrees: Double, upDegrees: Double)

    /**
     * Turns the view so that the scene follows a drag on the screen, as if the drag pulled the scene
     * along, for the part of the drag from [dragFromInScreenWidths] to [dragToInScreenWidths]. Both
     * are measured from where the drag started, right and down, in widths of the screen, so a drag
     * reported in any number of parts turns the view as far as one reported whole.
     */
    suspend fun turnViewWithDrag(dragFromInScreenWidths: Offset, dragToInScreenWidths: Offset)

    /** Puts the view back where it started, facing forward. */
    suspend fun recenterView()

    suspend fun isPassthroughOn(): Boolean

    suspend fun setPassthrough(on: Boolean)
}
