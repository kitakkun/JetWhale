package com.kitakkun.jetwhale.plugins.semantics.agent

import android.content.res.Resources
import android.view.View
import android.view.ViewGroup
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttributeValue

internal fun View.density(): Float = resources.displayMetrics.density

// A TextView's sp scales with the user's font-size setting on top of display density, so converting
// to sp needs scaledDensity, not density.
@Suppress("DEPRECATION")
internal fun View.scaledTextDensity(): Float = resources.displayMetrics.scaledDensity

internal fun View.dimension(px: Float): ViewAttributeValue.DimensionValue = ViewAttributeValue.DimensionValue(px = px, dp = px / density())

internal fun View.marginParams(): ViewGroup.MarginLayoutParams? = layoutParams as? ViewGroup.MarginLayoutParams

internal fun View.visibilityName(): String = when (visibility) {
    View.VISIBLE -> "VISIBLE"
    View.INVISIBLE -> "INVISIBLE"
    else -> "GONE"
}

internal fun layoutSizeConstantName(raw: Int): String? = when (raw) {
    ViewGroup.LayoutParams.MATCH_PARENT -> "MATCH_PARENT"
    ViewGroup.LayoutParams.WRAP_CONTENT -> "WRAP_CONTENT"
    else -> null
}

/**
 * The entry name of the view's `android:id`, or `null` when it has none. A generated id has no entry
 * to look up, so the lookup is allowed to fail rather than being guarded by a check on the packing.
 */
internal fun View.resourceEntryNameOrNull(): String? {
    if (id == View.NO_ID) return null
    return try {
        resources?.getResourceEntryName(id)
    } catch (_: Resources.NotFoundException) {
        null
    }
}
