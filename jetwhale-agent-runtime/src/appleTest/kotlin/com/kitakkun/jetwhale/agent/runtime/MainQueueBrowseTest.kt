package com.kitakkun.jetwhale.agent.runtime

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.autoreleasepool
import platform.Foundation.NSNetServiceBrowserDelegateProtocol
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference
import kotlin.native.runtime.GC
import kotlin.native.runtime.NativeRuntimeApi
import kotlin.test.Test
import kotlin.test.assertNotNull

@OptIn(ExperimentalNativeApi::class, NativeRuntimeApi::class, BetaInteropApi::class)
class MainQueueBrowseTest {
    @Test
    fun `a running browse keeps the delegate of its browser alive through a garbage collection`() {
        val browse = MainQueueBrowse(onResolved = {})
        // A run loop drains its autorelease pool after each event. Draining this one does the same,
        // so nothing autoreleased while starting the browse can keep the delegate alive through the
        // collection.
        val weakDelegate = autoreleasepool {
            browse.start()
            weakDelegateOf(browse)
        }
        GC.collect()

        assertNotNull(weakDelegate?.value)
        browse.stop()
    }

    // Its own frame, so no temporary left in the test's frame keeps the delegate alive through the
    // collection. The delegate is a Kotlin object, so its Kotlin side is what has to survive.
    @Suppress("KOTRAIL_OBJC_WEAK_REFERENCE")
    private fun weakDelegateOf(browse: MainQueueBrowse): WeakReference<NSNetServiceBrowserDelegateProtocol>? = browse.browser?.delegate?.let(::WeakReference)
}
