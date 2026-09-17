package com.kitakkun.jetwhale.plugins.semantics.agent

import com.kitakkun.jetwhale.plugins.semantics.protocol.UiNode
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.objcPtr
import platform.UIKit.UIWindow
import platform.darwin.NSObject

/**
 * Hands out the [UiNode.id] an accessibility object is reported under, and resolves one back.
 *
 * Nothing on the object itself survives a capture as an identifier: `accessibilityIdentifier` is
 * optional and shared by every instance of the same view, and an address is reused once the object
 * is freed. So ids are assigned here, counting **down** from `-1`, the same range the Android agent
 * uses for `View`s and disjoint from Compose's non-negative semantics ids.
 *
 * Objects are held **strongly**, for as long as the latest capture of their window reported them.
 * A weak reference would not do: a Kotlin reference to an Objective-C object is a wrapper the
 * runtime may collect while the object itself lives on, so a weak reference to it is gone by the
 * time an action arrives. Holding the object keeps its address unique, which is what makes the
 * address a sound key, and [trackingCapture] releases whatever a window no longer shows — so the
 * registry never holds more than one screen's worth per window.
 *
 * The window an object was captured in is recorded with it. A view knows its window, but a bare
 * element SwiftUI or Compose published does not say which view it hangs off, and this is the one
 * place that knew when it was captured.
 *
 * Main thread only, like everything that reads the tree.
 */
@OptIn(ExperimentalForeignApi::class)
internal object AppleNodeIds {
    private class Entry(val id: Int, val obj: NSObject, val window: UIWindow)

    private val entriesByAddress = HashMap<Long, Entry>()
    private val entriesById = HashMap<Int, Entry>()
    private var nextId = -1
    private var seenInCapture: MutableSet<Long>? = null

    /**
     * Runs one capture of [window]. Every object [idOf] sees inside [block] is retained; every
     * object the previous capture of this window reported and this one did not is released.
     */
    fun <T> trackingCapture(window: UIWindow, block: () -> T): T {
        val seen = HashSet<Long>()
        seenInCapture = seen
        try {
            return block()
        } finally {
            seenInCapture = null
            val stale = entriesByAddress.filterValues { it.window === window && it.obj.address() !in seen }
            stale.forEach { (address, entry) ->
                entriesByAddress.remove(address)
                entriesById.remove(entry.id)
            }
        }
    }

    /** The id for [obj], captured in [window], assigning one on first sight. */
    fun idOf(obj: NSObject, window: UIWindow): Int {
        val address = obj.address()
        seenInCapture?.add(address)
        entriesByAddress[address]?.let { return it.id }
        val entry = Entry(id = nextId, obj = obj, window = window)
        nextId -= 1
        entriesByAddress[address] = entry
        entriesById[entry.id] = entry
        return entry.id
    }

    /** The object [id] names, or `null` once it has left its window's latest capture, or was captured in another window. */
    fun objectOf(id: Int, window: UIWindow): NSObject? = entriesById[id]?.takeIf { it.window === window }?.obj

    /** The window [obj] was last captured in, or `null` for an object no capture has reported. */
    fun windowOf(obj: NSObject): UIWindow? = entriesByAddress[obj.address()]?.window

    private fun NSObject.address(): Long = objcPtr().toLong()
}
