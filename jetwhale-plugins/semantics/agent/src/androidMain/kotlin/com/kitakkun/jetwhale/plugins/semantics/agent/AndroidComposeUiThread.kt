package com.kitakkun.jetwhale.plugins.semantics.agent

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Runs the block on Android's main thread, without hopping when the caller is already on it.
 *
 * A plain `Dispatchers.Main` would always dispatch — costing a frame's worth of latency per capture
 * — and would pull in `kotlinx-coroutines-android`, which the app may not otherwise need. A bare
 * `Handler` has neither cost.
 */
internal object AndroidComposeUiThread : ComposeUiThread {
    private val mainHandler = Handler(Looper.getMainLooper())

    override suspend fun <T> await(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        return suspendCancellableCoroutine { continuation ->
            val posted = mainHandler.post {
                // The caller may have been cancelled while the message sat in the queue; running
                // the block then would touch the UI for a request nobody is waiting on any more.
                if (!continuation.isActive) return@post
                continuation.resumeWith(runCatching(block))
            }
            if (!posted) {
                continuation.resumeWith(Result.failure(IllegalStateException("The main thread's message queue is no longer accepting work.")))
            }
        }
    }
}
