package com.kitakkun.jetwhale.plugins.semantics.agent

import kotlinx.coroutines.suspendCancellableCoroutine
import java.awt.EventQueue

/**
 * Runs the block on the AWT event dispatch thread, which owns a Compose Desktop composition, without
 * hopping when the caller is already on it.
 *
 * `Dispatchers.Main` would be the obvious choice but it is not free on desktop: it needs
 * `kotlinx-coroutines-swing` on the **app's** runtime classpath, and an app that does not happen to
 * have it fails every capture with "Module with the Main dispatcher is missing" rather than
 * degrading. `EventQueue` is in the JDK, so this works in any Compose Desktop app.
 */
internal object SwingComposeUiThread : ComposeUiThread {
    override suspend fun <T> await(block: () -> T): T {
        if (EventQueue.isDispatchThread()) return block()
        return suspendCancellableCoroutine { continuation ->
            EventQueue.invokeLater {
                // The caller may have been cancelled while the event sat in the queue; running the
                // block then would touch the UI for a request nobody is waiting on any more.
                if (!continuation.isActive) return@invokeLater
                continuation.resumeWith(runCatching(block))
            }
        }
    }
}
