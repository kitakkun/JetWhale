package com.kitakkun.jetwhale.plugins.semantics.agent

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSThread
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Runs the block on the main thread, without hopping when the caller is already on it.
 *
 * UIKit and the accessibility protocol may only be touched from the main thread, and a plain
 * `Dispatchers.Main` would always dispatch, costing a run-loop turn per capture even from a caller
 * that is already there.
 */
internal object IosUiThread : ComposeUiThread {
    override suspend fun <T> await(block: () -> T): T {
        if (NSThread.isMainThread()) return block()
        return suspendCancellableCoroutine { continuation ->
            dispatch_async(dispatch_get_main_queue()) {
                // The caller may have been cancelled while the block sat in the queue; running it
                // then would touch the UI for a request nobody is waiting on any more.
                if (!continuation.isActive) return@dispatch_async
                continuation.resumeWith(runCatching(block))
            }
        }
    }
}
