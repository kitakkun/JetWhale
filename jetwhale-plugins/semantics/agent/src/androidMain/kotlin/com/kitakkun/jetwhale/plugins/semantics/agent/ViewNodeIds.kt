package com.kitakkun.jetwhale.plugins.semantics.agent

import android.view.View
import com.kitakkun.jetwhale.plugins.semantics.protocol.UiNode
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/**
 * Hands out the [UiNode.id] a `View` is reported under, and resolves one back.
 *
 * A `View` has no identifier of its own that survives a capture: `View.getId()` is a resource id
 * shared by every inflation of the same layout, and `identityHashCode` can be reused once a view is
 * collected. So ids are assigned here, counting **down** from `-1`: Compose's semantics ids come
 * from a non-negative process-wide counter, which makes the two ranges disjoint and lets
 * `performNodeAction` tell from the id alone which side of the tree a node came from.
 *
 * Views are held weakly, so an id lives exactly as long as the view it names, and a screen that has
 * gone away costs nothing.
 */
internal object ViewNodeIds {
    private val lock = Any()
    private val idsByView = WeakHashMap<View, Int>()

    // The reverse direction has a map of its own so resolving an id is one lookup rather than a walk
    // over every view ever captured: `performNodeAction` resolves on the UI thread, where that walk
    // would grow with the size of the hierarchy. Its references are weak for the same reason the
    // forward map is, and a cleared one is dropped the next time it is read.
    private val viewsById = HashMap<Int, WeakReference<View>>()
    private var nextId = -1

    /** The id for [view], assigning one on first sight. */
    fun idOf(view: View): Int = synchronized(lock) {
        idsByView.getOrPut(view) {
            val assigned = nextId
            nextId -= 1
            viewsById[assigned] = WeakReference(view)
            assigned
        }
    }

    /** The view [id] names, or `null` once it has been collected or was never assigned. */
    fun viewOf(id: Int): View? = synchronized(lock) {
        val view = viewsById[id]?.get()
        if (view == null) viewsById.remove(id)
        view
    }
}
