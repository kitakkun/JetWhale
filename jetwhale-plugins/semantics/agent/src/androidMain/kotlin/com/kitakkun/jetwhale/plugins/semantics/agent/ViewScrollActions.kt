package com.kitakkun.jetwhale.plugins.semantics.agent

import android.graphics.Rect
import android.view.View
import android.widget.AbsListView
import androidx.recyclerview.widget.RecyclerView
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeActionResult
import com.kitakkun.jetwhale.plugins.semantics.protocol.PerformNodeAction
import kotlin.math.roundToInt

/** Moving a scrolling view, or moving the scrolling views around a view so that it shows. */
internal object ViewScrollActions {
    object ScrollBy : ViewActionHandler {
        override val runsOnDisabledView = true

        override fun isOfferedBy(view: View) = view.isScrollable()

        override fun perform(view: View, request: PerformNodeAction): NodeActionResult {
            if (!view.isScrollable()) return notSupported("the view has nothing to scroll")
            view.scrollBy(request.scrollX.roundToInt(), request.scrollY.roundToInt())
            return NodeActionResult(performed = true)
        }
    }

    /**
     * The item-position scroll of the two list widgets the platform and AndroidX offer.
     * `RecyclerView` is checked only when the app ships it: the agent compiles against it without
     * bundling it, and an `is` check on a class the app does not have would blow up rather than
     * answer `false`.
     */
    object ScrollToIndex : ViewActionHandler {
        override val runsOnDisabledView = true

        override fun isOfferedBy(view: View) = view.hasItemPositions()

        override fun perform(view: View, request: PerformNodeAction): NodeActionResult {
            val index = request.index ?: return missingIndex()
            return when {
                view is AbsListView -> {
                    if (index !in 0 until view.count) {
                        notSupported("index $index is out of bounds [0, ${view.count})")
                    } else {
                        view.setSelection(index)
                        NodeActionResult(performed = true)
                    }
                }

                isRecyclerViewAvailable && view is RecyclerView -> {
                    val itemCount = view.adapter?.itemCount ?: 0
                    if (index !in 0 until itemCount) {
                        notSupported("index $index is out of bounds [0, $itemCount)")
                    } else {
                        view.scrollToPosition(index)
                        NodeActionResult(performed = true)
                    }
                }

                else -> notSupported("the view is not a RecyclerView or a ListView")
            }
        }
    }

    /**
     * `requestRectangleOnScreen` is the platform's own "show on screen": every scrolling parent —
     * `ScrollView`, `RecyclerView`, and the holder Compose puts around an `AndroidView { }` —
     * answers `requestChildRectangleOnScreen`, so the one call climbs out through Compose containers
     * too. Immediate rather than animated, so the next capture already sees the result.
     */
    object BringIntoView : ViewActionHandler {
        override val runsOnDisabledView = true

        override fun isOfferedBy(view: View) = true

        override fun perform(view: View, request: PerformNodeAction): NodeActionResult {
            val whole = Rect(0, 0, view.width, view.height)
            if (whole.isEmpty) return notSupported("the view has no size to bring into view")
            val visible = Rect()
            if (view.getLocalVisibleRect(visible) && visible == whole) {
                return NodeActionResult(performed = true, message = "the view is already in view")
            }
            return performed(view.requestRectangleOnScreen(whole, true), "no ancestor scrolled the view into view")
        }
    }
}
