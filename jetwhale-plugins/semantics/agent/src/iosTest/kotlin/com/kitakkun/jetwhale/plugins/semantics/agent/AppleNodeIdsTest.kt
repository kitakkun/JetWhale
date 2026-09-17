package com.kitakkun.jetwhale.plugins.semantics.agent

import platform.UIKit.UIWindow
import platform.darwin.NSObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class AppleNodeIdsTest {
    private val window = UIWindow()

    @Test
    fun `an object keeps its id across captures while it is still reported`() {
        val obj = NSObject()
        val first = AppleNodeIds.trackingCapture(window) { AppleNodeIds.idOf(obj, window) }
        val second = AppleNodeIds.trackingCapture(window) { AppleNodeIds.idOf(obj, window) }

        assertEquals(first, second)
        assertSame(obj, AppleNodeIds.objectOf(first, window))
    }

    @Test
    fun `an object a capture no longer reports is released and its id resolves to nothing`() {
        val obj = NSObject()
        val id = AppleNodeIds.trackingCapture(window) { AppleNodeIds.idOf(obj, window) }
        AppleNodeIds.trackingCapture(window) { AppleNodeIds.idOf(NSObject(), window) }

        assertNull(AppleNodeIds.objectOf(id, window))
    }

    @Test
    fun `ids are negative and never reused`() {
        val first = AppleNodeIds.trackingCapture(window) { AppleNodeIds.idOf(NSObject(), window) }
        val second = AppleNodeIds.trackingCapture(window) { AppleNodeIds.idOf(NSObject(), window) }

        assertEquals(true, first < 0)
        assertNotEquals(first, second)
    }

    @Test
    fun `an id from another window does not resolve`() {
        val obj = NSObject()
        val other = UIWindow()
        val id = AppleNodeIds.trackingCapture(window) { AppleNodeIds.idOf(obj, window) }

        assertNull(AppleNodeIds.objectOf(id, other))
        assertSame(window, AppleNodeIds.windowOf(obj))
    }

    @Test
    fun `a capture of one window leaves another window's objects in place`() {
        val other = UIWindow()
        val obj = NSObject()
        val id = AppleNodeIds.trackingCapture(window) { AppleNodeIds.idOf(obj, window) }
        AppleNodeIds.trackingCapture(other) { AppleNodeIds.idOf(NSObject(), other) }

        assertSame(obj, AppleNodeIds.objectOf(id, window))
    }
}
