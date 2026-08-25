package com.kitakkun.jetwhale.plugins.semantics.agent

import android.view.View
import com.kitakkun.jetwhale.plugins.semantics.protocol.UiNode
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
    private var nextId = -1

    /** The id for [view], assigning one on first sight. */
    fun idOf(view: View): Int = synchronized(lock) {
        idsByView.getOrPut(view) { nextId.also { nextId -= 1 } }
    }

    /** The view [id] names, or `null` once it has been collected or was never assigned. */
    fun viewOf(id: Int): View? = synchronized(lock) {
        idsByView.entries.firstOrNull { it.value == id }?.key
    }
}
